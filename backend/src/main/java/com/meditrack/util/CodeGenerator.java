package com.meditrack.util;

import java.time.Year;

/**
 * Generates human-friendly sequential codes:
 *   Patient -> PAT-2026-001
 *   Doctor  -> DOC-2026-001
 *   Nurse   -> NUR-2026-001
 */
public class CodeGenerator {

    public static String generate(String prefix, long existingCountForThisYear) {
        int year = Year.now().getValue();
        long next = existingCountForThisYear + 1;
        return String.format("%s-%d-%03d", prefix, year, next);
    }

    public static String patientCode(long existingCountThisYear) {
        return generate("PAT", existingCountThisYear);
    }

    public static String doctorCode(long existingCount) {
        return String.format("DOC-%03d", existingCount + 1);
    }

    public static String nurseCode(long existingCount) {
        return String.format("NUR-%03d", existingCount + 1);
    }
}
