-- =====================================================================
-- MediTrack — V6: SUPER_ADMIN tier.
--   A Super Admin sits one level above Admin: only a Super Admin may
--   create/approve/reject/disable/enable an ADMIN (or another SUPER_ADMIN)
--   account — this closes a real gap where any Admin could previously
--   mint or disable other Admins unchecked. Regular hospital operations
--   (Doctors, Nurses, patients) are unaffected: Admins keep running those
--   exactly as before.
-- =====================================================================

ALTER TABLE `roles` DROP CHECK `chk_roles_role_name`;
ALTER TABLE `roles` ADD CONSTRAINT `chk_roles_role_name`
    CHECK (`role_name` IN ('SUPER_ADMIN','ADMIN','DOCTOR','NURSE'));

INSERT INTO `roles` (`role_name`, `description`) VALUES
    ('SUPER_ADMIN', 'Institute-level super administrator — manages Admin accounts and has full system oversight');

-- The actual super admin user account is seeded by DataSeeder.java (bcrypt hashing
-- needs application code, not a plain migration), the same way the demo admin is.
