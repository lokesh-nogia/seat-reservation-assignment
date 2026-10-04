package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findByShowId(String showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.show.id = :showId AND s.seatNumber IN :seatNumbers ORDER BY s.seatNumber")
    List<Seat> findByShowIdAndSeatNumberInForUpdate(@Param("showId") String showId, @Param("seatNumbers") List<String> seatNumbers);

    @Query("SELECT s.show.id, s.status, COUNT(s) FROM Seat s GROUP BY s.show.id, s.status")
    List<Object[]> countSeatsByShowAndStatus();

    @Query("SELECT s.status, COUNT(s) FROM Seat s WHERE s.show.id = :showId GROUP BY s.status")
    List<Object[]> countSeatsByStatusForShow(@Param("showId") String showId);
}
