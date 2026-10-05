// js/api.js
// Thin fetch wrapper around the MediTrack Spring Boot API.
// Handles JWT storage, automatic refresh-on-401 (once), and JSON/multipart bodies.

const BASE_URL = window.MEDITRACK_API_BASE || "http://localhost:8080/api";

const TOKEN_KEY = "meditrack.token";
const REFRESH_KEY = "meditrack.refreshToken";
const USER_KEY = "meditrack.user";

export const auth = {
  getToken: () => localStorage.getItem(TOKEN_KEY),
  getRefreshToken: () => localStorage.getItem(REFRESH_KEY),
  getUser: () => {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  },
  setSession: (authResponse) => {
    localStorage.setItem(TOKEN_KEY, authResponse.token);
    if (authResponse.refreshToken) {
      localStorage.setItem(REFRESH_KEY, authResponse.refreshToken);
    }
    localStorage.setItem(USER_KEY, JSON.stringify({
      userId: authResponse.userId,
      username: authResponse.username,
      fullName: authResponse.fullName,
      role: authResponse.role,
    }));
  },
  clearSession: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
  },
  isLoggedIn: () => !!localStorage.getItem(TOKEN_KEY),
};

class ApiError extends Error {
  constructor(message, status, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

let refreshInFlight = null;

async function doRefresh() {
  const refreshToken = auth.getRefreshToken();
  if (!refreshToken) throw new ApiError("No refresh token", 401);

  const res = await fetch(`${BASE_URL}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) throw new ApiError("Session expired", 401);
  const data = await res.json();
  auth.setSession(data);
  return data.token;
}

/**
 * Core request function.
 * @param {string} path - path relative to BASE_URL, e.g. "/patients"
 * @param {object} options - { method, body, isMultipart, skipAuth }
 */
async function request(path, options = {}) {
  const { method = "GET", body, isMultipart = false, skipAuth = false, _retried = false } = options;

  const headers = {};
  if (!isMultipart) headers["Content-Type"] = "application/json";
  if (!skipAuth) {
    const token = auth.getToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body ? (isMultipart ? body : JSON.stringify(body)) : undefined,
  });

  if (res.status === 401 && !skipAuth && !_retried && auth.getRefreshToken()) {
    // try a single silent refresh, then retry the original call once
    try {
      refreshInFlight = refreshInFlight || doRefresh();
      await refreshInFlight;
    } catch (e) {
      auth.clearSession();
      window.location.hash = "#/login";
      throw new ApiError("Session expired. Please log in again", 401);
    } finally {
      refreshInFlight = null;
    }
    return request(path, { ...options, _retried: true });
  }

  if (res.status === 204) return null;

  const contentType = res.headers.get("content-type") || "";
  const isJson = contentType.includes("application/json");
  const payload = isJson ? await res.json().catch(() => null) : await res.blob();

  if (!res.ok) {
    const message = (isJson && payload && payload.message) || `Request failed (${res.status})`;
    const details = isJson && payload ? payload.details : null;
    if (res.status === 401) {
      auth.clearSession();
      window.location.hash = "#/login";
    }
    throw new ApiError(message, res.status, details);
  }

  return payload;
}

export const api = {
  // ---- auth (password) ----
  login: (identifier, password, portal) => request("/auth/login", { method: "POST", body: { username: identifier, password, portal }, skipAuth: true }),
  register: (payload) => request("/auth/register", { method: "POST", body: payload, skipAuth: true }),
  logout: () => request("/auth/logout", { method: "POST" }),

  // ---- auth (OTP: verification, password reset, passwordless login) ----
  verifyOtp: (identifier, otp) => request("/auth/verify-otp", { method: "POST", body: { identifier, otp }, skipAuth: true }),
  resendOtp: (identifier) => request("/auth/resend-otp", { method: "POST", body: { identifier }, skipAuth: true }),
  forgotPassword: (identifier) => request("/auth/forgot-password", { method: "POST", body: { identifier }, skipAuth: true }),
  resetPassword: (identifier, otp, newPassword) =>
    request("/auth/reset-password", { method: "POST", body: { identifier, otp, newPassword }, skipAuth: true }),
  requestLoginOtp: (identifier, portal) => request("/auth/login-otp/request", { method: "POST", body: { identifier, portal }, skipAuth: true }),
  verifyLoginOtp: (identifier, otp, portal) => request("/auth/login-otp/verify", { method: "POST", body: { identifier, otp, portal }, skipAuth: true }),

  // ---- current user ----
  getMe: () => request("/users/me"),
  updateProfile: (payload) => request("/users/me", { method: "PUT", body: payload }),
  changePassword: (currentPassword, newPassword) =>
    request("/users/me/password", { method: "PUT", body: { currentPassword, newPassword } }),

  // ---- patients ----
  searchPatients: (q = "", page = 0, size = 20) =>
    request(`/patients?q=${encodeURIComponent(q)}&page=${page}&size=${size}&sort=createdAt,desc`),
  /** forAssignment=true: unfiltered, hospital-wide list — used by the Manage Assignments page so a
   *  NURSE can assign any patient (same power as admin for that one action), not just her own. */
  searchPatientsForAssignment: (q = "", page = 0, size = 20) =>
    request(`/patients?q=${encodeURIComponent(q)}&page=${page}&size=${size}&sort=lastName,asc&forAssignment=true`),
  getPatient: (id) => request(`/patients/${id}`),
  createPatient: (payload) => request("/patients", { method: "POST", body: payload }),
  updatePatient: (id, payload) => request(`/patients/${id}`, { method: "PUT", body: payload }),
  addAllergy: (patientId, payload) => request(`/patients/${patientId}/allergies`, { method: "POST", body: payload }),
  addCondition: (patientId, payload) => request(`/patients/${patientId}/conditions`, { method: "POST", body: payload }),

  // ---- visits ----
  createVisit: (payload) => request("/visits", { method: "POST", body: payload }),
  getVisit: (id) => request(`/visits/${id}`),
  getVisitsForPatient: (patientId) => request(`/visits/patient/${patientId}`),
  closeVisit: (id) => request(`/visits/${id}/close`, { method: "POST" }),
  addDiagnosis: (visitId, payload) => request(`/visits/${visitId}/diagnoses`, { method: "POST", body: payload }),

  // ---- prescriptions ----
  createPrescription: (payload) => request("/prescriptions", { method: "POST", body: payload }),
  getPrescription: (id) => request(`/prescriptions/${id}`),
  getPrescriptionsForPatient: (patientId) => request(`/prescriptions/patient/${patientId}`),

  // ---- medical tests ----
  orderTest: (payload) => request("/medical-tests", { method: "POST", body: payload }),
  updateTestResult: (id, payload) => request(`/medical-tests/${id}/result`, { method: "PUT", body: payload }),
  getTest: (id) => request(`/medical-tests/${id}`),
  getTestsForPatient: (patientId) => request(`/medical-tests/patient/${patientId}`),

  // ---- reports (attached to a specific test) ----
  uploadReport: (testId, file, description) => {
    const form = new FormData();
    form.append("file", file);
    if (description) form.append("description", description);
    return request(`/reports/tests/${testId}/upload`, { method: "POST", body: form, isMultipart: true });
  },
  downloadReportUrl: (reportId) => `${BASE_URL}/reports/${reportId}/download`,
  downloadReport: async (reportId, fileName) => {
    const blob = await request(`/reports/${reportId}/download`, { method: "GET" });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName || "report";
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  },

  // ---- medical documents / attachments (general patient documents) ----
  getPatientDocuments: (patientId) => request(`/patients/${patientId}/documents`),
  uploadPatientDocument: (patientId, { documentName, documentType, file, description }) => {
    const form = new FormData();
    form.append("file", file);
    if (documentName) form.append("documentName", documentName);
    if (documentType) form.append("documentType", documentType);
    if (description) form.append("description", description);
    return request(`/patients/${patientId}/documents`, { method: "POST", body: form, isMultipart: true });
  },
  /** Fetches the document for in-page viewing (server sends it inline, not as an attachment). */
  viewPatientDocument: (documentId) => request(`/patient-documents/${documentId}/view`, { method: "GET" }),
  downloadPatientDocument: async (documentId, fileName) => {
    const blob = await request(`/patient-documents/${documentId}/download`, { method: "GET" });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName || "document";
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  },

  // ---- vitals ----
  recordVitals: (payload) => request("/vitals", { method: "POST", body: payload }),
  getVitalsForPatient: (patientId) => request(`/vitals/patient/${patientId}`),

  // ---- appointments ----
  createAppointment: (payload) => request("/appointments", { method: "POST", body: payload }),
  getAppointments: () => request("/appointments"),
  getAppointmentsForPatient: (patientId) => request(`/appointments/patient/${patientId}`),
  updateAppointmentStatus: (id, status) => request(`/appointments/${id}/status`, { method: "PUT", body: { status } }),

  // ---- doctors / nurses ----
  getDoctors: () => request("/doctors"),
  getNurses: () => request("/nurses"),

  // ---- admin ----
  createUser: (payload) => request("/admin/users", { method: "POST", body: payload }),
  getAllUsers: () => request("/admin/users"),
  getPendingUsers: () => request("/admin/users/pending"),
  resendUserVerification: (id) => request(`/admin/users/${id}/resend-verification`, { method: "POST" }),
  approveUser: (id) => request(`/admin/users/${id}/approve`, { method: "POST" }),
  rejectUser: (id) => request(`/admin/users/${id}/reject`, { method: "POST" }),
  disableUser: (id) => request(`/admin/users/${id}/disable`, { method: "POST" }),
  enableUser: (id) => request(`/admin/users/${id}/enable`, { method: "POST" }),
  /** Admin-only: full assignment (doctor AND/OR nurse) for any patient. */
  assignPatient: (patientId, doctorId, nurseId) =>
    request(`/admin/patients/${patientId}/assignment`, { method: "PUT", body: { doctorId, nurseId } }),
  getAuditLogs: (page = 0, size = 25) => request(`/admin/audit-logs?page=${page}&size=${size}&sort=createdAt,desc`),
};

export { ApiError };
