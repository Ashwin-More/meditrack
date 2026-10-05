package com.meditrack.service;

import com.meditrack.dto.DiagnosisRequest;
import com.meditrack.dto.VisitRequest;
import com.meditrack.dto.DiagnosisResponse;
import com.meditrack.dto.VisitResponse;

import java.util.List;

public interface VisitService {
    VisitResponse createVisit(VisitRequest request);
    VisitResponse getVisit(Integer visitId);
    List<VisitResponse> getVisitsForPatient(Integer patientId);
    VisitResponse closeVisit(Integer visitId);
    DiagnosisResponse addDiagnosis(Integer visitId, DiagnosisRequest request);
}
