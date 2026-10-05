package com.meditrack.service;

import com.meditrack.dto.VitalRequest;
import com.meditrack.dto.VitalResponse;
import com.meditrack.entity.Patient;
import com.meditrack.entity.User;
import com.meditrack.entity.Vital;
import com.meditrack.enums.AuditAction;
import com.meditrack.exception.BadRequestException;
import com.meditrack.repository.UserRepository;
import com.meditrack.repository.VitalRepository;
import com.meditrack.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VitalServiceImpl implements VitalService {

    private final VitalRepository vitalRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final AccessControlService accessControl;

    @Override
    @Transactional
    public VitalResponse record(VitalRequest r) {
        Patient patient = accessControl.loadAccessiblePatient(r.getPatientId());

        boolean anyValue = r.getTemperatureC() != null || r.getPulseBpm() != null || r.getSystolicBp() != null
                || r.getDiastolicBp() != null || r.getRespiratoryRate() != null || r.getSpo2() != null
                || r.getWeightKg() != null;
        if (!anyValue) {
            throw new BadRequestException("Enter at least one measurement");
        }
        if ((r.getSystolicBp() == null) != (r.getDiastolicBp() == null)) {
            throw new BadRequestException("Blood pressure needs both systolic and diastolic values");
        }
        if (r.getSystolicBp() != null && r.getSystolicBp() <= r.getDiastolicBp()) {
            throw new BadRequestException("Systolic pressure must be higher than diastolic");
        }

        User me = userRepository.findById(accessControl.currentPrincipal().getUserId()).orElse(null);
        Vital vital = vitalRepository.save(Vital.builder()
                .patient(patient)
                .recordedBy(me)
                .temperatureC(r.getTemperatureC())
                .pulseBpm(r.getPulseBpm())
                .systolicBp(r.getSystolicBp())
                .diastolicBp(r.getDiastolicBp())
                .respiratoryRate(r.getRespiratoryRate())
                .spo2(r.getSpo2())
                .weightKg(r.getWeightKg())
                .notes(r.getNotes())
                .build());

        auditLogService.log(me, AuditAction.CREATE, "vitals", String.valueOf(vital.getVitalId()),
                "Vitals recorded for " + patient.getPatientCode(), null);
        return toResponse(vital);
    }

    @Override
    public List<VitalResponse> listForPatient(Integer patientId) {
        accessControl.loadAccessiblePatient(patientId);
        return vitalRepository.findByPatient_PatientIdOrderByRecordedAtDesc(patientId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    private VitalResponse toResponse(Vital v) {
        User by = v.getRecordedBy();
        return VitalResponse.builder()
                .vitalId(v.getVitalId())
                .patientId(v.getPatient().getPatientId())
                .recordedByName(by != null ? by.getFullName() : null)
                .recordedByRole(by != null && by.getRole() != null ? by.getRole().getRoleName().name() : null)
                .temperatureC(v.getTemperatureC())
                .pulseBpm(v.getPulseBpm())
                .systolicBp(v.getSystolicBp())
                .diastolicBp(v.getDiastolicBp())
                .respiratoryRate(v.getRespiratoryRate())
                .spo2(v.getSpo2())
                .weightKg(v.getWeightKg())
                .notes(v.getNotes())
                .recordedAt(v.getRecordedAt())
                .build();
    }
}
