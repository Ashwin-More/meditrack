-- =====================================================================
-- MediTrack — V2: seed the three fixed roles.
-- (DataSeeder.java also does this defensively on boot, so this is safe
-- to run even if the app seeds first — both use find-or-create semantics.)
-- =====================================================================

INSERT INTO `roles` (`role_name`, `description`) VALUES
    ('ADMIN',  'Full system access — manages users, approvals, audit logs'),
    ('DOCTOR', 'Clinical workflow — visits, diagnoses, prescriptions, tests'),
    ('NURSE',  'Supports clinical workflow and patient record-keeping');
