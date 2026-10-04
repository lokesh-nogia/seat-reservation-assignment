package com.paytm.seatreservation.exception;

public class ReservationNotOwnerException extends RuntimeException {
    public ReservationNotOwnerException(String message) {
        super(message);
    }
}
