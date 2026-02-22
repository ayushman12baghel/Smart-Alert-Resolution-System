import { useCallback, useEffect, useRef, useState } from "react";
import {
  Bell,
  LogOut,
  RefreshCw,
  ShieldAlert,
} from "lucide-react";
import { useAuth } from "../context/AuthContext.jsx";
import { fetchStats } from "../api/api.js";
import StatsOverview from "../components/StatsOverview.jsx";
import TrendsChart from "../components/TrendsChart.jsx";
import TopOffenders from "../components/TopOffenders.jsx";
import AutoClosedAlerts from "../components/AutoClosedAlerts.jsx";
import AlertsFeed from "../components/AlertsFeed.jsx";
import AlertDrillDownModal from "../components/AlertDrillDownModal.jsx";

const REFRESH_INTERVAL_MS = 30_000; // Auto-refresh every 30 s

export default function Dashboard() {
  const { user, logout } = useAuth();

  // ── Stats ──────────────────────────────────────────────────────────────
  const [stats, setStats] = useState(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [statsError, setStatsError] = useState(null);

  // ── Refresh key — incrementing triggers child refetches ────────────────
  const [refreshKey, setRefreshKey] = useState(0);

  // ── Modal — holds the full alert object so the modal needs no extra fetch ──
  const [selectedAlert, setSelectedAlert] = useState(null);

  // ── Last-refreshed display ────────────────────────────────────────────
  const [lastRefreshed, setLastRefreshed] = useState(null);
  const intervalRef = useRef(null);

  // ── Load stats ────────────────────────────────────────────────────────
  const loadStats = useCallback(async () => {
    setStatsLoading(true);
    setStatsError(null);
    try {
      const res = await fetchStats();
      setStats(res.data);
      setLastRefreshed(new Date());
    } catch (err) {
      setStatsError("Failed to load stats.");
      console.error(err);
    } finally {
      setStatsLoading(false);
    }
  }, []);

  // Initial load + interval refresh
  useEffect(() => {
    loadStats();
    intervalRef.current = setInterval(loadStats, REFRESH_INTERVAL_MS);
    return () => clearInterval(intervalRef.current);
  }, [loadStats]);

  // ── Manual refresh ────────────────────────────────────────────────────
  function handleManualRefresh() {
    setRefreshKey((k) => k + 1);
    loadStats();
  }

  // ── After resolving an alert, bump refresh to pull new data ───────────
  function handleAlertResolved() {
    setSelectedAlert(null);
    setRefreshKey((k) => k + 1);
    loadStats();
  }

  const displayName = user?.sub ?? "Admin";
  const initials = displayName.slice(0, 2).toUpperCase();

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col">
      {/* ─── Header ──────────────────────────────────────────────────────── */}
      <header className="sticky top-0 z-20 flex items-center justify-between px-6 py-3
                         bg-slate-900/80 backdrop-blur-md border-b border-slate-800 shadow-lg">
        {/* Brand */}
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-9 h-9 rounded-xl bg-blue-600/20 ring-1 ring-blue-500/30">
            <ShieldAlert className="w-5 h-5 text-blue-400" />
          </div>
          <div>
            <span className="text-white font-bold text-lg leading-tight tracking-tight">
              MoveInSync Alerts
            </span>
            <span className="hidden sm:block text-xs text-slate-500 -mt-0.5">
              Intelligent Escalation &amp; Resolution System
            </span>
          </div>
        </div>

        {/* Right controls */}
        <div className="flex items-center gap-3">
          {/* Last refreshed */}
          {lastRefreshed && (
            <span className="hidden md:block text-xs text-slate-600">
              Updated {lastRefreshed.toLocaleTimeString()}
            </span>
          )}

          {/* Refresh button */}
          <button
            onClick={handleManualRefresh}
            className="btn-ghost px-2.5 py-2"
            title="Refresh dashboard"
          >
            <RefreshCw
              className={`w-4 h-4 ${statsLoading ? "animate-spin" : ""}`}
            />
          </button>

          {/* Notification bell placeholder */}
          <button className="btn-ghost px-2.5 py-2 relative" title="Alerts">
            <Bell className="w-4 h-4" />
            {stats?.openCount > 0 && (
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-red-500 ring-2 ring-slate-900" />
            )}
          </button>

          {/* Avatar + logout */}
          <div className="flex items-center gap-2 pl-3 border-l border-slate-800">
            <div className="w-7 h-7 rounded-full bg-blue-600/30 ring-1 ring-blue-500/30
                            flex items-center justify-center text-xs font-bold text-blue-300">
              {initials}
            </div>
            <span className="hidden sm:block text-sm text-slate-400 max-w-[120px] truncate">
              {displayName}
            </span>
            <button
              onClick={logout}
              className="btn-ghost px-2 py-1.5 text-red-400 hover:text-red-300 hover:bg-red-500/10"
              title="Sign out"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </header>

      {/* ─── Main Content ────────────────────────────────────────────────── */}
      <main className="flex-1 px-4 sm:px-6 lg:px-8 py-6 max-w-[1600px] mx-auto w-full space-y-6">

        {/* Stats error */}
        {statsError && (
          <div className="px-4 py-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
            {statsError}
          </div>
        )}

        {/* ── Row 1: Stat Cards ─────────────────────────────────────────── */}
        <StatsOverview stats={stats} />

        {/* ── Row 2: Trends Chart (full width) ─────────────────────────── */}
        <TrendsChart refreshKey={refreshKey} />

        {/* ── Row 3: Leaderboard + Auto-Closed side-by-side ────────────── */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <TopOffenders
            refreshKey={refreshKey}
          />
          <AlertsFeed
            refreshKey={refreshKey}
            onAlertClick={setSelectedAlert}
          />
        </div>
      </main>

      {/* ─── Footer ──────────────────────────────────────────────────────── */}
      <footer className="text-center py-3 text-xs text-slate-700 border-t border-slate-900">
        MoveInSync © {new Date().getFullYear()} · Alert Escalation System
      </footer>

      {/* ─── Drill-Down Modal ─────────────────────────────────────────────── */}
      {selectedAlert && (
        <AlertDrillDownModal
          alert={selectedAlert}
          onClose={() => setSelectedAlert(null)}
          onResolved={handleAlertResolved}
        />
      )}
    </div>
  );
}
