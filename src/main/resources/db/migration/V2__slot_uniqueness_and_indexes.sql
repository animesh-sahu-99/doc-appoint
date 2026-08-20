-- =============================================================================================
-- V2 - Close the duplicate-slot race at the database, and give the appointment/slot uniqueness
--      a name the application can recognise.
--
-- Written to be safe on both paths: a schema freshly created by V1, and an existing schema
-- created by `ddl-auto: update` (where V1 was baselined away and Hibernate's own generated
-- constraint names are in place).
-- =============================================================================================


-- ---------------------------------------------------------------------------------------------
-- 1. One slot per doctor, date and start time.
--
-- SlotOverlapValidator checks for an overlap and then inserts, with no lock between the two. Two
-- concurrent requests for the same doctor/date/time therefore both see an empty overlap set and
-- both insert. The resulting rows are separate entities with separate @Version values, so neither
-- optimistic locking nor the booking flow can detect that the same consultation now exists twice
-- and can be booked by two different patients. Only the database can rule this out.
--
-- IF NOT EXISTS is not available for ADD CONSTRAINT, hence the catalog check.
--
-- NOTE FOR OPERATORS: if this migration fails with a uniqueness violation, the table already
-- contains duplicate slots produced by that race. That is deliberately not auto-repaired - one of
-- the duplicates may already be booked, so choosing which to keep is a clinical decision, not a
-- migration's. Query them with:
--
--   SELECT doctor_id, slot_date, start_time, count(*), array_agg(slot_id)
--     FROM doctor_availability
--    GROUP BY doctor_id, slot_date, start_time
--   HAVING count(*) > 1;
-- ---------------------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_slot_doctor_date_start'
    ) THEN
        ALTER TABLE doctor_availability
            ADD CONSTRAINT uk_slot_doctor_date_start UNIQUE (doctor_id, slot_date, start_time);
    END IF;
END $$;


-- ---------------------------------------------------------------------------------------------
-- 2. Normalise the name of the appointment/slot uniqueness.
--
-- The constraint itself already exists on any schema Hibernate built, because Appointment.slot is
-- a @OneToOne @JoinColumn - but under a generated name such as `appointments_slot_id_key`.
-- GlobalExceptionHandler matches on `uk_appointment_slot`, so with the generated name the friendly
-- "that slot was just taken" message could never fire and a genuine double-book reported itself as
-- a generic data conflict.
--
-- Rename it where it exists under another name; create it where it does not exist at all.
-- ---------------------------------------------------------------------------------------------
DO $$
DECLARE
    current_name TEXT;
BEGIN
    SELECT c.conname
      INTO current_name
      FROM pg_constraint c
      JOIN pg_class t ON t.oid = c.conrelid
     WHERE t.relname = 'appointments'
       AND c.contype = 'u'
       AND c.conkey = ARRAY[(SELECT a.attnum
                               FROM pg_attribute a
                              WHERE a.attrelid = t.oid
                                AND a.attname = 'slot_id')]
     LIMIT 1;

    IF current_name IS NULL THEN
        ALTER TABLE appointments ADD CONSTRAINT uk_appointment_slot UNIQUE (slot_id);
    ELSIF current_name <> 'uk_appointment_slot' THEN
        EXECUTE format('ALTER TABLE appointments RENAME CONSTRAINT %I TO uk_appointment_slot', current_name);
    END IF;
END $$;


-- ---------------------------------------------------------------------------------------------
-- 3. Index the access path every slot query uses.
--
-- Every read in DoctorAvailabilityRepository filters on doctor_id, and most also on slot_date;
-- the day-at-a-time bulk-create check does too. The table carried no index at all beyond its
-- primary key - unlike notifications, user_devices, refresh_tokens and push_outbox, which all
-- declare theirs on the entity.
--
-- The unique constraint above already covers (doctor_id, slot_date, start_time), so a separate
-- (doctor_id, slot_date) index would be a redundant prefix. Indexed here is the remaining
-- uncovered path: finding a doctor's unclaimed upcoming slots.
-- ---------------------------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_slot_doctor_available
    ON doctor_availability (doctor_id, is_available, slot_date);
