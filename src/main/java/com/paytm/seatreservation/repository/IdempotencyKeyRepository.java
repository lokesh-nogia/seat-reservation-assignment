package com.paytm.seatreservation.repository;

import com.paytm.seatreservation.entity.IdempotencyKey;
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
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    @Modifying
    @Query(value = "INSERT INTO idempotency_keys(id, user_id, idempotency_key, show_id, request_hash, created_at) " +
            "VALUES (:id, :userId, :idempotencyKey, :showId, :requestHash, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (user_id, idempotency_key) DO NOTHING", nativeQuery = true)
    void insertIfNotExists(@Param("id") UUID id,
                           @Param("userId") String userId,
                           @Param("idempotencyKey") String idempotencyKey,
                           @Param("showId") String showId,
                           @Param("requestHash") String requestHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyKey i WHERE i.userId = :userId AND i.idempotencyKey = :idempotencyKey")
    Optional<IdempotencyKey> findByUserIdAndIdempotencyKeyForUpdate(@Param("userId") String userId, @Param("idempotencyKey") String idempotencyKey);
}
