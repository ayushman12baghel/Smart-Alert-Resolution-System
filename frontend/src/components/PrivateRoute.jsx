import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext.jsx";
import { Loader2 } from "lucide-react";

/**
 * Wraps any route that requires authentication.
 * Shows a spinner while the context is hydrating from localStorage.
 * Redirects to /login if no valid token is found.
 */
export default function PrivateRoute({ children }) {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center bg-slate-950">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
      </div>
    );
  }

  return isAuthenticated ? children : <Navigate to="/login" replace />;
}
