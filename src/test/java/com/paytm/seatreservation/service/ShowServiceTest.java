package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ShowServiceTest {

    private ShowRepository showRepository;
    private SeatRepository seatRepository;
    private ShowService showService;

    @BeforeEach
    void setUp() {
        showRepository = mock(ShowRepository.class);
        seatRepository = mock(SeatRepository.class);
        showService = new ShowService(showRepository, seatRepository);
    }

    @Test
    void testCreateShowFailsOnDuplicateSeats() {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("test-show");
        request.setPricePaise(100L);
        request.setSeats(List.of("A1", "A2", "A1"));

        assertThrows(IllegalArgumentException.class, () -> showService.createShow(request));
        
        verifyNoInteractions(showRepository);
        verifyNoInteractions(seatRepository);
    }

    @Test
    void testCreateShowFailsOnBlankSeats() {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("test-show");
        request.setPricePaise(100L);
        request.setSeats(List.of("A1", "  ", "A2"));

        assertThrows(IllegalArgumentException.class, () -> showService.createShow(request));

        verifyNoInteractions(showRepository);
        verifyNoInteractions(seatRepository);
    }
}
