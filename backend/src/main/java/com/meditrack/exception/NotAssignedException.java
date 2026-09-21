package com.meditrack.exception;

import org.springframework.security.access.AccessDeniedException;

/** Thrown when a DOCTOR/NURSE touches a patient (or record) that is not assigned to them. */
public class NotAssignedException extends AccessDeniedException {
    public NotAssignedException(String message) {
        super(message);
    }
}
