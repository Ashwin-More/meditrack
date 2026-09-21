package com.meditrack.service;

import com.meditrack.dto.DiagnosisRequest;
import com.meditrack.dto.VisitRequest;
import com.meditrack.dto.DiagnosisResponse;
import com.meditrack.dto.VisitResponse;
import com.meditrack.entity.Diagnosis;
import com.meditrack.entity.Doctor;
import com.meditrack.entity.Patient;
import com.meditrack.entity.Visit;
import com.meditrack.enums.VisitStatus;
import com.meditrack.enums.VisitType;
import com.meditrack.exception.ResourceNotFoundException;
import com.meditrack.repository.DiagnosisRepository;
import com.meditrack.repository.DoctorRepository;
import com.meditrack.repository.PatientRepository;
import com.meditrack.repository.VisitRepository;
import com.meditrack.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisitServiceImpl implements VisitService {

    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final AccessControlService accessControl;

    @Override
    @Transactional
    public VisitResponse createVisit(VisitRequest request) {
        Patient patient = accessControl.loadAccessiblePatient(request.getPatientId());
        accessControl.assertActingAsDoctor(request.getDoctorId());
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.getDoctorId()));

        Visit visit = Visit.builder()
                .patient(patient)
                .doctor(doctor)
                .visitType(request.getVisitType() != null ? VisitType.valueOf(request.getVisitType()) : VisitType.OPD)
                .chiefComplaint(request.getChiefComplaint())
                .symptoms(request.getSymptoms())
                .doctorNotes(request.getDoctorNotes())
                .followUpDate(request.getFollowUpDate())
                .status(VisitStatus.OPEN)
                .build();

        visit = visitRepository.save(visit);
        return toResponse(visit);
    }

    @Override
    public VisitResponse getVisit(Integer visitId) {
        Visit visit = findVisit(visitId);
        accessControl.assertCanAccessPatient(visit.getPatient());
        return toResponse(visit);
    }

    @Override
    public List<VisitResponse> getVisitsForPatient(Integer patientId) {
        accessControl.loadAccessiblePatient(patientId);
        return visitRepository.findByPatient_PatientIdOrderByVisitDateDesc(patientId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VisitResponse closeVisit(Integer visitId) {
        Visit visit = findVisit(visitId);
        accessControl.assertCanAccessPatient(visit.getPatient());
        visit.setStatus(VisitStatus.CLOSED);
        visit = visitRepository.save(visit);
        return toResponse(visit);
    }

    @Override
    @Transactional
    public DiagnosisResponse addDiagnosis(Integer visitId, DiagnosisRequest request) {
        Visit visit = findVisit(visitId);
        accessControl.assertCanAccessPatient(visit.getPatient());
        Diagnosis diagnosis = Diagnosis.builder()
                .visit(visit)
                .diagnosisName(request.getDiagnosisName())
                .description(request.getDescription())
                .severity(request.getSeverity())
                .notes(request.getNotes())
                .build();
        diagnosis = diagnosisRepository.save(diagnosis);
        return DiagnosisResponse.builder()
                .diagnosisId(diagnosis.getDiagnosisId())
                .diagnosisName(diagnosis.getDiagnosisName())
                .description(diagnosis.getDescription())
                .diagnosedDate(diagnosis.getDiagnosedDate())
                .severity(diagnosis.getSeverity())
                .notes(diagnosis.getNotes())
                .build();
    }

    private Visit findVisit(Integer visitId) {
        return visitRepository.findById(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found: " + visitId));
    }

    private VisitResponse toResponse(Visit v) {
        List<DiagnosisResponse> diagnoses = v.getDiagnoses().stream().map(d -> DiagnosisResponse.builder()
                .diagnosisId(d.getDiagnosisId())
                .diagnosisName(d.getDiagnosisName())
                .description(d.getDescription())
                .diagnosedDate(d.getDiagnosedDate())
                .severity(d.getSeverity())
                .notes(d.getNotes())
                .build()).collect(Collectors.toList());

        return VisitResponse.builder()
                .visitId(v.getVisitId())
                .patientId(v.getPatient().getPatientId())
                .patientName(v.getPatient().getFullName())
                .doctorId(v.getDoctor().getDoctorId())
                .doctorName(v.getDoctor().getFullName())
                .visitDate(v.getVisitDate())
                .visitType(v.getVisitType() != null ? v.getVisitType().name() : null)
                .chiefComplaint(v.getChiefComplaint())
                .symptoms(v.getSymptoms())
                .doctorNotes(v.getDoctorNotes())
                .followUpDate(v.getFollowUpDate())
                .status(v.getStatus().name())
                .diagnoses(diagnoses)
                .build();
    }
}
