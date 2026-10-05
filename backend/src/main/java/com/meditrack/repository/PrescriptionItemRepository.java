package com.meditrack.repository;

import com.meditrack.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Integer> {
    List<PrescriptionItem> findByPrescription_PrescriptionId(Integer prescriptionId);
}
