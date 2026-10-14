import { useEffect, useState } from "react";
import { api, SnapshotResponse } from "../api";
import { formatDate } from "../formatDate";

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

  return (
    <>
      <section className="panel">
        <h2 style={{ marginTop: 0 }}>Growth snapshot</h2>
        <p className="sub">Assessment clock: {snap?.today ?? "2026-09-28"}</p>
        {error ? <p className="err">{error}</p> : null}
        <div className="grid" style={{ marginTop: "1rem" }}>
          <div className="stat">
            <strong>{snap?.newsletterSubscribers ?? "—"}</strong>
            <span>Newsletter profiles</span>
          </div>
          <div className="stat">
            <strong>{snap?.coldInstagram30d ?? "—"}</strong>
            <span>Instagram cold 30d</span>
          </div>
          <div className="stat">
            <strong>{snap?.appUsers ?? "—"}</strong>
            <span>App users</span>
          </div>
          <div className="stat">
            <strong>{snap?.webEventsLinked ?? "—"}</strong>
            <span>Linked web events</span>
          </div>
        </div>
      </section>

      <section className="panel">
        <h3 style={{ marginTop: 0 }}>Acquisition mix</h3>
        <table>
          <thead>
            <tr>
              <th>Source</th>
              <th>Subscribers</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(snap?.sourceMix || {})
              .sort((a, b) => b[1] - a[1])
              .map(([source, count]) => (
                <tr key={source}>
                  <td>{source}</td>
                  <td>{count}</td>
                </tr>
              ))}
          </tbody>
        </table>
      </section>

      <section className="panel">
        <h3 style={{ marginTop: 0 }}>Live webhook ticker</h3>
        <p className="sub">Refreshes every 5s — POST /webhooks/app with Bearer secret</p>
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
