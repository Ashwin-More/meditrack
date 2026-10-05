package com.meditrack.repository;

import com.meditrack.entity.TestReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestReportRepository extends JpaRepository<TestReport, Integer> {
    List<TestReport> findByTest_TestId(Integer testId);
}
