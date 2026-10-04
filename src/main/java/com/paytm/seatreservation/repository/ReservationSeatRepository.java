package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.ReservationSeat;
import com.paytm.seatreservation.entity.ReservationSeatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, ReservationSeatId> {
    
    @Query("SELECT rs FROM ReservationSeat rs JOIN FETCH rs.seat WHERE rs.reservation.id = :reservationId AND rs.releasedAt IS NULL")
    List<ReservationSeat> findActiveByReservationId(@Param("reservationId") UUID reservationId);
}
