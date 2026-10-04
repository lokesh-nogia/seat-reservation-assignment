package com.paytm.seatreservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveSeatsRequest;
import com.paytm.seatreservation.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.context.annotation.Import;
import com.paytm.seatreservation.config.WebConfig;
import com.paytm.seatreservation.security.AdminAuthInterceptor;
import com.paytm.seatreservation.security.UserAuthInterceptor;

@WebMvcTest(ReservationController.class)
@Import({WebConfig.class, UserAuthInterceptor.class, AdminAuthInterceptor.class})
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationService reservationService;

    @Test
    void testReserveSeatsSuccess() throws Exception {
        String showId = "test-show-1";
        String userId = "user-123";
        String idempotencyKey = "test-key";

        ReserveSeatsRequest request = new ReserveSeatsRequest();
        request.setSeats(List.of("A1"));
        request.setIdempotencyKey(idempotencyKey);

        ReservationResponse response = new ReservationResponse();
        response.setReservationId(UUID.randomUUID());
        response.setShowId(showId);
        response.setUserId(userId);
        response.setStatus("confirmed");

        when(reservationService.reserveSeats(eq(showId), eq(userId), any())).thenReturn(response);

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value(userId))
                .andExpect(jsonPath("$.status").value("confirmed"));
    }

    @Test
    void testReserveSeatsMissingToken() throws Exception {
        String showId = "test-show-1";
        ReserveSeatsRequest request = new ReserveSeatsRequest();
        request.setSeats(List.of("A1"));
        request.setIdempotencyKey("test-key");

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testReserveSeatsEmptyToken() throws Exception {
        String showId = "test-show-1";
        ReserveSeatsRequest request = new ReserveSeatsRequest();
        request.setSeats(List.of("A1"));
        request.setIdempotencyKey("test-key");

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testReserveSeatsSpoofedUserId() throws Exception {
        String showId = "test-show-1";
        String actualUserId = "user-123";
        
        // Malicious request trying to pass a different user ID in the body
        String maliciousPayload = "{\n" +
                "  \"user_id\": \"user-999\",\n" +
                "  \"seats\": [\"A1\"],\n" +
                "  \"idempotency_key\": \"key-1\"\n" +
                "}";

        ReservationResponse response = new ReservationResponse();
        response.setReservationId(UUID.randomUUID());
        response.setShowId(showId);
        response.setUserId(actualUserId);
        response.setStatus("confirmed");

        // We verify that the service is called with the actualUserId from the token, not user-999
        when(reservationService.reserveSeats(eq(showId), eq(actualUserId), any())).thenReturn(response);

        mockMvc.perform(post("/shows/" + showId + "/reserve")
                        .header("Authorization", "Bearer " + actualUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value(actualUserId));
    }

    @Test
    void testCancelReservationSuccess() throws Exception {
        UUID reservationId = UUID.randomUUID();
        String actualUserId = "user-123";

        ReservationResponse response = new ReservationResponse();
        response.setReservationId(reservationId);
        response.setUserId(actualUserId);
        response.setStatus("cancelled");

        when(reservationService.cancelReservation(reservationId, actualUserId)).thenReturn(response);

        mockMvc.perform(post("/reservations/" + reservationId + "/cancel")
                        .header("Authorization", "Bearer " + actualUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation_id").value(reservationId.toString()))
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void testCancelReservationMissingToken() throws Exception {
        UUID reservationId = UUID.randomUUID();

        mockMvc.perform(post("/reservations/" + reservationId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
