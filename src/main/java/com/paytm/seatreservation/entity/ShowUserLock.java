package com.paytm.seatreservation.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "show_user_locks")
@IdClass(ShowUserLockId.class)
public class ShowUserLock {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public ShowUserLock() {}

    public Show getShow() { return show; }
    public void setShow(Show show) { this.show = show; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
