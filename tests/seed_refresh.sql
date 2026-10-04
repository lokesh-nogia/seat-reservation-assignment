-- seed_refresh.sql
-- Safely truncates business data and re-inserts fresh test data without modifying flyway_schema_history

TRUNCATE TABLE show_user_locks, idempotency_keys, reservation_seats, reservations, seats, shows CASCADE;

-- Run AFTER Flyway V1..V4 have been applied.
-- Loads a Paytm-specific, deliberately isolated test dataset:
--   show-1 hot-seat-storm        100 seats      hot seat A12, pair A13/A14
--   show-2 friday-night          900 seats      first 300 seats pre-booked
--   show-3 per-user-limit-test    50 seats      all initially available
--   show-4 onsale-20k         20,000 seats      large stampede/scale scenario
--   show-5 stadium-concert    28,950 seats      large GET/payload scenario
--                              -------
--                              50,000 seats
--
-- Show IDs are VARCHAR in the final schema (V4), so deterministic IDs are used
-- to make local tests repeatable and easy to target.

BEGIN;

-- A1..Z..., then AA..., based on row index and seat index.
CREATE FUNCTION pg_temp.seat_label(i int, per_row int) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN (i / per_row) < 26
              THEN chr(65 + (i / per_row))
              ELSE chr(65 + (i / per_row) / 26 - 1) || chr(65 + (i / per_row) % 26)
         END || ((i % per_row) + 1)::text
$$;

-- Fail before doing anything if this seed was already applied.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM shows WHERE id IN ('show-1','show-2','show-3','show-4','show-5')) THEN
        RAISE EXCEPTION 'Paytm seed data already exists. Aborting to prevent duplicate seed data.';
    END IF;
END $$;

INSERT INTO shows (id, name, price_paise, per_user_limit) VALUES
  ('show-1', 'hot-seat-storm',      25000, 4),
  ('show-2', 'friday-night',        25000, 4),
  ('show-3', 'per-user-limit-test', 15000, 4),
  ('show-4', 'onsale-20k',          50000, 4),
  ('show-5', 'stadium-concert',    120000, 4);

WITH def(show_id, total, per_row) AS (VALUES
  ('show-1',   100,  20),
  ('show-2',   900,  30),
  ('show-3',    50,  10),
  ('show-4', 20000, 100),
  ('show-5', 28950, 150)
)
INSERT INTO seats (id, show_id, seat_number, status)
SELECT gen_random_uuid(), d.show_id, pg_temp.seat_label(i, d.per_row), 'AVAILABLE'
FROM def d, generate_series(0, d.total - 1) AS i;

-- 100 users x 3 seats = 300 CONFIRMED seats on show-2.
INSERT INTO reservations (id, show_id, user_id, amount_paise, status)
SELECT md5('prebooked-reservation-' || k)::uuid,
       sh.id,
       'prebook-user-' || lpad(k::text, 3, '0'),
       3 * sh.price_paise,
       'CONFIRMED'
FROM generate_series(1, 100) AS k
CROSS JOIN shows sh
WHERE sh.id = 'show-2';

INSERT INTO reservation_seats (reservation_id, seat_id)
SELECT md5('prebooked-reservation-' || (i / 3 + 1))::uuid, s.id
FROM generate_series(0, 299) AS i
JOIN seats s
  ON s.show_id = 'show-2'
 AND s.seat_number = pg_temp.seat_label(i, 30);

-- Restrict this update to the pre-booked show only.
UPDATE seats
SET status = 'CONFIRMED', updated_at = now()
WHERE show_id = 'show-2'
  AND id IN (
      SELECT rs.seat_id
      FROM reservation_seats rs
      JOIN reservations r ON r.id = rs.reservation_id
      WHERE r.show_id = 'show-2'
        AND rs.released_at IS NULL
  );

-- The booking service coordinates same-user/same-show operations through this row.
INSERT INTO show_user_locks (show_id, user_id)
SELECT 'show-2',
       'prebook-user-' || lpad(k::text, 3, '0')
FROM generate_series(1, 100) AS k;

COMMIT;

ANALYZE;
