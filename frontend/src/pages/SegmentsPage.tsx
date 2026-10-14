import { useEffect, useState } from "react";
import { api, SegmentMeta, SegmentResult } from "../api";
import { formatDate } from "../formatDate";

export default function SegmentsPage() {
  const [list, setList] = useState<SegmentMeta[]>([]);
  const [active, setActive] = useState<SegmentResult | null>(null);
  const [source, setSource] = useState("instagram");
  const [inactiveDays, setInactiveDays] = useState("30");
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .segments()
      .then((d) => setList(d.segments || []))
      .catch(() => setError("Failed to load segments"));
  }, []);

  async function runPreset(id: string) {
    setError("");
    try {
      setActive(await api.runSegment(id));
    } catch {
      setError("Segment failed");
    }
  }

  async function runCustom() {
    setError("");
    try {
      setActive(await api.customSegment(source, inactiveDays));
    } catch {
      setError("Custom segment failed");
    }
  }

  return (
    <>
      <section className="panel">
        <h2 style={{ marginTop: 0 }}>Build a segment</h2>
        <div className="grid">
          {list.map((s) => (
            <button key={s.id} type="button" className="secondary" onClick={() => runPreset(s.id)}>
              <strong style={{ display: "block" }}>{s.name}</strong>
              <span style={{ color: "var(--muted)", fontSize: "0.85rem" }}>{s.count} people</span>
            </button>
          ))}
        </div>
      </section>

      <section className="panel">
        <h3 style={{ marginTop: 0 }}>Custom filter</h3>
        <div className="row">
          <div>
            <label htmlFor="source">Acquisition source</label>
            <input id="source" value={source} onChange={(e) => setSource(e.target.value)} />
          </div>
          <div>
            <label htmlFor="days">Inactive days</label>
            <input
              id="days"
              value={inactiveDays}
              onChange={(e) => setInactiveDays(e.target.value)}
            />
          </div>
          <button type="button" onClick={runCustom}>
            Run filter
          </button>
        </div>
      </section>

      {error ? <p className="err">{error}</p> : null}

      {active ? (
        <section className="panel">
          <div className="row" style={{ alignItems: "center" }}>
            <div>
              <h3 style={{ margin: 0 }}>{active.name}</h3>
              <p className="sub">{active.description}</p>
              <p>
                <strong>{active.count}</strong> matches (showing {active.members.length})
              </p>
            </div>
            {active.id !== "custom" ? (
              <a className="btn" href={`/api/segments/${encodeURIComponent(active.id)}/export`}>
                Export masked CSV
              </a>
            ) : null}
          </div>
          <table>
            <thead>
              <tr>
                <th>Ref</th>
                <th>Masked email</th>
                <th>Source</th>
                <th>Last open</th>
                <th>Score</th>
              </tr>
            </thead>
            <tbody>
              {active.members.map((m) => (
                <tr key={m.subscriberRef}>
                  <td className="mono">{m.subscriberRef}</td>
                  <td>{m.maskedEmail}</td>
                  <td>{m.acquisitionSource}</td>
                  <td>{formatDate(m.lastOpenDate)}</td>
                  <td>{m.engagementScore}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ) : null}
    </>
  );
}
