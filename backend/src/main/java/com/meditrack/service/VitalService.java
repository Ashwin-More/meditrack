package com.meditrack.service;

import com.meditrack.dto.VitalRequest;
import com.meditrack.dto.VitalResponse;

import java.util.List;

public interface VitalService {
    VitalResponse record(VitalRequest request);
    List<VitalResponse> listForPatient(Integer patientId);
}
