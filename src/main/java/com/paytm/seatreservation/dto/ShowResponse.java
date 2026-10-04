package com.paytm.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

public class ShowResponse {

    private String id;
    
    private String name;
    
    @JsonProperty("price_paise")
    private long pricePaise;
    
    @JsonProperty("per_user_limit")
    private int perUserLimit;
    
    @JsonProperty("total_seats")
    private int totalSeats;
    
    private int available;
    
    private int held;
    
    private int confirmed;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<SeatResponse> seats;

    public ShowResponse() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public void setPricePaise(long pricePaise) {
        this.pricePaise = pricePaise;
    }

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(int perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public int getAvailable() {
        return available;
    }

    public void setAvailable(int available) {
        this.available = available;
    }

    public int getHeld() {
        return held;
    }

    public void setHeld(int held) {
        this.held = held;
    }

    public int getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(int confirmed) {
        this.confirmed = confirmed;
    }

    public List<SeatResponse> getSeats() {
        return seats;
    }

    public void setSeats(List<SeatResponse> seats) {
        this.seats = seats;
    }
}
