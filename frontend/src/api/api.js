import axios from "axios";

// In development, VITE_API_BASE_URL is unset and Vite's proxy forwards /api/* to localhost:8080.
// In production (Vercel), set VITE_API_BASE_URL=https://your-app.onrender.com so requests
// go directly to the Render backend instead of being proxied.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "",
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("jwt_token");
    if (token) {
      config.headers["Authorization"] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Force a full logout on any 401 — avoids leaving the UI in a broken half-authenticated state.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("jwt_token");
      localStorage.removeItem("jwt_user");
      window.location.replace("/login");
    }
    return Promise.reject(error);
  }
);

export const login = (username, password) =>
  api.post("/api/auth/login", { username, password });

export const fetchStats = () => api.get("/api/dashboard/stats");

export const fetchLeaderboard = () => api.get("/api/dashboard/leaderboard");

export const fetchTrends = (tz = "UTC") =>
  api.get("/api/dashboard/trends", { params: { tz } });

export const fetchActiveAlerts = (page = 0, size = 10) =>
  api.get("/api/dashboard/alerts/active", { params: { page, size } });

// AUTO_CLOSED + RESOLVED combined — both system-closed and manually resolved alerts
export const fetchClosedAlerts = (page = 0, size = 10) =>
  api.get("/api/dashboard/alerts/closed", { params: { page, size } });

export const fetchAutoClosedAlerts = (filter, page = 0, size = 10) =>
  api.get("/api/dashboard/alerts/auto-closed", {
    params: { ...(filter && { filter }), page, size },
  });

export const fetchAlertHistory = (alertId) =>
  api.get(`/api/dashboard/alerts/${alertId}/history`);

export const fetchAlert = (id) => api.get(`/api/alerts/${id}`);

export const resolveAlert = (id) => api.put(`/api/alerts/${id}/resolve`);

export const ingestAlert = (payload) => api.post("/api/alerts", payload);

export default api;
