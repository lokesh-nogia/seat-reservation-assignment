package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ShowCreationIntegrationTest {

    @Autowired
    private ShowService showService;

    @Autowired
    private ShowRepository showRepository;

    @SpyBean
    private SeatRepository seatRepository;

    @MockBean
    private SeatMetricsService seatMetricsService;

    @Test
    void testShowCreationIsAtomic() {
        // Initial count
        long initialShowCount = showRepository.count();

        CreateShowRequest request = new CreateShowRequest();
        request.setName("atomic-test");
        request.setPricePaise(500L);
        request.setSeats(List.of("A1", "A2"));

        // Force a failure during seat creation
        doThrow(new RuntimeException("Simulated database failure during seat insertion"))
                .when(seatRepository).saveAll(any());

        // The operation should fail
        assertThrows(RuntimeException.class, () -> showService.createShow(request));

        // Verify the show was rolled back (not saved)
        long finalShowCount = showRepository.count();
        assertEquals(initialShowCount, finalShowCount, "Show should not be created if seat creation fails");
    }
}
