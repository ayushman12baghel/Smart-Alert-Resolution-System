import { useEffect, useState, useCallback } from "react";
import {
  Activity,
  XCircle,
  Loader2,
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  ChevronRight as ArrowRight,
} from "lucide-react";
import { fetchActiveAlerts, fetchClosedAlerts } from "../api/api.js";
import clsx from "clsx";

// ── Constants ────────────────────────────────────────────────────────────────

const PAGE_SIZE = 5;

const TABS = [
  { key: "active", label: "Active Alerts",  Icon: Activity },
  { key: "closed", label: "Closed Alerts",  Icon: XCircle  },
];

/**
 * Per-status badge styles.
 * AUTO_CLOSED → grey/slate (system-closed)
 * RESOLVED    → green     (manually resolved by operator)
 * These are the existing Tailwind utilities from index.css.
 */
const STATUS_BADGE = {
  OPEN:        "badge-open",
  ESCALATED:   "badge-escalated",
  AUTO_CLOSED: "badge-auto-closed",
  RESOLVED:    "badge-resolved",
};

const SEVERITY_BAR = {
  CRITICAL: "bg-red-500",
  WARNING:  "bg-amber-500",
  INFO:     "bg-sky-500",
};

// ── Helpers ──────────────────────────────────────────────────────────────────

