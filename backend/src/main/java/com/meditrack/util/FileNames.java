package com.meditrack.util;

import java.text.Normalizer;

/** Builds filesystem- and header-safe download names. */
public final class FileNames {

    private FileNames() {}

    /** "Rahul  Sharma/../x" -> "Rahul_Sharma_x": accents stripped, only letters/digits/hyphen, words joined by "_". */
    public static String safePart(String value, String fallback) {
        String s = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.replaceAll("[^A-Za-z0-9-]+", "_").replaceAll("^_+|_+$", "");
        return s.isEmpty() ? fallback : s;
    }

    /** PatientName_DocumentType.ext, e.g. Rahul_Sharma_Lab_Report.pdf */
    public static String documentDownloadName(String patientName, String documentTypeLabel, String extension) {
        return safePart(patientName, "Patient") + "_" + safePart(documentTypeLabel, "Document") + "." + extension;
    }
}
