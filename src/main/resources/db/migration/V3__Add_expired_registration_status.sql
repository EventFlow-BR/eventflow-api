ALTER TABLE registrations
DROP CONSTRAINT chk_registrations_status;

ALTER TABLE registrations
    ADD CONSTRAINT chk_registrations_status
        CHECK (
            status IN (
                       'PENDING',
                       'CONFIRMED',
                       'CANCELLED',
                       'EXPIRED'
                )
            );