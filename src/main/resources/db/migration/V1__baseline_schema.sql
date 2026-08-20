-- =============================================================================================
-- V1 - Baseline schema.
--
-- This is a faithful copy of the DDL Hibernate generated for the current entities under the
-- Postgres dialect and Spring Boot's naming strategy, with constraints given explicit names
-- instead of Hibernate's generated hashes.
--
-- On an EXISTING database this file never runs: `spring.flyway.baseline-on-migrate=true` with
-- `baseline-version=1` records it as already applied, because those installs were created by
-- `ddl-auto: update`. It runs only when creating a schema from scratch.
--
-- From here on the schema is owned by these migrations and Hibernate is set to `validate`.
-- =============================================================================================

CREATE TABLE doctors (
    doctor_id        VARCHAR(255) NOT NULL,
    first_name       VARCHAR(255) NOT NULL,
    last_name        VARCHAR(255),
    country_code     VARCHAR(5)   NOT NULL,
    phone_number     VARCHAR(15)  NOT NULL,
    email            VARCHAR(255),
    password         VARCHAR(255) NOT NULL,
    specialization   VARCHAR(255) NOT NULL,
    qualification    VARCHAR(255),
    experience_years INTEGER,
    consultation_fee NUMERIC(10, 2),
    profile_image    VARCHAR(255),
    about            TEXT,
    average_rating   FLOAT(53),
    total_reviews    INTEGER,
    is_active        BOOLEAN,
    created_at       TIMESTAMP(6),
    updated_at       TIMESTAMP(6),
    CONSTRAINT pk_doctors PRIMARY KEY (doctor_id),
    CONSTRAINT uk_doctors_email UNIQUE (email),
    CONSTRAINT uk_doctors_phone UNIQUE (country_code, phone_number),
    CONSTRAINT chk_doctors_specialization CHECK (specialization IN (
        'GENERAL_PRACTITIONER', 'CARDIOLOGIST', 'DERMATOLOGIST', 'NEUROLOGIST', 'ORTHOPEDIC_SURGEON',
        'PEDIATRICIAN', 'PSYCHIATRIST', 'RADIOLOGIST', 'ONCOLOGIST', 'GYNECOLOGIST', 'OPHTHALMOLOGIST',
        'ENT_SPECIALIST', 'ANESTHESIOLOGIST', 'ENDOCRINOLOGIST', 'GASTROENTEROLOGIST', 'UROLOGIST',
        'NEPHROLOGIST', 'PULMONOLOGIST', 'DENTIST', 'PHYSIOTHERAPIST'))
);

CREATE TABLE patients (
    patient_id    VARCHAR(255) NOT NULL,
    first_name    VARCHAR(255) NOT NULL,
    last_name     VARCHAR(255),
    country_code  VARCHAR(5)   NOT NULL,
    phone_number  VARCHAR(15)  NOT NULL,
    email         VARCHAR(255),
    password      VARCHAR(255) NOT NULL,
    gender        VARCHAR(255),
    date_of_birth DATE,
    address       VARCHAR(255),
    created_at    TIMESTAMP(6),
    updated_at    TIMESTAMP(6),
    CONSTRAINT pk_patients PRIMARY KEY (patient_id),
    CONSTRAINT uk_patients_email UNIQUE (email),
    CONSTRAINT uk_patients_phone UNIQUE (country_code, phone_number),
    CONSTRAINT chk_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE TABLE doctor_availability (
    slot_id          VARCHAR(255) NOT NULL,
    doctor_id        VARCHAR(255) NOT NULL,
    slot_date        DATE         NOT NULL,
    start_time       TIME(6)      NOT NULL,
    end_time         TIME(6)      NOT NULL,
    duration_minutes INTEGER      NOT NULL,
    is_available     BOOLEAN      NOT NULL,
    version          BIGINT,
    created_at       TIMESTAMP(6),
    updated_at       TIMESTAMP(6),
    CONSTRAINT pk_doctor_availability PRIMARY KEY (slot_id),
    CONSTRAINT fk_slot_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (doctor_id)
);

CREATE TABLE appointments (
    appointment_id     VARCHAR(255) NOT NULL,
    appointment_number VARCHAR(255) NOT NULL,
    patient_id         VARCHAR(255) NOT NULL,
    doctor_id          VARCHAR(255) NOT NULL,
    slot_id            VARCHAR(255) NOT NULL,
    status             VARCHAR(255) NOT NULL,
    reason_for_visit   VARCHAR(255),
    notes              TEXT,
    version            BIGINT,
    created_at         TIMESTAMP(6),
    updated_at         TIMESTAMP(6),
    CONSTRAINT pk_appointments PRIMARY KEY (appointment_id),
    CONSTRAINT uk_appointment_number UNIQUE (appointment_number),
    -- Named, not inline: GlobalExceptionHandler matches this name to tell a client their chosen
    -- slot was taken rather than reporting a generic "data conflict".
    CONSTRAINT uk_appointment_slot UNIQUE (slot_id),
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id),
    CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (doctor_id),
    CONSTRAINT fk_appointment_slot FOREIGN KEY (slot_id) REFERENCES doctor_availability (slot_id),
    CONSTRAINT chk_appointment_status CHECK (status IN (
        'PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'))
);

