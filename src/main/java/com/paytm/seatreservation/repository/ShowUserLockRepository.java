package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.ShowUserLock;
import com.paytm.seatreservation.entity.ShowUserLockId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShowUserLockRepository extends JpaRepository<ShowUserLock, ShowUserLockId> {

    @Modifying
    @Query(value = "INSERT INTO show_user_locks(show_id, user_id, created_at) VALUES (:showId, :userId, CURRENT_TIMESTAMP) ON CONFLICT (show_id, user_id) DO NOTHING", nativeQuery = true)
    void insertIfNotExists(@Param("showId") String showId, @Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM ShowUserLock l WHERE l.show.id = :showId AND l.userId = :userId")
    Optional<ShowUserLock> findByIdForUpdate(@Param("showId") String showId, @Param("userId") String userId);
}
