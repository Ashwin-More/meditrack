package com.meditrack.util;

import java.time.Year;
import java.util.function.Predicate;

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

    /**
     * Generated login name for ADMIN accounts only (admin001, admin002, ...).
     * Doctors and nurses choose their own username, so there is no generation for them.
     * The number is a running sequence (existing user count + 1), bumped until unused.
     * Returns null for every other role.
     */
    public static String username(String role, long existingUserCount, Predicate<String> taken) {
        if (!"ADMIN".equals(role)) return null;
        long n = existingUserCount + 1;
        String candidate = String.format("admin%03d", n);
        while (taken.test(candidate)) {
            candidate = String.format("admin%03d", ++n);
        }
        return candidate;
    }
}
