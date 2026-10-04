CREATE UNIQUE INDEX idx_reservation_seats_active 
ON reservation_seats(seat_id) 
WHERE released_at IS NULL;
