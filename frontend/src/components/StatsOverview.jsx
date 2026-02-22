import { AlertTriangle, CheckCircle2, Clock, TrendingUp, XCircle } from "lucide-react";
import clsx from "clsx";

const STAT_CONFIG = {
  totalAlerts: {
    label: "Total Alerts",
    icon: TrendingUp,
    color: "text-blue-400",
    bg: "bg-blue-500/10",
    ring: "ring-blue-500/20",
  },
  openCount: {
    label: "Open",
    icon: Clock,
    color: "text-emerald-400",
    bg: "bg-emerald-500/10",
    ring: "ring-emerald-500/20",
  },
  escalatedCount: {
    label: "Escalated",
    icon: AlertTriangle,
    color: "text-orange-400",
    bg: "bg-orange-500/10",
    ring: "ring-orange-500/20",
  },
  autoClosedCount: {
    label: "Auto-Closed",
    icon: XCircle,
    color: "text-slate-400",
    bg: "bg-slate-500/10",
    ring: "ring-slate-500/20",
  },
  resolvedCount: {
    label: "Resolved",
    icon: CheckCircle2,
    color: "text-green-400",
    bg: "bg-green-500/10",
    ring: "ring-green-500/20",
  },
};

function StatCard({ statKey, value }) {
  const cfg = STAT_CONFIG[statKey];
  if (!cfg) return null;
  const Icon = cfg.icon;

  return (
    <div className="card flex items-center gap-4">
      <div
        className={clsx(
          "flex items-center justify-center w-11 h-11 rounded-xl flex-shrink-0 ring-1",
          cfg.bg,
          cfg.ring
        )}
      >
        <Icon className={clsx("w-5 h-5", cfg.color)} />
      </div>
      <div className="min-w-0">
        <p className="text-sm text-slate-500 truncate">{cfg.label}</p>
        <p className="text-2xl font-bold text-white tabular-nums">
          {value ?? "—"}
        </p>
      </div>
    </div>
  );
}

/**
 * Renders the five stat cards in a responsive grid.
 *
 * @param {{ stats: import("../api/api").DashboardStats | null }} props
 */
export default function StatsOverview({ stats }) {
  const keys = [
    "totalAlerts",
    "openCount",
    "escalatedCount",
    "autoClosedCount",
    "resolvedCount",
  ];

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
      {keys.map((k) => (
        <StatCard key={k} statKey={k} value={stats?.[k]} />
      ))}
    </div>
  );
}
