import { useEffect, useState, useCallback } from "react";
import { XCircle, Loader2, AlertCircle, ChevronLeft, ChevronRight } from "lucide-react";
import { fetchAutoClosedAlerts } from "../api/api.js";
import clsx from "clsx";

const FILTER_OPTIONS = [
  { label: "24 h",  value: "24h" },
  { label: "7 d",   value: "7d"  },
  { label: "30 d",  value: "30d" },
  { label: "All",   value: ""    },
];

const SEVERITY_BADGE = {
  CRITICAL: "badge-critical",
  WARNING:  "badge-warning",
  INFO:     "badge-info",
};

function formatTimestamp(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-IN", {
    day:    "2-digit",
    month:  "short",
    hour:   "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

const PAGE_SIZE = 5;

/**
 * AutoClosedAlerts — GET /api/dashboard/alerts/auto-closed
 * Displays recently auto-closed alerts with filter (24h/7d/30d) and pagination.
 *
 * @param {{ refreshKey: number, onAlertClick: (id: string) => void }} props
 */
export default function AutoClosedAlerts({ refreshKey, onAlertClick }) {
  const [filter, setFilter]       = useState("24h");
  const [page, setPage]           = useState(0);
  const [alerts, setAlerts]       = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState(null);

  const load = useCallback(
    (currentFilter, currentPage) => {
      let cancelled = false;
      setLoading(true);
      setError(null);

      fetchAutoClosedAlerts(currentFilter || undefined, currentPage, PAGE_SIZE)
        .then((res) => {
          if (!cancelled) {
            const page = res.data;
            setAlerts(page.content ?? []);
            setTotalPages(page.totalPages ?? 0);
            setTotalItems(page.totalElements ?? 0);
          }
        })
        .catch((err) => {
          if (!cancelled) {
            setError("Failed to load auto-closed alerts.");
            console.error(err);
          }
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });

      return () => { cancelled = true; };
    },
    []
  );

  // Re-fetch when filter, page, or parent refreshKey changes
  useEffect(() => {
    return load(filter, page);
  }, [filter, page, refreshKey, load]);

  function handleFilterChange(newFilter) {
    setFilter(newFilter);
    setPage(0); // reset to first page on filter change
  }

  return (
    <div className="card flex flex-col">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2">
          <XCircle className="w-5 h-5 text-slate-400" />
          <h2 className="font-semibold text-white text-base">Auto-Closed</h2>
          {!loading && (
            <span className="text-xs text-slate-500">
              ({totalItems} total)
            </span>
          )}
        </div>

        {/* Filter chips */}
        <div className="flex items-center gap-1 bg-slate-800 rounded-lg p-1">
          {FILTER_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              onClick={() => handleFilterChange(opt.value)}
              className={clsx(
                "px-3 py-1 rounded-md text-xs font-medium transition-colors",
                filter === opt.value
                  ? "bg-slate-600 text-white shadow"
                  : "text-slate-500 hover:text-slate-300"
              )}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* Loading spinner */}
      {loading && (
        <div className="flex items-center justify-center py-10">
          <Loader2 className="w-5 h-5 text-slate-500 animate-spin" />
        </div>
      )}

      {/* Error */}
      {!loading && error && (
        <div className="flex items-center gap-2 text-red-400 text-sm py-6 justify-center">
          <AlertCircle className="w-4 h-4" />
          {error}
        </div>
      )}

      {/* Alert rows */}
      {!loading && !error && (
        <>
          {alerts.length === 0 ? (
            <p className="text-center text-slate-600 text-sm py-8">
              No auto-closed alerts in this window.
            </p>
          ) : (
            <div className="space-y-2 flex-1 overflow-y-auto">
              {alerts.map((alert) => (
                <button
                  key={alert.id}
                  onClick={() => onAlertClick?.(alert)}
                  className="w-full text-left flex items-start gap-3 p-3 rounded-xl
                             bg-slate-800/50 hover:bg-slate-800 border border-slate-800
                             hover:border-slate-700 transition-all group"
                >
                  {/* Severity indicator bar */}
                  <span
                    className={clsx(
                      "mt-0.5 w-1 self-stretch rounded-full flex-shrink-0",
                      {
                        "bg-red-500":    alert.severity === "CRITICAL",
                        "bg-amber-500":  alert.severity === "WARNING",
                        "bg-sky-500":    alert.severity === "INFO",
                      }
                    )}
                  />

                  <div className="flex-1 min-w-0">
                    {/* Row 1: source type + severity badge */}
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-medium text-slate-200 truncate">
                        {alert.sourceType ?? "Unknown Source"}
                      </span>
                      <span
                        className={clsx(
                          SEVERITY_BADGE[alert.severity] ?? "badge"
                        )}
                      >
                        {alert.severity}
                      </span>
                    </div>

                    {/* Row 2: driver + timestamp */}
                    <div className="flex items-center gap-3 mt-1 text-xs text-slate-500">
                      <code className="font-mono">{alert.driverId}</code>
                      <span>·</span>
                      <span>{formatTimestamp(alert.timestamp)}</span>
                    </div>
                  </div>

                  {/* Status badge */}
                  <span className="badge-auto-closed flex-shrink-0">
                    AUTO-CLOSED
                  </span>
                </button>
              ))}
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between mt-4 pt-3 border-t border-slate-800">
              <span className="text-xs text-slate-500">
                Page {page + 1} of {totalPages}
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="btn-ghost px-2.5 py-1.5 disabled:opacity-30"
                >
                  <ChevronLeft className="w-4 h-4" />
                  Prev
                </button>
                <button
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="btn-ghost px-2.5 py-1.5 disabled:opacity-30"
                >
                  Next
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
