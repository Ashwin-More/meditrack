package com.meditrack.service;

import com.meditrack.dto.AllergyRequest;
import com.meditrack.dto.AssignmentRequest;
import com.meditrack.dto.ConditionRequest;
import com.meditrack.dto.PatientRequest;
import com.meditrack.dto.AllergyResponse;
import com.meditrack.dto.ConditionResponse;
import com.meditrack.dto.PatientResponse;
import com.meditrack.entity.Doctor;
import com.meditrack.entity.Nurse;
import com.meditrack.entity.Patient;
import com.meditrack.entity.PatientAllergy;
import com.meditrack.entity.PatientCondition;
import com.meditrack.enums.AuditAction;
import com.meditrack.enums.ConditionStatus;
import com.meditrack.enums.Severity;
import com.meditrack.exception.ResourceNotFoundException;
import com.meditrack.repository.DoctorRepository;
import com.meditrack.repository.NurseRepository;
import com.meditrack.repository.UserRepository;
import com.meditrack.repository.PatientAllergyRepository;
import com.meditrack.security.AccessControlService;
import com.meditrack.repository.PatientConditionRepository;
import com.meditrack.repository.PatientRepository;
import com.meditrack.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final PatientAllergyRepository allergyRepository;
    private final PatientConditionRepository conditionRepository;
    private final DoctorRepository doctorRepository;
    private final NurseRepository nurseRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final AccessControlService accessControl;

    @Override
    @Transactional
    public PatientResponse createPatient(PatientRequest request) {
        String prefix = "PAT-" + Year.now().getValue() + "-";
        long countThisYear = patientRepository.countByPatientCodeStartingWith(prefix);

        Patient patient = Patient.builder()
                .patientCode(CodeGenerator.patientCode(countThisYear))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .bloodGroup(request.getBloodGroup())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .build();

        // A patient registered by a doctor/nurse is automatically assigned to that person,
        // otherwise they could not open the record they just created. Admin-created patients stay
        // unassigned until an admin assigns them.
        if (accessControl.isDoctor()) {
            Integer doctorId = accessControl.currentDoctorId();
            if (doctorId != null) patient.setAssignedDoctor(doctorRepository.getReferenceById(doctorId));
        } else if (accessControl.isNurse()) {
            Integer nurseId = accessControl.currentNurseId();
            if (nurseId != null) patient.setAssignedNurse(nurseRepository.getReferenceById(nurseId));
        }

        patient = patientRepository.save(patient);
        return toResponse(patient);
    }

    @Override
    @Transactional
    public PatientResponse assignPatient(Integer patientId, AssignmentRequest request) {
        Patient patient = findPatient(patientId);
        Doctor doctor = null;
        Nurse nurse = null;
        if (request.getDoctorId() != null) {
            doctor = doctorRepository.findById(request.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.getDoctorId()));
        }
        if (request.getNurseId() != null) {
            nurse = nurseRepository.findById(request.getNurseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Nurse not found: " + request.getNurseId()));
        }
        patient.setAssignedDoctor(doctor);
        patient.setAssignedNurse(nurse);
        patient = patientRepository.save(patient);

        auditLogService.log(userRepository.findById(accessControl.currentPrincipal().getUserId()).orElse(null),
                AuditAction.ASSIGN, "patients", String.valueOf(patientId),
                "Assigned to doctor=" + (doctor != null ? doctor.getDoctorCode() : "none")
                        + ", nurse=" + (nurse != null ? nurse.getNurseCode() : "none"), null);
        return toResponse(patient);
    }

    @Override
    @Transactional
    public PatientResponse updatePatient(Integer patientId, PatientRequest request) {
        Patient patient = accessControl.loadAccessiblePatient(patientId);
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setBloodGroup(request.getBloodGroup());
        patient.setPhone(request.getPhone());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setEmergencyContactName(request.getEmergencyContactName());
        patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        patient = patientRepository.save(patient);
        return toResponse(patient);
    }

    @Override
    public PatientResponse getPatient(Integer patientId) {
        return toResponse(accessControl.loadAccessiblePatient(patientId));
    }

    @Override
    public Page<PatientResponse> searchPatients(String query, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        Page<Patient> page;
        if (accessControl.isDoctor()) {
            Integer doctorId = accessControl.currentDoctorId();
            page = doctorId == null ? Page.empty(pageable) : patientRepository.searchForDoctor(q, doctorId, pageable);
        } else if (accessControl.isNurse()) {
            Integer nurseId = accessControl.currentNurseId();
            page = nurseId == null ? Page.empty(pageable) : patientRepository.searchForNurse(q, nurseId, pageable);
        } else {
            page = patientRepository.search(q, pageable);
        }
        return page.map(this::toResponse);
    }

    @Override
    @Transactional
    public AllergyResponse addAllergy(Integer patientId, AllergyRequest request) {
        Patient patient = accessControl.loadAccessiblePatient(patientId);
        PatientAllergy allergy = PatientAllergy.builder()
                .patient(patient)
                .allergyName(request.getAllergyName())
                .reaction(request.getReaction())
                .severity(request.getSeverity() != null ? Severity.valueOf(request.getSeverity()) : null)
                .notes(request.getNotes())
                .build();
        allergy = allergyRepository.save(allergy);
        return AllergyResponse.builder()
                .allergyId(allergy.getAllergyId())
                .allergyName(allergy.getAllergyName())
                .reaction(allergy.getReaction())
                .severity(allergy.getSeverity() != null ? allergy.getSeverity().name() : null)
                .notes(allergy.getNotes())
                .build();
    }

    @Override
    @Transactional
    public ConditionResponse addCondition(Integer patientId, ConditionRequest request) {
        Patient patient = accessControl.loadAccessiblePatient(patientId);
        PatientCondition condition = PatientCondition.builder()
                .patient(patient)
                .conditionName(request.getConditionName())
                .diagnosedDate(request.getDiagnosedDate())
                .status(request.getStatus() != null ? ConditionStatus.valueOf(request.getStatus()) : ConditionStatus.ONGOING)
                .notes(request.getNotes())
                .build();
        condition = conditionRepository.save(condition);
        return ConditionResponse.builder()
                .conditionId(condition.getConditionId())
                .conditionName(condition.getConditionName())
                .diagnosedDate(condition.getDiagnosedDate())
                .status(condition.getStatus().name())
                .notes(condition.getNotes())
                .build();
    }

    private Patient findPatient(Integer patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + patientId));
    }

    private PatientResponse toResponse(Patient p) {
        List<AllergyResponse> allergies = p.getAllergies().stream().map(a -> AllergyResponse.builder()
                .allergyId(a.getAllergyId())
                .allergyName(a.getAllergyName())
                .reaction(a.getReaction())
                .severity(a.getSeverity() != null ? a.getSeverity().name() : null)
                .notes(a.getNotes())
                .build()).collect(Collectors.toList());

        List<ConditionResponse> conditions = p.getConditions().stream().map(c -> ConditionResponse.builder()
                .conditionId(c.getConditionId())
                .conditionName(c.getConditionName())
                .diagnosedDate(c.getDiagnosedDate())
                .status(c.getStatus().name())
                .notes(c.getNotes())
                .build()).collect(Collectors.toList());

        return PatientResponse.builder()
                .patientId(p.getPatientId())
                .patientCode(p.getPatientCode())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .fullName(p.getFullName())
                .dateOfBirth(p.getDateOfBirth())
                .age(p.getAge())
                .gender(p.getGender())
                .bloodGroup(p.getBloodGroup())
                .phone(p.getPhone())
                .email(p.getEmail())
                .address(p.getAddress())
                .emergencyContactName(p.getEmergencyContactName())
                .emergencyContactPhone(p.getEmergencyContactPhone())
                .status(p.getStatus().name())
                .assignedDoctorId(p.getAssignedDoctor() != null ? p.getAssignedDoctor().getDoctorId() : null)
                .assignedDoctorName(p.getAssignedDoctor() != null ? p.getAssignedDoctor().getFullName() : null)
                .assignedNurseId(p.getAssignedNurse() != null ? p.getAssignedNurse().getNurseId() : null)
                .assignedNurseName(p.getAssignedNurse() != null ? p.getAssignedNurse().getFullName() : null)
                .createdAt(p.getCreatedAt())
                .allergies(allergies)
                .conditions(conditions)
                .build();
    }
}
