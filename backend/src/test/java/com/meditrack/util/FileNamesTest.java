package com.meditrack.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FileNamesTest {

    @Test
    void documentName_isPatientNameUnderscoreType() {
        assertEquals("Rahul_Sharma_Lab_Report.pdf", FileNames.documentDownloadName("Rahul Sharma", "Lab Report", "pdf"));
        assertEquals("Rahul_Sharma_Prescription.pdf", FileNames.documentDownloadName("Rahul  Sharma", "Prescription", "pdf"));
    }

    @Test
    void unsafeCharacters_areRemoved() {
        String name = FileNames.documentDownloadName("../../etc/pa\"ss\r\nwd", "X-Ray Scan", "pdf");
        assertEquals("etc_pa_ss_wd_X-Ray_Scan.pdf", name);
        assertFalse(name.contains("/") || name.contains("\\") || name.contains("\"") || name.contains("\n"));
        assertEquals("Jose_Nandu_Other.pdf", FileNames.documentDownloadName("José Ñandú", "Other", "pdf"));
        assertEquals("Patient_Document.pdf", FileNames.documentDownloadName("  ", null, "pdf"));
    }
}
