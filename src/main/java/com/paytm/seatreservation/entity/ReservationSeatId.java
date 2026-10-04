package com.paytm.seatreservation.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ReservationSeatId implements Serializable {
    private UUID reservation;
    private UUID seat;

    public ReservationSeatId() {}

    public ReservationSeatId(UUID reservation, UUID seat) {
        this.reservation = reservation;
        this.seat = seat;
    }

    public UUID getReservation() { return reservation; }
    public void setReservation(UUID reservation) { this.reservation = reservation; }

    public UUID getSeat() { return seat; }
    public void setSeat(UUID seat) { this.seat = seat; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReservationSeatId that = (ReservationSeatId) o;
        return Objects.equals(reservation, that.reservation) && Objects.equals(seat, that.seat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservation, seat);
    }
}
