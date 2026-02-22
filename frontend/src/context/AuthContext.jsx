import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { useNavigate } from "react-router-dom";
import { login as apiLogin } from "../api/api.js";

const AuthContext = createContext(null);

// Decode JWT payload (no verification — server validates every request)
function decodeJwtPayload(token) {
  try {
    const base64Url = token.split(".")[1];
    if (!base64Url) return null;
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      window
        .atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join("")
    );
    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
}

function isTokenExpired(payload) {
  if (!payload?.exp) return true;
  return Date.now() >= payload.exp * 1000;
}

export function AuthProvider({ children }) {
  const navigate = useNavigate();

  const [token, setToken]     = useState(null);
  const [user, setUser]       = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError]     = useState(null);

  // Hydrate from localStorage on mount
  useEffect(() => {
    const storedToken = localStorage.getItem("jwt_token");
    if (storedToken) {
      const payload = decodeJwtPayload(storedToken);
      if (payload && !isTokenExpired(payload)) {
        setToken(storedToken);
        setUser(payload);
      } else {
        localStorage.removeItem("jwt_token");
        localStorage.removeItem("jwt_user");
      }
    }
    setIsLoading(false);
  }, []);

  const login = useCallback(
    async (username, password) => {
      setError(null);
      try {
        const response = await apiLogin(username, password);
        const { token: jwt } = response.data;
        const payload = decodeJwtPayload(jwt);

        localStorage.setItem("jwt_token", jwt);
        localStorage.setItem(
          "jwt_user",
          JSON.stringify({ username: payload?.sub ?? username })
        );

        setToken(jwt);
        setUser(payload);
        navigate("/", { replace: true });
      } catch (err) {
        const message =
          err.response?.status === 401
            ? "Invalid username or password."
            : err.response?.data?.message ?? "Login failed. Please try again.";
        setError(message);
        throw err;
      }
    },
    [navigate]
  );

  const logout = useCallback(() => {
    localStorage.removeItem("jwt_token");
    localStorage.removeItem("jwt_user");
    setToken(null);
    setUser(null);
    setError(null);
    navigate("/login", { replace: true });
  }, [navigate]);

  const value = useMemo(
    () => ({
      token,
      user,
      isLoading,
      isAuthenticated: Boolean(token),
      error,
      setError,
      login,
      logout,
    }),
    [token, user, isLoading, error, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within <AuthProvider>");
  return ctx;
}

export default AuthContext;