function fmt(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-IN", {
    day:    "2-digit",
    month:  "short",
    hour:   "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

// ── AlertRow sub-component ───────────────────────────────────────────────────

function AlertRow({ alert, onClick }) {
  return (
    <button
      onClick={() => onClick?.(alert)}
      className="w-full text-left flex items-start gap-3 p-3 rounded-xl
                 bg-slate-800/50 hover:bg-slate-800 border border-slate-800
                 hover:border-slate-700 transition-all group"
    >
      {/* Severity indicator bar */}
      <span
        className={clsx(
          "mt-0.5 w-1 self-stretch rounded-full flex-shrink-0",
          SEVERITY_BAR[alert.severity] ?? "bg-slate-600"
        )}
      />

      <div className="flex-1 min-w-0">
        {/* Row 1: source type */}
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-medium text-slate-200 truncate">
            {alert.sourceType ?? "Unknown Source"}
          </span>
          <span className={clsx(STATUS_BADGE[alert.status] ?? "badge")}>
            {alert.status?.replace("_", " ")}
          </span>
        </div>
        {/* Row 2: driver ID + timestamp */}
        <div className="flex items-center gap-3 mt-1 text-xs text-slate-500">
          <code className="font-mono">{alert.driverId}</code>
          <span>·</span>
          <span>{fmt(alert.timestamp)}</span>
        </div>
      </div>

      {/* Arrow hint — only visible on hover for active/clickable rows */}
      <ArrowRight
        className="w-4 h-4 text-slate-700 group-hover:text-slate-400
                   flex-shrink-0 self-center transition-colors"
      />
    </button>
  );
}

// ── Pagination bar ────────────────────────────────────────────────────────────

function Pagination({ page, totalPages, onPrev, onNext }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between mt-4 pt-3 border-t border-slate-800">
      <span className="text-xs text-slate-500">
        Page {page + 1} of {totalPages}
      </span>
      <div className="flex items-center gap-2">
        <button
          onClick={onPrev}
          disabled={page === 0}
          className="btn-ghost px-2.5 py-1.5 disabled:opacity-30"
        >
          <ChevronLeft className="w-4 h-4" />
          Prev
        </button>
        <button
          onClick={onNext}
          disabled={page >= totalPages - 1}
          className="btn-ghost px-2.5 py-1.5 disabled:opacity-30"
        >
          Next
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

// ── Main Component ────────────────────────────────────────────────────────────

/**
 * AlertsFeed — two-tab feed showing Active (OPEN/ESCALATED) and Closed alerts.
 *
 * Active rows are clickable → pass full alert object to the drill-down modal
 * so operators can trigger "Resolve Manually".
 *
 * @param {{ refreshKey: number, onAlertClick: (alert: object) => void }} props
 */
export default function AlertsFeed({ refreshKey, onAlertClick }) {
  const [tab, setTab]             = useState("active");

  // Active tab state
  const [activeAlerts, setActiveAlerts]     = useState([]);
  const [activePage, setActivePage]         = useState(0);
  const [activeTotalPages, setActiveTotalPages] = useState(0);
  const [activeTotalItems, setActiveTotalItems] = useState(0);
  const [activeLoading, setActiveLoading]   = useState(true);
  const [activeError, setActiveError]       = useState(null);

  // Closed tab state
  const [closedAlerts, setClosedAlerts]         = useState([]);
  const [closedPage, setClosedPage]             = useState(0);
  const [closedTotalPages, setClosedTotalPages] = useState(0);
  const [closedTotalItems, setClosedTotalItems] = useState(0);
  const [closedLoading, setClosedLoading]       = useState(true);
  const [closedError, setClosedError]           = useState(null);

  // ── Fetch active alerts ────────────────────────────────────────────────
  const loadActive = useCallback((page) => {
    let cancelled = false;
    setActiveLoading(true);
    setActiveError(null);

    fetchActiveAlerts(page, PAGE_SIZE)
      .then((res) => {
        if (!cancelled) {
          const pg = res.data;
          setActiveAlerts(pg.content ?? []);
          setActiveTotalPages(pg.totalPages ?? 0);
          setActiveTotalItems(pg.totalElements ?? 0);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setActiveError("Failed to load active alerts.");
          console.error(err);
        }
      })
      .finally(() => { if (!cancelled) setActiveLoading(false); });

    return () => { cancelled = true; };
  }, []);

  // ── Fetch closed alerts (AUTO_CLOSED + RESOLVED) ─────────────────────
  const loadClosed = useCallback((page) => {
    let cancelled = false;
    setClosedLoading(true);
    setClosedError(null);

    fetchClosedAlerts(page, PAGE_SIZE)
      .then((res) => {
        if (!cancelled) {
          const pg = res.data;
          setClosedAlerts(pg.content ?? []);
          setClosedTotalPages(pg.totalPages ?? 0);
          setClosedTotalItems(pg.totalElements ?? 0);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setClosedError("Failed to load closed alerts.");
          console.error(err);
        }
      })
      .finally(() => { if (!cancelled) setClosedLoading(false); });

    return () => { cancelled = true; };
  }, []);

  // Trigger re-fetch when dependencies change
  useEffect(() => loadActive(activePage), [activePage, refreshKey, loadActive]);
  useEffect(() => loadClosed(closedPage),  [closedPage,  refreshKey, loadClosed]);

  // ── Tab switch helpers ────────────────────────────────────────────────
  function switchTab(key) {
    setTab(key);
  }


  // ── Derived ──────────────────────────────────────────────────────────
  const isActive = tab === "active";
  const loading  = isActive ? activeLoading  : closedLoading;
  const error    = isActive ? activeError    : closedError;
  const alerts   = isActive ? activeAlerts   : closedAlerts;
  const total    = isActive ? activeTotalItems : closedTotalItems;
  const page     = isActive ? activePage     : closedPage;
  const totalPgs = isActive ? activeTotalPages : closedTotalPages;

  const emptyMsg = isActive
    ? "No open or escalated alerts. System is clear."
    : "No closed alerts in this window.";

  // ── Render ────────────────────────────────────────────────────────────
  return (
    <div className="card flex flex-col">

      {/* ── Tabs ─────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex gap-1 bg-slate-800 rounded-lg p-1">
          {TABS.map(({ key, label, Icon }) => (
            <button
              key={key}
              onClick={() => switchTab(key)}
              className={clsx(
                "flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-colors",
                tab === key
                  ? "bg-slate-600 text-white shadow"
                  : "text-slate-500 hover:text-slate-300"
              )}
            >
              <Icon className="w-3.5 h-3.5" />
              {label}
              {key === "active" && !activeLoading && activeTotalItems > 0 && (
                <span className="ml-0.5 inline-flex items-center justify-center
                                 min-w-[1.1rem] h-[1.1rem] rounded-full
                                 bg-red-500/20 text-red-400 text-[10px] font-bold px-1">
                  {activeTotalItems}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Total count */}
        {!loading && (
          <span className="text-xs text-slate-500">{total} total</span>
        )}
      </div>

      {/* Legend — closed tab only: helps users distinguish AUTO_CLOSED vs RESOLVED */}
      {!isActive && (
        <div className="flex items-center gap-4 mb-3 text-xs text-slate-500">
          <span className="flex items-center gap-1.5">
            <span className="badge-auto-closed">AUTO CLOSED</span>
            System
          </span>
          <span className="flex items-center gap-1.5">
            <span className="badge-resolved">RESOLVED</span>
            Manual
          </span>
        </div>
      )}

      {/* ── Body ─────────────────────────────────────────────────────── */}

      {loading && (
        <div className="flex items-center justify-center py-10">
          <Loader2 className="w-5 h-5 text-slate-500 animate-spin" />
        </div>
      )}

      {!loading && error && (
        <div className="flex items-center gap-2 text-red-400 text-sm py-6 justify-center">
          <AlertCircle className="w-4 h-4" />
          {error}
        </div>
      )}

      {!loading && !error && (
        <>
          {alerts.length === 0 ? (
            <p className="text-center text-slate-600 text-sm py-8">{emptyMsg}</p>
          ) : (
            <div className="space-y-2 flex-1 overflow-y-auto">
              {alerts.map((alert) => (
                <AlertRow
                  key={alert.id}
                  alert={alert}
                  onClick={onAlertClick}
                />
              ))}
            </div>
          )}

          <Pagination
            page={page}
            totalPages={totalPgs}
            onPrev={() =>
              isActive
                ? setActivePage((p) => Math.max(0, p - 1))
                : setClosedPage((p) => Math.max(0, p - 1))
            }
            onNext={() =>
              isActive
                ? setActivePage((p) => Math.min(totalPgs - 1, p + 1))
                : setClosedPage((p) => Math.min(totalPgs - 1, p + 1))
            }
          />
        </>
      )}
    </div>
  );
}
