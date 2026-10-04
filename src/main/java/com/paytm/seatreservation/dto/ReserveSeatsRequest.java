package com.paytm.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ReserveSeatsRequest {

    @NotEmpty(message = "Seats list cannot be empty")
    @NotNull(message = "Seats list is required")
    @Size(max = 1000, message = "Cannot exceed 1000 seats per request")
    private List<String> seats;

    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    public ReserveSeatsRequest() {}

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
