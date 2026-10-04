package com.paytm.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreateShowRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    private String name;

    @NotEmpty(message = "Seats list cannot be empty")
    @NotNull(message = "Seats list is required")
    @Size(max = 1000, message = "Cannot exceed 1000 seats per show")
    private List<String> seats;

    @JsonProperty("price_paise")
    @NotNull(message = "Price is required")
    @Min(value = 1, message = "Price must be greater than 0")
    private Long pricePaise;

    private String id;

    public CreateShowRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null) {
            this.name = name.trim();
        }
    }

    public List<String> getSeats() {
        return seats;
    }

    public void setSeats(List<String> seats) {
        this.seats = seats;
    }

    public Long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(Long pricePaise) {
        this.pricePaise = pricePaise;
    }
}
