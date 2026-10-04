package com.paytm.seatreservation.entity;

import java.io.Serializable;
import java.util.Objects;

public class ShowUserLockId implements Serializable {
    private String show;
    private String userId;

    public ShowUserLockId() {}

    public ShowUserLockId(String show, String userId) {
        this.show = show;
        this.userId = userId;
    }

    public String getShow() { return show; }
    public void setShow(String show) { this.show = show; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShowUserLockId that = (ShowUserLockId) o;
        return Objects.equals(show, that.show) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(show, userId);
    }
}
