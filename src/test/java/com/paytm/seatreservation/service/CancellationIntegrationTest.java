package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.ReservationStatus;
import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.exception.ReservationAlreadyCancelledException;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Tag("integration")
class CancellationIntegrationTest {

    @Autowired
    private ShowService showService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ShowRepository showRepository;

    private String testShowId;
    private final String testUserId = "user-cancel-test-123";

    @BeforeEach
    void setUp() {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("cancellation-show-" + UUID.randomUUID());
        request.setPricePaise(1000L);
        request.setSeats(List.of("A1", "A2", "A3", "A4", "A5"));
        
        request.setId("cancellation-show-" + UUID.randomUUID());
        testShowId = showService.createShow(request).getId();
    }

    @Test
    void testSuccessfulCancellationAndRebook() {
        // 1. Reserve seats
        ReserveSeatsRequest reserveRequest = new ReserveSeatsRequest();
        reserveRequest.setSeats(List.of("A1", "A2"));
        reserveRequest.setIdempotencyKey(UUID.randomUUID().toString());

        ReservationResponse reserveResponse = reservationService.reserveSeats(testShowId, testUserId, reserveRequest);
        UUID reservationId = reserveResponse.getReservationId();

        // Verify seats are CONFIRMED
        seatRepository.findByShowId(testShowId).forEach(seat -> {
            if (List.of("A1", "A2").contains(seat.getSeatNumber())) {
                assertEquals(SeatStatus.CONFIRMED, seat.getStatus());
            } else {
                assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
            }
        });

        // 2. Cancel reservation
        ReservationResponse cancelResponse = reservationService.cancelReservation(reservationId, testUserId);
        assertEquals("cancelled", cancelResponse.getStatus());

        // Verify reservation status in DB
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());

        // Verify seats are AVAILABLE
        seatRepository.findByShowId(testShowId).forEach(seat -> 
            assertEquals(SeatStatus.AVAILABLE, seat.getStatus())
        );

        // 3. Rebook by a different user
        String anotherUserId = "user-other-456";
        ReserveSeatsRequest rebookRequest = new ReserveSeatsRequest();
        rebookRequest.setSeats(List.of("A1", "A2"));
        rebookRequest.setIdempotencyKey(UUID.randomUUID().toString());

        ReservationResponse rebookResponse = reservationService.reserveSeats(testShowId, anotherUserId, rebookRequest);
        assertNotNull(rebookResponse.getReservationId());
        assertNotEquals(reservationId, rebookResponse.getReservationId());
    }

    @Test
    void testConcurrentDoubleCancellation() throws InterruptedException {
        // Reserve seat
        ReserveSeatsRequest reserveRequest = new ReserveSeatsRequest();
        reserveRequest.setSeats(List.of("A3"));
        reserveRequest.setIdempotencyKey(UUID.randomUUID().toString());
        UUID reservationId = reservationService.reserveSeats(testShowId, testUserId, reserveRequest).getReservationId();

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    reservationService.cancelReservation(reservationId, testUserId);
                    successCount.incrementAndGet();
                } catch (ReservationAlreadyCancelledException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        assertEquals(1, successCount.get(), "Exactly one cancellation should succeed");
        assertEquals(4, conflictCount.get(), "Remaining cancellations should throw ReservationAlreadyCancelledException");

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
    }

    @Test
    void testCancellationFreesPerUserLimit() {
        // Create 4 seats (limit)
        ReserveSeatsRequest req1 = new ReserveSeatsRequest();
        req1.setSeats(List.of("A1", "A2", "A3", "A4"));
        req1.setIdempotencyKey(UUID.randomUUID().toString());
        UUID resId = reservationService.reserveSeats(testShowId, testUserId, req1).getReservationId();

        // Attempting to book A5 should fail (per user limit)
        ReserveSeatsRequest req2 = new ReserveSeatsRequest();
        req2.setSeats(List.of("A5"));
        req2.setIdempotencyKey(UUID.randomUUID().toString());
        
        assertThrows(com.paytm.seatreservation.exception.PerUserLimitExceededException.class, () -> 
            reservationService.reserveSeats(testShowId, testUserId, req2)
        );

        // Cancel the reservation
        reservationService.cancelReservation(resId, testUserId);

        // Now we can book A5
        ReservationResponse response = reservationService.reserveSeats(testShowId, testUserId, req2);
        assertNotNull(response.getReservationId());
    }
}
