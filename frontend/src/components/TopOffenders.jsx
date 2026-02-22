import { useEffect, useState } from "react";
import { Trophy, Loader2, AlertCircle } from "lucide-react";
import { fetchLeaderboard } from "../api/api.js";
import clsx from "clsx";

const MEDAL_COLORS = [
  "text-yellow-400",  // 1st
  "text-slate-300",   // 2nd
  "text-amber-600",   // 3rd
];

const SEVERITY_BADGE = {
  CRITICAL: "inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-red-500/20 text-red-400",
  WARNING:  "inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-yellow-500/20 text-yellow-400",
  INFO:     "inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-500/20 text-blue-400",
};

/** Derive a representative risk level from alert count when no severity field is present. */
function deriveRiskLevel(count) {
  if (count >= 3) return "CRITICAL";
  if (count >= 2) return "WARNING";
  return "INFO";
}

/**
 * TopOffenders — GET /api/dashboard/leaderboard
 * Shows top-5 drivers ranked by open/escalated alert count.
 *
 * @param {{ refreshKey: number, onAlertClick: (id: string) => void }} props
 */
export default function TopOffenders({ refreshKey, onAlertClick }) {
  const [drivers, setDrivers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]   = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    fetchLeaderboard()
      .then((res) => {
        if (!cancelled) setDrivers(res.data ?? []);
      })
      .catch((err) => {
        if (!cancelled) {
          setError("Failed to load leaderboard.");
          console.error(err);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [refreshKey]);

  return (
    <div className="card flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Trophy className="w-5 h-5 text-yellow-400" />
          <h2 className="font-semibold text-white text-base">Top Offenders</h2>
          <span className="text-xs text-slate-500">(open + escalated)</span>
        </div>
        {loading && <Loader2 className="w-4 h-4 text-slate-500 animate-spin" />}
      </div>

      {/* Error */}
      {error && (
        <div className="flex items-center gap-2 text-red-400 text-sm py-6 justify-center">
          <AlertCircle className="w-4 h-4" />
          {error}
        </div>
      )}

      {/* Table */}
      {!error && (
        <div className="overflow-x-auto -mx-1">
          {drivers.length === 0 && !loading ? (
            <p className="text-center text-slate-600 text-sm py-8">
              No active violations — all clear 🎉
            </p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left">
                  <th className="pb-3 pr-2 text-slate-500 font-medium w-8">#</th>
                  <th className="pb-3 pr-4 text-slate-500 font-medium">Driver ID</th>
                  <th className="pb-3 pr-4 text-slate-500 font-medium text-right">
                    Alerts
                  </th>
                  <th className="pb-3 text-slate-500 font-medium">Risk</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {drivers.map((d, idx) => {
                  const rawRisk = (d.severity ?? d.risk ?? "").toUpperCase();
                  const risk = SEVERITY_BADGE[rawRisk] ? rawRisk : deriveRiskLevel(d.alertCount);
                  return (
                    <tr
                      key={d.driverId}
                      className="group hover:bg-slate-800/50 transition-colors cursor-pointer"
                      onClick={() => onAlertClick?.(d.driverId)}
                      title={`View alerts for ${d.driverId}`}
                    >
                      <td className="py-3 pr-2">
                        <span
                          className={clsx(
                            "font-bold tabular-nums",
                            MEDAL_COLORS[idx] ?? "text-slate-500"
                          )}
                        >
                          {idx + 1}
                        </span>
                      </td>
                      <td className="py-3 pr-4">
                        <code className="text-slate-200 font-mono text-xs bg-slate-800 px-2 py-0.5 rounded group-hover:bg-slate-700 transition-colors">
                          {d.driverId}
                        </code>
                      </td>
                      <td className="py-3 pr-4 text-right">
                        <span className="font-bold text-white tabular-nums">
                          {d.alertCount}
                        </span>
                      </td>
                      <td className="py-3">
                        <span className={clsx(SEVERITY_BADGE[risk])}>
                          {risk}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
