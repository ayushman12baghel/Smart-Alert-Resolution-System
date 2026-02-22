import { useEffect, useState } from "react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { TrendingUp, Loader2, AlertCircle } from "lucide-react";
import api from "../api/api.js";

// ── Color palette for each series ──────────────────────────────────────────
const SERIES = [
  { key: "total",      name: "Total",       color: "#60a5fa" }, // blue-400
  { key: "open",       name: "Open",        color: "#34d399" }, // emerald-400
  { key: "escalated",  name: "Escalated",   color: "#fb923c" }, // orange-400
  { key: "autoClosed", name: "Auto-Closed", color: "#94a3b8" }, // slate-400
  { key: "resolved",   name: "Resolved",    color: "#4ade80" }, // green-400
];

// Custom tooltip styled to match the dark theme
function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 shadow-2xl text-sm">
      <p className="font-semibold text-slate-300 mb-2">{label}</p>
      {payload.map((entry) => (
        <div key={entry.dataKey} className="flex items-center gap-2 mb-0.5">
          <span
            className="inline-block w-2.5 h-2.5 rounded-full flex-shrink-0"
            style={{ backgroundColor: entry.color }}
          />
          <span className="text-slate-400">{entry.name}:</span>
          <span className="font-semibold text-white tabular-nums">{entry.value}</span>
        </div>
      ))}
    </div>
  );
}

/**
 * TrendsChart — fetches GET /api/dashboard/trends?tz=Asia/Kolkata
 * and renders a Recharts LineChart showing daily alert evolution.
 *
 * @param {{ refreshKey: number }} props
 */
export default function TrendsChart({ refreshKey }) {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    api.get('/api/dashboard/trends?tz=Asia/Kolkata')
      .then((res) => {
        if (!cancelled) {
          // Backend returns YYYY-MM-DD; slice to MM-DD for compact X-axis labels
          const formatted = res.data.map((entry) => ({
            ...entry,
            day: entry.day.slice(5), // "02-22"
          }));
          setData(formatted);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setError("Failed to load trend data.");
          console.log(error);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  return (
    <div className="card">
      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <div className="flex items-center gap-2">
          <TrendingUp className="w-5 h-5 text-blue-400" />
          <h2 className="font-semibold text-white text-base">
            Alert Trends
          </h2>
          <span className="text-xs text-slate-500">(Asia/Kolkata · daily)</span>
        </div>
        {loading && <Loader2 className="w-4 h-4 text-slate-500 animate-spin" />}
      </div>

      {/* Error state */}
      {error && (
        <div className="flex items-center gap-2 text-red-400 text-sm py-8 justify-center">
          <AlertCircle className="w-4 h-4" />
          {error}
        </div>
      )}

      {/* Chart */}
      {!error && (
        <div className="h-72">
          {data.length === 0 && !loading ? (
            <div className="flex items-center justify-center h-full text-slate-600 text-sm">
              No trend data available yet.
            </div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart
                data={data}
                margin={{ top: 5, right: 10, left: -10, bottom: 5 }}
              >
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke="#1e293b"
                  vertical={false}
                />
                <XAxis
                  dataKey="day"
                  tick={{ fill: "#64748b", fontSize: 11 }}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis
                  tick={{ fill: "#64748b", fontSize: 11 }}
                  axisLine={false}
                  tickLine={false}
                  allowDecimals={false}
                />
                <Tooltip content={<CustomTooltip />} />
                <Legend
                  wrapperStyle={{ paddingTop: "16px", fontSize: "12px" }}
                  formatter={(value) => (
                    <span className="text-slate-400">{value}</span>
                  )}
                />
                {SERIES.map(({ key, name, color }) => (
                  <Line
                    key={key}
                    type="monotone"
                    dataKey={key}
                    name={name}
                    stroke={color}
                    strokeWidth={2}
                    dot={false}
                    activeDot={{ r: 4, strokeWidth: 0 }}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>
      )}
    </div>
  );
}
