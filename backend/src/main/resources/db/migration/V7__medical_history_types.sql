-- =====================================================================
-- MediTrack — V7: richer Medical History.
--   * patient_conditions now also holds previous surgeries, previous
--     hospitalizations and important medical notes (record_type).
--   * status gains CHRONIC (Ongoing / Chronic / Recovered).
-- =====================================================================
ALTER TABLE `patient_conditions` ADD COLUMN `record_type` VARCHAR(20) NOT NULL DEFAULT 'CONDITION' AFTER `patient_id`;
ALTER TABLE `patient_conditions` ADD CONSTRAINT `chk_conditions_record_type`
    CHECK (`record_type` IN ('CONDITION','SURGERY','HOSPITALIZATION','NOTE'));
ALTER TABLE `patient_conditions` DROP CHECK `chk_conditions_status`;
ALTER TABLE `patient_conditions` ADD CONSTRAINT `chk_conditions_status`
    CHECK (`status` IN ('ONGOING','CHRONIC','RESOLVED'));
