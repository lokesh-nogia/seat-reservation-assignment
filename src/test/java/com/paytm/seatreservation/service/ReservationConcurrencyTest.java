package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.exception.IdempotencyConflictException;
import com.paytm.seatreservation.exception.PerUserLimitExceededException;
import com.paytm.seatreservation.exception.SeatTakenException;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests require a real PostgreSQL database to execute properly due to explicit FOR UPDATE locks.
 * They are marked with @Tag("integration") to execute only when explicitly requested
 * (e.g. via postgres maven profile).
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Show createTestShow(String name, int numSeats, int perUserLimit) {
        Show show = new Show();
        show.setId(UUID.randomUUID().toString());
        show.setName(name);
        show.setPricePaise(100L);
        show.setPerUserLimit(perUserLimit);
        showRepository.save(show);

        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= numSeats; i++) {
            Seat seat = new Seat();
            seat.setId(UUID.randomUUID());
            seat.setShow(show);
            seat.setSeatNumber("A" + i);
            seat.setStatus(SeatStatus.AVAILABLE);
            seats.add(seat);
        }
        seatRepository.saveAll(seats);
        return show;
    }

    @Test
    void testHotSeatConcurrency() throws InterruptedException {
        Show show = createTestShow("Hot Seat Show", 10, 4);

        int numConcurrentUsers = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numConcurrentUsers);
        List<Callable<ReservationResponse>> tasks = new ArrayList<>();

        for (int i = 0; i < numConcurrentUsers; i++) {
            String userId = "user-" + UUID.randomUUID().toString();
            tasks.add(() -> {
                ReserveSeatsRequest request = new ReserveSeatsRequest();
                request.setSeats(List.of("A1"));
                request.setIdempotencyKey(UUID.randomUUID().toString());
                return reservationService.reserveSeats(show.getId(), userId, request);
            });
        }

        List<Future<ReservationResponse>> results = executor.invokeAll(tasks);
        executor.shutdown();

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger seatTakenConflicts = new AtomicInteger(0);

        for (Future<ReservationResponse> result : results) {
            try {
                result.get();
                successes.incrementAndGet();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof SeatTakenException) {
                    seatTakenConflicts.incrementAndGet();
                } else {
                    throw new RuntimeException("Unexpected exception: " + e.getCause(), e);
                }
            }
        }

        assertEquals(1, successes.get(), "Exactly 1 reservation should succeed");
        assertEquals(numConcurrentUsers - 1, seatTakenConflicts.get(), "All other requests should fail with SEAT_TAKEN");

        long activeSeats = reservationRepository.countActiveSeatsForShow(show.getId());
        assertEquals(1, activeSeats, "There must be exactly 1 active seat reservation for A1");
        
        List<Seat> seats = seatRepository.findByShowId(show.getId());
        Seat a1 = seats.stream().filter(s -> s.getSeatNumber().equals("A1")).findFirst().get();
        assertEquals(SeatStatus.CONFIRMED, a1.getStatus(), "Seat A1 must be marked as CONFIRMED");
    }

    @Test
    void testIdempotencyConcurrency() throws InterruptedException {
        Show show = createTestShow("Idempotent Show", 10, 4);
        String userId = "user-" + UUID.randomUUID().toString();
        String idempotencyKey = "idemp-key-123";

        int numConcurrentRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numConcurrentRequests);
        List<Callable<ReservationResponse>> tasks = new ArrayList<>();

        for (int i = 0; i < numConcurrentRequests; i++) {
            tasks.add(() -> {
                ReserveSeatsRequest request = new ReserveSeatsRequest();
                request.setSeats(List.of("A1"));
                request.setIdempotencyKey(idempotencyKey);
                return reservationService.reserveSeats(show.getId(), userId, request);
            });
        }

        List<Future<ReservationResponse>> results = executor.invokeAll(tasks);
        executor.shutdown();

        UUID firstReservationId = null;
        for (Future<ReservationResponse> result : results) {
            try {
                ReservationResponse response = result.get();
                assertNotNull(response.getReservationId());
                if (firstReservationId == null) {
                    firstReservationId = response.getReservationId();
                } else {
                    assertEquals(firstReservationId, response.getReservationId(), "All responses must reference the same reservation ID");
                }
            } catch (ExecutionException e) {
                throw new RuntimeException("Unexpected exception: " + e.getCause(), e);
            }
        }

        long count = reservationRepository.countActiveSeatsForShow(show.getId());
        assertEquals(1, count, "Only 1 reservation should be created in the DB for this show");
    }

    @Test
    void testMultiSeatDeadlockPrevention() throws InterruptedException {
        Show show = createTestShow("Deadlock Show", 10, 4);
        UUID userA = UUID.randomUUID();
        String userAStr = userA.toString();
        UUID userB = UUID.randomUUID();
        String userBStr = userB.toString();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        Callable<ReservationResponse> taskA = () -> {
            ReserveSeatsRequest req = new ReserveSeatsRequest();
            req.setSeats(List.of("A1", "A2"));
            req.setIdempotencyKey("keyA");
            return reservationService.reserveSeats(show.getId(), userAStr, req);
        };
        
        Callable<ReservationResponse> taskB = () -> {
            ReserveSeatsRequest req = new ReserveSeatsRequest();
            req.setSeats(List.of("A2", "A1"));
            req.setIdempotencyKey("keyB");
            return reservationService.reserveSeats(show.getId(), userBStr, req);
        };

        List<Future<ReservationResponse>> results = executor.invokeAll(List.of(taskA, taskB));
        executor.shutdown();

        int successes = 0;
        int conflicts = 0;
        
        for (Future<ReservationResponse> result : results) {
            try {
                result.get();
                successes++;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof SeatTakenException) {
                    conflicts++;
                } else {
                    throw new RuntimeException("Unexpected exception: " + e.getCause(), e);
                }
            }
        }

        assertEquals(1, successes, "One transaction should succeed");
        assertEquals(1, conflicts, "One transaction should conflict (not deadlock)");
    }

    @Test
    void testPerUserLimitConcurrency() throws InterruptedException {
        Show show = createTestShow("Limit Show", 10, 4);
        String userId = "user-" + UUID.randomUUID().toString();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Callable<ReservationResponse>> tasks = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            final String seat = "A" + i;
            tasks.add(() -> {
                ReserveSeatsRequest request = new ReserveSeatsRequest();
                request.setSeats(List.of(seat));
                request.setIdempotencyKey(UUID.randomUUID().toString());
                return reservationService.reserveSeats(show.getId(), userId, request);
            });
        }

        List<Future<ReservationResponse>> results = executor.invokeAll(tasks);
        executor.shutdown();

        int successes = 0;
        int conflicts = 0;
        
        for (Future<ReservationResponse> result : results) {
            try {
                result.get();
                successes++;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof PerUserLimitExceededException) {
                    conflicts++;
                } else {
                    throw new RuntimeException("Unexpected exception: " + e.getCause(), e);
                }
            }
        }

        assertEquals(4, successes, "Exactly 4 seats should be successfully reserved");
        assertEquals(6, conflicts, "6 requests should be rejected due to per user limit");

        long activeSeats = reservationRepository.countActiveSeatsForUserAndShow(userId, show.getId());
        assertTrue(activeSeats <= 4, "Active seats for user must not exceed 4 in the database");
    }

    @Test
    void testIdempotencySameKeyDifferentRequest() {
        Show show = createTestShow("Idemp Diff Req Show", 10, 4);
        String userIdStr = "user-" + UUID.randomUUID().toString();
        
        ReserveSeatsRequest req1 = new ReserveSeatsRequest();
        req1.setSeats(List.of("A1"));
        req1.setIdempotencyKey("shared-key");
        
        reservationService.reserveSeats(show.getId(), userIdStr, req1);
        
        ReserveSeatsRequest req2 = new ReserveSeatsRequest();
        req2.setSeats(List.of("A2"));
        req2.setIdempotencyKey("shared-key");
        
        Exception ex = null;
        try {
            reservationService.reserveSeats(show.getId(), userIdStr, req2);
        } catch (Exception e) {
            ex = e;
        }
        
        assertNotNull(ex);
        assertTrue(ex instanceof IdempotencyConflictException);
    }
}
