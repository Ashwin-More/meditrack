package com.meditrack.util;

import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeGeneratorTest {

    @Test
    void patientCode_padsToThreeDigitsAndUsesCurrentYear() {
        int year = Year.now().getValue();
        assertEquals("PAT-" + year + "-001", CodeGenerator.patientCode(0));
        assertEquals("PAT-" + year + "-042", CodeGenerator.patientCode(41));
        assertEquals("PAT-" + year + "-100", CodeGenerator.patientCode(99));
    }

    @Test
    void doctorCode_padsToThreeDigitsNoYear() {
        assertEquals("DOC-001", CodeGenerator.doctorCode(0));
        assertEquals("DOC-010", CodeGenerator.doctorCode(9));
    }

    @Test
    void nurseCode_padsToThreeDigitsNoYear() {
        assertEquals("NUR-001", CodeGenerator.nurseCode(0));
    }

    @Test
    void username_isGeneratedOnlyForAdmins() {
        assertEquals("admin001", CodeGenerator.username("ADMIN", 0, u -> false));
        assertEquals("admin003", CodeGenerator.username("ADMIN", 1, u -> u.equals("admin002")));
        // Doctors, nurses (and super admins) choose their own username - nothing is generated.
        org.junit.jupiter.api.Assertions.assertNull(CodeGenerator.username("DOCTOR", 0, u -> false));
        org.junit.jupiter.api.Assertions.assertNull(CodeGenerator.username("NURSE", 0, u -> false));
        org.junit.jupiter.api.Assertions.assertNull(CodeGenerator.username("SUPER_ADMIN", 0, u -> false));
    }
}
