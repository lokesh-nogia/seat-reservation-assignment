package com.paytm.seatreservation.service;

import com.paytm.seatreservation.entity.SeatStatus;
import com.paytm.seatreservation.repository.SeatRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service dedicated to business metrics like seats_available gauge.
 * Uses an efficient @Scheduled approach to avoid hammering the database 
 * with a full scan query every time Prometheus scrapes the /actuator/prometheus endpoint.
 */
@Service
public class SeatMetricsService {
    private static final Logger log = LoggerFactory.getLogger(SeatMetricsService.class);

    private final SeatRepository seatRepository;
    private final AtomicLong availableSeatsGauge;

    public SeatMetricsService(SeatRepository seatRepository, MeterRegistry meterRegistry) {
        this.seatRepository = seatRepository;
        this.availableSeatsGauge = new AtomicLong(0);

        Gauge.builder("seats_available_total", availableSeatsGauge, AtomicLong::get)
             .description("Total number of available seats across all shows")
             .register(meterRegistry);
    }

    // Refresh every 15 seconds (typical Prometheus scrape interval is 15s)
    @Scheduled(fixedRate = 15000)
    public void updateAvailableSeatsMetric() {
        try {
            List<Object[]> counts = seatRepository.countSeatsByShowAndStatus();
            long totalAvailable = 0;
            
            if (counts != null) {
                for (Object[] row : counts) {
                    SeatStatus status = (SeatStatus) row[1];
                    if (status == SeatStatus.AVAILABLE) {
                        totalAvailable += (Long) row[2];
                    }
                }
            }
            
            availableSeatsGauge.set(totalAvailable);
        } catch (Exception e) {
            log.error("Failed to update seats_available_total metric", e);
        }
    }
}
