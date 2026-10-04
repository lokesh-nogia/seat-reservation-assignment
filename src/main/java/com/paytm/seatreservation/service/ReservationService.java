package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.seatreservation.entity.IdempotencyKey;
import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.ReservationSeat;
import com.paytm.seatreservation.entity.ReservationStatus;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.exception.IdempotencyConflictException;
import com.paytm.seatreservation.exception.PerUserLimitExceededException;
import com.paytm.seatreservation.exception.ReservationAlreadyCancelledException;
import com.paytm.seatreservation.exception.ReservationNotOwnerException;
import com.paytm.seatreservation.exception.SeatTakenException;
import com.paytm.seatreservation.exception.ShowNotFoundException;
import com.paytm.seatreservation.repository.IdempotencyKeyRepository;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.ReservationSeatRepository;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import com.paytm.seatreservation.repository.ShowUserLockRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ShowRepository showRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ShowUserLockRepository showUserLockRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final MeterRegistry meterRegistry;

    public ReservationService(ShowRepository showRepository,
                              IdempotencyKeyRepository idempotencyKeyRepository,
                              ShowUserLockRepository showUserLockRepository,
                              SeatRepository seatRepository,
                              ReservationRepository reservationRepository,
                              ReservationSeatRepository reservationSeatRepository,
                              MeterRegistry meterRegistry) {
        this.showRepository = showRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.showUserLockRepository = showUserLockRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ReservationResponse reserveSeats(String showId, String userId, ReserveSeatsRequest request) {
        log.debug("operation=reserve_seats_start, show_id={}, user_id={}, idempotency_key={}", showId, userId, request.getIdempotencyKey());
        Timer.Sample sample = Timer.start(meterRegistry);

        try {

        // 0. Validate input and sort seats deterministically
        List<String> requestedSeats = validateAndSortSeats(request.getSeats());

        // Calculate Request Hash
        String requestHash = calculateRequestHash(showId, requestedSeats);

        // 1. Idempotency record
        UUID idempotencyRecordId = UUID.randomUUID();
        idempotencyKeyRepository.insertIfNotExists(idempotencyRecordId, userId, request.getIdempotencyKey(), showId, requestHash);
        
        IdempotencyKey idempotencyKey = idempotencyKeyRepository.findByUserIdAndIdempotencyKeyForUpdate(userId, request.getIdempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Failed to lock idempotency record"));

        if (idempotencyKey.getReservation() != null) {
            if (requestHash.equals(idempotencyKey.getRequestHash())) {
                log.debug("operation=reserve_seats_idempotent_replay, reservation_id={}", idempotencyKey.getReservation().getId());
                meterRegistry.counter("reservations_idempotent_replay_total").increment();
                return mapToResponse(idempotencyKey.getReservation(), requestedSeats);
            } else {
                meterRegistry.counter("reservations_declined_total", "reason", "idempotency_conflict").increment();
                throw new IdempotencyConflictException("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");
            }
        }

        // Validate show exists
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ShowNotFoundException("Show not found"));

        // 2. Lock show_user_locks row
        showUserLockRepository.insertIfNotExists(showId, userId);
        showUserLockRepository.findByIdForUpdate(showId, userId)
                .orElseThrow(() -> new IllegalStateException("Failed to lock show user record"));

        // 3. Lock requested seats in deterministic order
        List<Seat> lockedSeats = seatRepository.findByShowIdAndSeatNumberInForUpdate(showId, requestedSeats);
        if (lockedSeats.size() != requestedSeats.size()) {
            throw new IllegalArgumentException("One or more requested seats do not exist in this show");
        }

        // 4. Check seat availability
        for (Seat seat : lockedSeats) {
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                meterRegistry.counter("reservations_declined_total", "reason", "seat_taken").increment();
                throw new SeatTakenException("Seat " + seat.getSeatNumber() + " is already taken");
            }
        }

        // 5. Check per-user limit
        long currentActiveSeats = reservationRepository.countActiveSeatsForUserAndShow(userId, showId);
        if (currentActiveSeats + requestedSeats.size() > show.getPerUserLimit()) {
            meterRegistry.counter("reservations_declined_total", "reason", "per_user_limit").increment();
            throw new PerUserLimitExceededException("Request exceeds per user limit of " + show.getPerUserLimit() + " seats");
        }

        // 6. Create reservation
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setShow(show);
        reservation.setUserId(userId);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        
        long amountPaise = Math.multiplyExact(show.getPricePaise(), (long) requestedSeats.size());
        reservation.setAmountPaise(amountPaise);
        
        reservation = reservationRepository.save(reservation);

        // 7. Create reservation_seats
        List<ReservationSeat> reservationSeats = new ArrayList<>();
        for (Seat seat : lockedSeats) {
            ReservationSeat rs = new ReservationSeat();
            rs.setReservation(reservation);
            rs.setSeat(seat);
            reservationSeats.add(rs);
        }
        reservationSeatRepository.saveAll(reservationSeats);

        // 8. Mark seats CONFIRMED
        for (Seat seat : lockedSeats) {
            seat.setStatus(SeatStatus.CONFIRMED);
        }
        seatRepository.saveAll(lockedSeats);

        // 9. Store reservation_id in idempotency row
        idempotencyKey.setReservation(reservation);
        idempotencyKeyRepository.save(idempotencyKey);

        log.debug("operation=reserve_seats_success, show_id={}, user_id={}, reservation_id={}", showId, userId, reservation.getId());
        meterRegistry.counter("reservations_confirmed_total").increment();

        return mapToResponse(reservation, requestedSeats);
        } finally {
            sample.stop(meterRegistry.timer("reservations.reserve_seats.timer"));
        }
    }

    @Transactional
    public ReservationResponse cancelReservation(UUID reservationId, String userId) {
        log.debug("operation=cancel_reservation_start, reservation_id={}, user_id={}", reservationId, userId);
        Timer.Sample sample = Timer.start(meterRegistry);

        try {

        // 1. Lock reservation row directly
        Reservation lockedReservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));

        String showId = lockedReservation.getShow().getId();

        // 2. Lock show_user_locks row
        showUserLockRepository.findByIdForUpdate(showId, lockedReservation.getUserId())
                .orElseThrow(() -> new IllegalStateException("Failed to lock show user record for cancellation"));

        // 4. Verify reservation ownership
        if (!lockedReservation.getUserId().equals(userId)) {
            meterRegistry.counter("cancellation_declined_total", "reason", "not_owner").increment();
            throw new ReservationNotOwnerException("You do not own this reservation");
        }

        // 5. Verify reservation status
        if (lockedReservation.getStatus() == ReservationStatus.CANCELLED) {
            meterRegistry.counter("cancellation_declined_total", "reason", "already_cancelled").increment();
            throw new ReservationAlreadyCancelledException("Reservation is already cancelled");
        }
        
        if (lockedReservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Reservation is in an invalid state for cancellation");
        }

        // 6. Find reservation's seats
        List<ReservationSeat> activeReservationSeats = reservationSeatRepository.findActiveByReservationId(reservationId);
        List<String> seatNumbers = activeReservationSeats.stream()
                .map(rs -> rs.getSeat().getSeatNumber())
                .sorted()
                .collect(Collectors.toList());

        // 7. Lock those seat rows in deterministic order
        if (!seatNumbers.isEmpty()) {
            List<Seat> lockedSeats = seatRepository.findByShowIdAndSeatNumberInForUpdate(showId, seatNumbers);

            // 8. Verify seat ownership/state
            for (Seat seat : lockedSeats) {
                if (seat.getStatus() != SeatStatus.CONFIRMED) {
                    throw new IllegalStateException("Inconsistent state: Seat " + seat.getSeatNumber() + " is not CONFIRMED");
                }
            }

            // 10. Mark reservation_seats released
            for (ReservationSeat rs : activeReservationSeats) {
                rs.setReleasedAt(OffsetDateTime.now());
            }
            reservationSeatRepository.saveAll(activeReservationSeats);

            // 11. Mark seats AVAILABLE
            for (Seat seat : lockedSeats) {
                seat.setStatus(SeatStatus.AVAILABLE);
            }
            seatRepository.saveAll(lockedSeats);
        }

        // 9. Mark reservation CANCELLED
        lockedReservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(lockedReservation);

        log.debug("operation=cancel_reservation_success, reservation_id={}", reservationId);
        meterRegistry.counter("reservations_cancelled_total").increment();

        return mapToResponse(lockedReservation, seatNumbers);
        } finally {
            sample.stop(meterRegistry.timer("reservations.cancel.timer"));
        }
    }

    private List<String> validateAndSortSeats(List<String> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("Seats list cannot be empty");
        }
        Set<String> uniqueSeats = new HashSet<>();
        List<String> validSeats = new ArrayList<>();
        for (String seat : seats) {
            if (seat == null || seat.trim().isEmpty()) {
                throw new IllegalArgumentException("Seat names cannot be blank");
            }
            String trimmed = seat.trim();
            if (!uniqueSeats.add(trimmed)) {
                throw new IllegalArgumentException("Duplicate seat requested: " + trimmed);
            }
            validSeats.add(trimmed);
        }
        Collections.sort(validSeats);
        return validSeats;
    }

    private String calculateRequestHash(String showId, List<String> sortedSeats) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = showId.toString() + ":" + String.join(",", sortedSeats);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private ReservationResponse mapToResponse(Reservation reservation, List<String> seats) {
        ReservationResponse response = new ReservationResponse();
        response.setReservationId(reservation.getId());
        response.setShowId(reservation.getShow().getId());
        response.setUserId(reservation.getUserId());
        response.setSeats(seats);
        response.setAmountPaise(reservation.getAmountPaise());
        response.setStatus(reservation.getStatus().name().toLowerCase());
        return response;
    }
}
