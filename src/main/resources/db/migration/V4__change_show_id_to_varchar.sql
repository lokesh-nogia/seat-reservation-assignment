-- V4__change_show_id_to_varchar.sql

-- 1. Drop constraints
ALTER TABLE seats DROP CONSTRAINT seats_show_id_fkey;
ALTER TABLE reservations DROP CONSTRAINT reservations_show_id_fkey;
ALTER TABLE show_user_locks DROP CONSTRAINT show_user_locks_show_id_fkey;
ALTER TABLE idempotency_keys DROP CONSTRAINT idempotency_keys_show_id_fkey;

-- 2. Alter column types
ALTER TABLE shows ALTER COLUMN id TYPE VARCHAR(128);
ALTER TABLE seats ALTER COLUMN show_id TYPE VARCHAR(128);
ALTER TABLE reservations ALTER COLUMN show_id TYPE VARCHAR(128);
ALTER TABLE show_user_locks ALTER COLUMN show_id TYPE VARCHAR(128);
ALTER TABLE idempotency_keys ALTER COLUMN show_id TYPE VARCHAR(128);

-- 3. Re-add constraints
ALTER TABLE seats ADD CONSTRAINT seats_show_id_fkey FOREIGN KEY (show_id) REFERENCES shows(id);
ALTER TABLE reservations ADD CONSTRAINT reservations_show_id_fkey FOREIGN KEY (show_id) REFERENCES shows(id);
ALTER TABLE show_user_locks ADD CONSTRAINT show_user_locks_show_id_fkey FOREIGN KEY (show_id) REFERENCES shows(id);
ALTER TABLE idempotency_keys ADD CONSTRAINT idempotency_keys_show_id_fkey FOREIGN KEY (show_id) REFERENCES shows(id);
