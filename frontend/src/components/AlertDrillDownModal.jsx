import { useEffect, useRef, useState } from "react";
import {
  X,
  CheckCircle2,
  Loader2,
  AlertCircle,
  ChevronRight,
  ClipboardList,
  Clock,
  Tag,
  User,
} from "lucide-react";
import { fetchAlertHistory, resolveAlert } from "../api/api.js";
import clsx from "clsx";

// ── Helpers ──────────────────────────────────────────────────────────────────

const SEVERITY_BADGE = {
  CRITICAL: "badge-critical",
  WARNING: "badge-warning",
  INFO: "badge-info",
};

const STATUS_BADGE = {
  OPEN: "badge-open",
  ESCALATED: "badge-escalated",
  AUTO_CLOSED: "badge-auto-closed",
  RESOLVED: "badge-resolved",
};

function formatTs(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Build a minimal synthetic transition timeline from the alert object alone.
 * Used when the history endpoint is unavailable or returns no rows.
 */
function buildSyntheticHistory(alert) {
  const entries = [
    {
      id: "syn-0",
      previousStatus: null,          // null → rendered as "CREATED"
      newStatus: "OPEN",
      transitionReason: "Alert created",
      timestamp: alert.timestamp,
    },
  ];
  if (alert.status && alert.status !== "OPEN") {
    entries.push({
      id: "syn-1",
      previousStatus: "OPEN",
      newStatus: alert.status,
      transitionReason: null,
      timestamp: null,               // unknown — won't display a time
    });
  }
  return entries;
}

// ── Sub-components ────────────────────────────────────────────────────────────

function DetailRow({ icon: Icon, label, children }) {
  return (
    <div className="flex items-start gap-3">
      <Icon className="w-4 h-4 text-slate-500 flex-shrink-0 mt-0.5" />
      <div className="min-w-0">
        <p className="text-xs text-slate-500 mb-0.5">{label}</p>
        <div className="text-sm text-slate-200">{children}</div>
      </div>
    </div>
  );
}

function TransitionTimeline({ history }) {
  if (!history?.length) {
    return (
      <p className="text-slate-600 text-sm text-center py-4">
        No transitions recorded.
      </p>
    );
  }

  return (
    <ol className="relative border-l border-slate-700 ml-2 space-y-4">
      {history.map((entry, idx) => {
        // Backend fields: previousStatus (nullable), newStatus, transitionReason, timestamp
        const fromLabel = entry.previousStatus ?? "CREATED";
        const toLabel   = entry.newStatus      ?? "—";
        const fromCls   = entry.previousStatus
          ? (STATUS_BADGE[entry.previousStatus] ?? "badge")
          : "inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-cyan-950 text-cyan-300 border border-cyan-800";
        const toCls     = STATUS_BADGE[entry.newStatus] ?? "badge";
        const reason    = entry.transitionReason ?? entry.reason ?? null;

        return (
          <li key={entry.id ?? entry.historyId ?? idx} className="ml-5">
            {/* Dot */}
            <span className="absolute -left-[7px] w-3.5 h-3.5 rounded-full bg-slate-700 ring-2 ring-slate-900 flex items-center justify-center">
              <span className="w-1.5 h-1.5 rounded-full bg-blue-400" />
            </span>

            <div className="flex flex-wrap items-center gap-2 mb-0.5">
              <span className={fromCls}>{fromLabel}</span>
              <ChevronRight className="w-3 h-3 text-slate-600 flex-shrink-0" />
              <span className={clsx(toCls)}>{toLabel}</span>
            </div>

            {reason && (
              <p className="text-xs text-slate-400 mb-0.5">
                <span className="text-slate-600">Reason: </span>
                {reason}
              </p>
            )}
            {entry.timestamp && (
              <time className="text-xs text-slate-600">{formatTs(entry.timestamp)}</time>
            )}
          </li>
        );
      })}
    </ol>
  );
}

// ── Main Modal ────────────────────────────────────────────────────────────────

/**
 * AlertDrillDownModal
 *
 * Displays full alert details, state-transition history, and raw metadata.
 * Provides a "Resolve Manually" button that calls PUT /api/alerts/{id}/resolve.
 *
 * @param {{ alert: object, onClose: () => void, onResolved: () => void }} props
 */
export default function AlertDrillDownModal({ alert, onClose, onResolved }) {
  const [history, setHistory]           = useState([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState(null);
  const [resolving, setResolving]       = useState(false);
  const [resolveError, setResolveError] = useState(null);
  const backdropRef = useRef(null);

  // Close on Escape key
  useEffect(() => {
    function onKey(e) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  // Fetch transition history — the alert object itself is passed as a prop
  useEffect(() => {
    if (!alert?.id) return;
    let cancelled = false;
    setHistoryLoading(true);
    setHistoryError(null);
    setResolveError(null);

    fetchAlertHistory(alert.id)
      .then((res) => {
        if (!cancelled) {
          const rows = Array.isArray(res.data) ? res.data : [];
          // If the API returned rows use them; otherwise fall back to synthetic
          setHistory(rows.length > 0 ? rows : buildSyntheticHistory(alert));
        }
      })
      .catch((err) => {
        if (!cancelled) {
          // Endpoint unavailable — degrade silently with a synthetic timeline
          console.warn("History endpoint unavailable, using synthetic fallback:", err);
          setHistory(buildSyntheticHistory(alert));
        }
      })
      .finally(() => {
        if (!cancelled) setHistoryLoading(false);
      });

    return () => { cancelled = true; };
  }, [alert?.id]);

  async function handleResolve() {
    setResolving(true);
    setResolveError(null);
    try {
      await resolveAlert(alert.id);
      // Close the modal and signal the dashboard to refresh immediately.
      onResolved?.();
      onClose();
    } catch (err) {
      setResolveError(
        err.response?.data?.message ?? "Failed to resolve alert. Try again."
      );
      setResolving(false);
    }
  }

  // Close when clicking outside the modal panel
  function handleBackdropClick(e) {
    if (e.target === backdropRef.current) onClose();
  }

  const isAlreadyResolved =
    alert.status === "RESOLVED" || alert.status === "AUTO_CLOSED";

  return (
    <div
      ref={backdropRef}
      onClick={handleBackdropClick}
      className="fixed inset-0 z-50 flex items-center justify-center p-4
                 bg-slate-950/80 backdrop-blur-sm"
    >
      <div
        className="relative w-full max-w-xl max-h-[90vh] flex flex-col
                   bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl"
        role="dialog"
        aria-modal="true"
        aria-label="Alert Details"
      >
        {/* ── Header ──────────────────────────────────────────────────────── */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <ClipboardList className="w-5 h-5 text-blue-400" />
            <h2 className="font-semibold text-white text-base">Alert Details</h2>
          </div>
          <button
            onClick={onClose}
            className="btn-ghost px-2 py-1.5 text-slate-500 hover:text-slate-100"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* ── Body ────────────────────────────────────────────────────────── */}
        <div className="flex-1 overflow-y-auto px-5 py-5 space-y-6">
            {/* Alert data — alert object is from parent state, no fetch needed */}
          {alert && (
            <>
              {/* Section: core fields */}
              <section className="space-y-3">
                <h3 className="text-xs uppercase tracking-widest text-slate-600 font-semibold">
                  Alert Info
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <DetailRow icon={Tag} label="UUID">
                    <code className="text-xs font-mono text-slate-300 break-all">
                      {alert.id}
                    </code>
                  </DetailRow>
                  <DetailRow icon={User} label="Driver ID">
                    <code className="text-xs font-mono text-slate-200">
                      {alert.driverId}
                    </code>
                  </DetailRow>
                  <DetailRow icon={Tag} label="Source Type">
                    {alert.sourceType}
                  </DetailRow>
                  <DetailRow icon={Clock} label="Ingested At">
                    <time className="text-xs">{formatTs(alert.timestamp)}</time>
                  </DetailRow>
                </div>

                {/* Badges */}
                <div className="flex items-center gap-2 flex-wrap pt-1">
                  <span className={clsx(STATUS_BADGE[alert.status] ?? "badge")}>
                    {alert.status}
                  </span>
                  <span
                    className={clsx(SEVERITY_BADGE[alert.severity] ?? "badge")}
                  >
                    {alert.severity}
                  </span>
                </div>
              </section>

              {/* Section: metadata */}
              {alert.metadata && Object.keys(alert.metadata).length > 0 && (
                <section>
                  <h3 className="text-xs uppercase tracking-widest text-slate-600 font-semibold mb-3">
                    Metadata
                  </h3>
                  <pre className="bg-slate-800/70 border border-slate-700/60 rounded-xl p-4
                                  text-xs text-slate-300 font-mono overflow-x-auto whitespace-pre-wrap
                                  leading-relaxed">
                    {JSON.stringify(alert.metadata, null, 2)}
                  </pre>
                </section>
              )}

              {/* Section: transition history */}
              <section>
                <h3 className="text-xs uppercase tracking-widest text-slate-600 font-semibold mb-4">
                  State Transitions
                </h3>
                {historyLoading ? (
                  <div className="flex items-center gap-2 text-slate-500 text-sm py-2">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Loading history…
                  </div>
                ) : historyError ? (
                  // historyError is kept in state but we never reach here now
                  // (errors fall back to synthetic timeline instead)
                  <TransitionTimeline history={buildSyntheticHistory(alert)} />
                ) : (
                  <TransitionTimeline history={history} />
                )}
              </section>
            </>
          )}
        </div>

        {/* ── Footer / Resolve button ──────────────────────────────────────── */}
        {alert && (
          <div className="px-5 py-4 border-t border-slate-800 flex flex-col gap-2">
            {resolveError && (
              <p className="text-xs text-red-400 flex items-center gap-1">
                <AlertCircle className="w-3.5 h-3.5" />
                {resolveError}
              </p>
            )}

            <div className="flex items-center justify-between gap-3">
              <button onClick={onClose} className="btn-ghost">
                Close
              </button>

              {isAlreadyResolved ? (
                /* ── Grey disabled: nothing actionable ─────────────────── */
                <button
                  disabled
                  className="inline-flex items-center gap-2 rounded-lg px-4 py-2
                             text-sm font-medium bg-slate-700/60 text-slate-500
                             border border-slate-700 cursor-not-allowed"
                  title={`Alert is already ${alert.status}`}
                >
                  <CheckCircle2 className="w-4 h-4" />
                  Already {alert.status.replace("_", "\u00a0")}
                </button>
              ) : (
                /* ── Blue: OPEN or ESCALATED alerts can be resolved ────── */
                <button
                  onClick={handleResolve}
                  disabled={resolving}
                  className="inline-flex items-center gap-2 rounded-lg px-4 py-2
                             text-sm font-semibold bg-blue-600 hover:bg-blue-500
                             active:bg-blue-700 text-white transition-colors
                             disabled:opacity-60 disabled:cursor-not-allowed"
                  title="Mark this alert as RESOLVED"
                >
                  {resolving ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Resolving…
                    </>
                  ) : (
                    <>
                      <CheckCircle2 className="w-4 h-4" />
                      Resolve Manually
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