CREATE TABLE payments (
    payment_id     VARCHAR(255)   NOT NULL,
    appointment_id VARCHAR(255)   NOT NULL,
    amount         NUMERIC(10, 2) NOT NULL,
    payment_method VARCHAR(255),
    transaction_id VARCHAR(255),
    status         VARCHAR(255),
    payment_date   TIMESTAMP(6),
    updated_at     TIMESTAMP(6),
    CONSTRAINT pk_payments PRIMARY KEY (payment_id),
    CONSTRAINT uk_payment_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_payment_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id),
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('CARD', 'UPI', 'NET_BANKING', 'CASH', 'WALLET')),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED'))
);

CREATE TABLE reviews (
    review_id      VARCHAR(255) NOT NULL,
    appointment_id VARCHAR(255) NOT NULL,
    doctor_id      VARCHAR(255) NOT NULL,
    patient_id     VARCHAR(255) NOT NULL,
    rating         INTEGER      NOT NULL,
    comment        TEXT,
    doctor_reply   TEXT,
    replied_at     TIMESTAMP(6),
    created_at     TIMESTAMP(6),
    CONSTRAINT pk_reviews PRIMARY KEY (review_id),
    CONSTRAINT uk_review_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_review_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id),
    CONSTRAINT fk_review_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (doctor_id),
    CONSTRAINT fk_review_patient FOREIGN KEY (patient_id) REFERENCES patients (patient_id),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE TABLE appointment_documents (
    document_id    VARCHAR(255) NOT NULL,
    appointment_id VARCHAR(255) NOT NULL,
    uploader_id    VARCHAR(255) NOT NULL,
    uploader_role  VARCHAR(255) NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    file_url       VARCHAR(255) NOT NULL,
    file_type      VARCHAR(255) NOT NULL,
    document_type  VARCHAR(255) NOT NULL,
    file_size      BIGINT,
    created_at     TIMESTAMP(6),
    updated_at     TIMESTAMP(6),
    CONSTRAINT pk_appointment_documents PRIMARY KEY (document_id),
    CONSTRAINT fk_document_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id)
);

CREATE TABLE notifications (
    notification_id   VARCHAR(255) NOT NULL,
    user_id           VARCHAR(255) NOT NULL,
    title             VARCHAR(255) NOT NULL,
    message           TEXT         NOT NULL,
    type              VARCHAR(255) NOT NULL,
    related_entity_id VARCHAR(255),
    is_read           BOOLEAN      NOT NULL,
    created_at        TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (notification_id),
    CONSTRAINT chk_notification_type CHECK (type IN ('APPOINTMENT_UPDATE', 'GENERAL_ALERT', 'PROMOTIONAL'))
);

CREATE INDEX idx_user_read ON notifications (user_id, is_read);

CREATE TABLE user_devices (
    id              VARCHAR(255) NOT NULL,
    user_id         VARCHAR(255) NOT NULL,
    fcm_token       VARCHAR(255) NOT NULL,
    device_type     VARCHAR(255),
    is_active       BOOLEAN      NOT NULL,
    last_updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_user_devices PRIMARY KEY (id),
    CONSTRAINT uk_user_device_token UNIQUE (fcm_token)
);

CREATE INDEX idx_user_device ON user_devices (user_id, fcm_token);

CREATE TABLE refresh_tokens (
    id                  VARCHAR(255)             NOT NULL,
    user_id             VARCHAR(255)             NOT NULL,
    user_email          VARCHAR(255),
    role                VARCHAR(20)              NOT NULL,
    token_hash          VARCHAR(64)              NOT NULL,
    family_id           VARCHAR(64)              NOT NULL,
    issued_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    absolute_expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked_at          TIMESTAMP(6) WITH TIME ZONE,
    revoked_reason      VARCHAR(32),
    created_ip          VARCHAR(45),
    user_agent          VARCHAR(256),
    device_type         VARCHAR(20),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_revoked_reason CHECK (revoked_reason IN (
        'ROTATED', 'LOGOUT', 'LOGOUT_ALL', 'REUSE_DETECTED', 'SUPERSEDED_BY_CAP'))
);

CREATE INDEX idx_refresh_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_expires ON refresh_tokens (expires_at);

CREATE TABLE push_outbox (
    id                VARCHAR(255)             NOT NULL,
    notification_id   VARCHAR(255)             NOT NULL,
    user_id           VARCHAR(255)             NOT NULL,
    title             VARCHAR(255)             NOT NULL,
    body              TEXT                     NOT NULL,
    type              VARCHAR(32)              NOT NULL,
    related_entity_id VARCHAR(255),
    status            VARCHAR(16)              NOT NULL,
    attempts          INTEGER                  NOT NULL,
    next_attempt_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    claimed_at        TIMESTAMP(6) WITH TIME ZONE,
    claimed_by        VARCHAR(64),
    last_error        VARCHAR(500),
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_push_outbox PRIMARY KEY (id),
    CONSTRAINT uk_push_outbox_notification UNIQUE (notification_id),
    CONSTRAINT chk_push_outbox_status CHECK (status IN ('PENDING', 'IN_FLIGHT', 'SENT', 'DEAD', 'SKIPPED')),
    CONSTRAINT chk_push_outbox_type CHECK (type IN ('APPOINTMENT_UPDATE', 'GENERAL_ALERT', 'PROMOTIONAL'))
);

CREATE INDEX idx_push_outbox_due ON push_outbox (status, next_attempt_at);
