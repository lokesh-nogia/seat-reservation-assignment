package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.SeatResponse;
import com.paytm.seatreservation.dto.ShowResponse;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.exception.ShowNotFoundException;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ShowService {

    private static final Logger log = LoggerFactory.getLogger(ShowService.class);

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    public ShowService(ShowRepository showRepository, SeatRepository seatRepository) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        log.info("operation=create_show_start, name={}", request.getName());

        List<String> rawSeats = request.getSeats();
        Set<String> uniqueSeats = new HashSet<>();
        List<String> validSeats = new ArrayList<>();

        for (String seatStr : rawSeats) {
            if (seatStr == null || seatStr.trim().isEmpty()) {
                throw new IllegalArgumentException("Seat names cannot be blank");
            }
            String trimmedSeat = seatStr.trim();
            if (!uniqueSeats.add(trimmedSeat)) {
                throw new IllegalArgumentException("Duplicate seat detected: " + trimmedSeat);
            }
            validSeats.add(trimmedSeat);
        }

        Show show = new Show();
        if (request.getId() != null && !request.getId().trim().isEmpty()) {
            show.setId(request.getId().trim());
        } else {
            show.setId(UUID.randomUUID().toString());
        }
        show.setName(request.getName());
        show.setPricePaise(request.getPricePaise());
        show.setPerUserLimit(4); // Default requirement

        show = showRepository.save(show);

        List<Seat> seats = new ArrayList<>();
        for (String seatName : validSeats) {
            Seat seat = new Seat();
            seat.setId(UUID.randomUUID());
            seat.setShow(show);
            seat.setSeatNumber(seatName);
            seat.setStatus(SeatStatus.AVAILABLE);
            seats.add(seat);
        }

        seatRepository.saveAll(seats);

        log.info("operation=create_show_success, show_id={}, seats_count={}", show.getId(), seats.size());

        return mapToShowResponse(show, seats);
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(String id) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ShowNotFoundException("Show not found with id: " + id));

        List<Seat> seats = seatRepository.findByShowId(id);

        return mapToShowResponse(show, seats);
    }

    private ShowResponse mapToShowResponse(Show show, List<Seat> seats) {
        ShowResponse response = new ShowResponse();
        response.setId(show.getId());
        response.setName(show.getName());
        response.setPricePaise(show.getPricePaise());
        response.setPerUserLimit(show.getPerUserLimit());
        
        response.setTotalSeats(seats.size());
        
        int available = 0;
        int held = 0;
        int confirmed = 0;
        
        List<SeatResponse> seatResponses = new ArrayList<>();
        for (Seat seat : seats) {
            switch (seat.getStatus()) {
                case AVAILABLE -> available++;
                case HELD -> held++;
                case CONFIRMED -> confirmed++;
            }
            seatResponses.add(new SeatResponse(seat.getSeatNumber(), seat.getStatus().name().toLowerCase()));
        }
        
        response.setAvailable(available);
        response.setHeld(held);
        response.setConfirmed(confirmed);
        response.setSeats(seatResponses);
        
        return response;
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> getAllShows() {
        List<Show> shows = showRepository.findAll();
        List<Object[]> counts = seatRepository.countSeatsByShowAndStatus();

        Map<String, Map<SeatStatus, Long>> showCounts = new HashMap<>();
        for (Object[] row : counts) {
            String showId = (String) row[0];
            SeatStatus status = (SeatStatus) row[1];
            Long count = (Long) row[2];
            showCounts.computeIfAbsent(showId, k -> new EnumMap<>(SeatStatus.class)).put(status, count);
        }

        return shows.stream()
                .map(show -> {
                    Map<SeatStatus, Long> statusCounts = showCounts.getOrDefault(show.getId(), Collections.emptyMap());

                    int available = statusCounts.getOrDefault(SeatStatus.AVAILABLE, 0L).intValue();
                    int held = statusCounts.getOrDefault(SeatStatus.HELD, 0L).intValue();
                    int confirmed = statusCounts.getOrDefault(SeatStatus.CONFIRMED, 0L).intValue();

                    ShowResponse response = new ShowResponse();
                    response.setId(show.getId());
                    response.setName(show.getName());
                    response.setPricePaise(show.getPricePaise());
                    response.setPerUserLimit(show.getPerUserLimit());

                    response.setTotalSeats(available + held + confirmed);
                    response.setAvailable(available);
                    response.setHeld(held);
                    response.setConfirmed(confirmed);
                    // intentionally leaving seats as null

                    return response;
                })
                .collect(Collectors.toList());
    }
}
