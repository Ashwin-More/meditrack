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
}
