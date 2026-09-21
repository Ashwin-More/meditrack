package com.meditrack.service;

import com.meditrack.dto.PrescriptionItemRequest;
import com.meditrack.dto.PrescriptionRequest;
import com.meditrack.dto.PrescriptionItemResponse;
import com.meditrack.dto.PrescriptionResponse;
import com.meditrack.entity.*;
import com.meditrack.exception.ResourceNotFoundException;
import com.meditrack.repository.*;
import com.meditrack.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final VisitRepository visitRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PatientAllergyRepository allergyRepository;
    private final AccessControlService accessControl;

    @Override
    @Transactional
    public PrescriptionResponse createPrescription(PrescriptionRequest request) {
        Visit visit = visitRepository.findById(request.getVisitId())
                .orElseThrow(() -> new ResourceNotFoundException("Visit not found: " + request.getVisitId()));
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.getDoctorId()));
        Patient patient = accessControl.loadAccessiblePatient(request.getPatientId());
        accessControl.assertActingAsDoctor(request.getDoctorId());
        if (!visit.getPatient().getPatientId().equals(patient.getPatientId())) {
            throw new com.meditrack.exception.BadRequestException("That visit does not belong to this patient");
        }

        Prescription prescription = Prescription.builder()
                .visit(visit)
                .doctor(doctor)
                .patient(patient)
                .instructions(request.getInstructions())
                .build();

        for (PrescriptionItemRequest itemReq : request.getItems()) {
            PrescriptionItem item = PrescriptionItem.builder()
                    .prescription(prescription)
                    .medicineName(itemReq.getMedicineName())
                    .dosage(itemReq.getDosage())
                    .frequency(itemReq.getFrequency())
                    .duration(itemReq.getDuration())
                    .route(itemReq.getRoute())
                    .instructions(itemReq.getInstructions())
                    .build();
            prescription.getItems().add(item);
        }

        prescription = prescriptionRepository.save(prescription);
        return toResponse(prescription);
    }

    @Override
    public PrescriptionResponse getPrescription(Integer prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + prescriptionId));
        accessControl.assertCanAccessPatient(prescription.getPatient());
        return toResponse(prescription);
    }

    @Override
    public List<PrescriptionResponse> getPrescriptionsForPatient(Integer patientId) {
        accessControl.loadAccessiblePatient(patientId);
        return prescriptionRepository.findByPatient_PatientIdOrderByPrescriptionDateDesc(patientId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    /** Cross-checks prescribed medicine names against the patient's recorded allergies (simple substring match). */
    private List<String> computeAllergyWarnings(Prescription p) {
        List<PatientAllergy> allergies = allergyRepository.findByPatient_PatientId(p.getPatient().getPatientId());
        if (allergies.isEmpty()) return List.of();

        return p.getItems().stream()
                .flatMap(item -> allergies.stream()
                        .filter(a -> item.getMedicineName().toLowerCase(Locale.ROOT)
                                .contains(a.getAllergyName().toLowerCase(Locale.ROOT)))
                        .map(a -> item.getMedicineName() + " may conflict with known allergy: " + a.getAllergyName()))
                .distinct()
                .collect(Collectors.toList());
    }

    private PrescriptionResponse toResponse(Prescription p) {
        List<PrescriptionItemResponse> items = p.getItems().stream().map(i -> PrescriptionItemResponse.builder()
                .itemId(i.getItemId())
                .medicineName(i.getMedicineName())
                .dosage(i.getDosage())
                .frequency(i.getFrequency())
                .duration(i.getDuration())
                .route(i.getRoute())
                .instructions(i.getInstructions())
                .build()).collect(Collectors.toList());

        return PrescriptionResponse.builder()
                .prescriptionId(p.getPrescriptionId())
                .visitId(p.getVisit().getVisitId())
                .doctorId(p.getDoctor().getDoctorId())
                .doctorName(p.getDoctor().getFullName())
                .patientId(p.getPatient().getPatientId())
                .patientName(p.getPatient().getFullName())
                .prescriptionDate(p.getPrescriptionDate())
                .instructions(p.getInstructions())
                .items(items)
                .allergyWarnings(computeAllergyWarnings(p))
                .build();
    }
}
