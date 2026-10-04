package com.paytm.seatreservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.ShowResponse;
import com.paytm.seatreservation.exception.ShowNotFoundException;
import com.paytm.seatreservation.service.ShowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShowController.class)
@TestPropertySource(properties = "app.admin-token=test-admin-token")
class ShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ShowService showService;

    @Test
    void testCreateShowSuccess() throws Exception {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("test-show");
        request.setSeats(List.of("A1", "A2"));
        request.setPricePaise(100L);

        ShowResponse response = new ShowResponse();
        response.setId("test-show-1");
        response.setName("test-show");
        
        when(showService.createShow(any())).thenReturn(response);

        mockMvc.perform(post("/shows")
                        .header("Authorization", "Bearer test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("test-show"));
    }

    @Test
    void testCreateShowMissingToken() throws Exception {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("test-show");
        request.setSeats(List.of("A1", "A2"));
        request.setPricePaise(100L);

        mockMvc.perform(post("/shows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateShowInvalidToken() throws Exception {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("test-show");
        request.setSeats(List.of("A1", "A2"));
        request.setPricePaise(100L);

        mockMvc.perform(post("/shows")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCreateShowValidationBlankName() throws Exception {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("");
        request.setSeats(List.of("A1", "A2"));
        request.setPricePaise(100L);

        mockMvc.perform(post("/shows")
                        .header("Authorization", "Bearer test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateShowValidationEmptySeats() throws Exception {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("test-show");
        request.setSeats(List.of());
        request.setPricePaise(100L);

        mockMvc.perform(post("/shows")
                        .header("Authorization", "Bearer test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCreateShowValidationInvalidPrice() throws Exception {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("test-show");
        request.setSeats(List.of("A1"));
        request.setPricePaise(0L);

        mockMvc.perform(post("/shows")
                        .header("Authorization", "Bearer test-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetShowSuccess() throws Exception {
        String id = "test-show-1";
        ShowResponse response = new ShowResponse();
        response.setId(id);
        response.setName("test-show");

        when(showService.getShow(id)).thenReturn(response);

        // Publicly readable, no token needed
        mockMvc.perform(get("/shows/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("test-show"));
    }

    @Test
    void testGetShowNotFound() throws Exception {
        String id = "test-show-1";
        when(showService.getShow(id)).thenThrow(new ShowNotFoundException("Not found"));

        mockMvc.perform(get("/shows/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
