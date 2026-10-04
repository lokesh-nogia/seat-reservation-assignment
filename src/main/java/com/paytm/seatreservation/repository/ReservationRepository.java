package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT COUNT(rs) FROM ReservationSeat rs " +
           "JOIN rs.reservation r " +
           "WHERE r.show.id = :showId " +
           "AND r.userId = :userId " +
           "AND r.status = com.paytm.seatreservation.entity.ReservationStatus.CONFIRMED " +
           "AND rs.releasedAt IS NULL")
    long countActiveSeatsForUserAndShow(@Param("userId") String userId, @Param("showId") String showId);

    @Query("SELECT COUNT(rs) FROM ReservationSeat rs " +
           "JOIN rs.reservation r " +
           "WHERE r.show.id = :showId " +
           "AND r.status = com.paytm.seatreservation.entity.ReservationStatus.CONFIRMED " +
           "AND rs.releasedAt IS NULL")
    long countActiveSeatsForShow(@Param("showId") String showId);
}
