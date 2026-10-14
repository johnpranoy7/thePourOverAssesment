import { useEffect, useMemo, useState } from "react";
import { api, SnapshotResponse } from "../api";
import { formatDate } from "../formatDate";

function titleCaseSource(source: string): string {
  if (!source || source === "unknown") return "Unknown";
  return source
    .split(/[\s_-]+/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(" ");
}

export default function SnapshotPage() {
  const [data, setData] = useState<SnapshotResponse | null>(null);
  const [error, setError] = useState("");

  async function load() {
    try {
      setData(await api.snapshot());
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load");
    }
  }

  useEffect(() => {
    load();
    const id = setInterval(load, 5000);
    return () => clearInterval(id);
  }, []);

  const snap = data?.snapshot;

  const acquisitionRows = useMemo(() => {
    const mix = snap?.sourceMix || {};
    const total = Object.values(mix).reduce((sum, n) => sum + n, 0);
    return Object.entries(mix)
      .sort((a, b) => b[1] - a[1])
      .map(([source, count]) => ({
        source,
        label: titleCaseSource(source),
        count,
        share: total > 0 ? Math.round((count / total) * 100) : 0,
      }));
  }, [snap?.sourceMix]);

  return (
    <>
      <section className="panel">
        <h2 style={{ marginTop: 0 }}>Snapshot</h2>
        <p className="sub">
          Quick pulse of the unified CDP as of <strong>{snap?.today ?? "2026-09-28"}</strong>.
        </p>
        {error ? <p className="err">{error}</p> : null}

        <div className="snapshot-metrics">
          <div className="snapshot-metric">
            <strong>{snap?.newsletterSubscribers ?? "—"}</strong>
            <span className="snapshot-metric__label">Newsletter subscribers</span>
            <span className="snapshot-metric__hint">Deduped profiles from the subscriber list</span>
          </div>
          <div className="snapshot-metric">
            <strong>{snap?.appUsers ?? "—"}</strong>
            <span className="snapshot-metric__label">App accounts</span>
            <span className="snapshot-metric__hint">Rows in app_users linked by email</span>
          </div>
          <div className="snapshot-metric">
            <strong>{snap?.webEventsLinked ?? "—"}</strong>
            <span className="snapshot-metric__label">Linked site visits</span>
            <span className="snapshot-metric__hint">Web events matched to a subscriber</span>
          </div>
          <div className="snapshot-metric">
            <strong>{snap?.coldInstagram30d ?? "—"}</strong>
            <span className="snapshot-metric__label">Instagram cold (30d)</span>
            <span className="snapshot-metric__hint">Instagram signups with no open in 30 days</span>
          </div>
        </div>
      </section>

      <section className="panel">
        <h3 style={{ marginTop: 0 }}>Acquisition mix</h3>
        <p className="sub">Where newsletter subscribers came from, highest count first.</p>
        <table className="acquisition-table">
          <thead>
            <tr>
              <th scope="col">Acquisition source</th>
              <th scope="col">Subscribers</th>
              <th scope="col">Share</th>
            </tr>
          </thead>
          <tbody>
            {acquisitionRows.length === 0 ? (
              <tr>
                <td colSpan={3} className="sub">
                  No acquisition data yet.
                </td>
              </tr>
            ) : (
              acquisitionRows.map((row) => (
                <tr key={row.source}>
                  <td>{row.label}</td>
                  <td>{row.count.toLocaleString()}</td>
                  <td>{row.share}%</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </section>

      <section className="panel">
        <h3 style={{ marginTop: 0 }}>Live app events</h3>
        <p className="sub">Latest webhook activity · refreshes every 5 seconds</p>
        <ul className="ticker">
          {(data?.recentEvents || []).length === 0 ? <li>No app events yet.</li> : null}
          {(data?.recentEvents || []).map((e) => (
            <li key={e.eventId}>
              <span>{formatDate(e.timestamp)}</span> · <strong>{e.event}</strong> · device{" "}
              {e.deviceId}
              {e.userId ? ` · user ${e.userId}` : " · anonymous"} · ref{" "}
              <span className="mono">{e.subscriberRef}</span>
            </li>
          ))}
        </ul>
      </section>
    </>
  );
}
