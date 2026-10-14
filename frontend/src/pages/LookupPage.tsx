import { FormEvent, useState } from "react";
import { api, ProfileResponse } from "../api";
import { formatDate } from "../formatDate";

export default function LookupPage() {
  const [email, setEmail] = useState("reader00000@example.com");
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    setProfile(null);
    try {
      setProfile(await api.profile(email));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Lookup failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <section className="panel">
        <h2 style={{ marginTop: 0 }}>Find anyone</h2>
        <form className="row" onSubmit={onSubmit}>
          <div>
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              enterKeyHint="search"
              autoComplete="email"
            />
          </div>
          <button type="submit" disabled={loading}>
            {loading ? "Searching…" : "Lookup"}
          </button>
        </form>
        {error ? <p className="err">{error}</p> : null}
      </section>

      {profile ? (
        <>
          <section className="panel">
            <h3 style={{ marginTop: 0 }}>Newsletter</h3>
            <table>
              <tbody>
                <tr>
                  <th>Email</th>
                  <td>{profile.email}</td>
                </tr>
                <tr>
                  <th>Status</th>
                  <td>{profile.status}</td>
                </tr>
                <tr>
                  <th>Signed up</th>
                  <td>{formatDate(profile.signupDate)}</td>
                </tr>
                <tr>
                  <th>Source</th>
                  <td>{profile.acquisitionSource}</td>
                </tr>
                <tr>
                  <th>Last open</th>
                  <td>{formatDate(profile.lastOpenDate, "(never)")}</td>
                </tr>
                <tr>
                  <th>App user ids</th>
                  <td className="mono">{profile.userIds.join(", ") || "—"}</td>
                </tr>
                <tr>
                  <th>Devices</th>
                  <td className="mono">{profile.deviceIds.join(", ") || "—"}</td>
                </tr>
              </tbody>
            </table>
          </section>

          <section className="panel">
            <h3 style={{ marginTop: 0 }}>Site visits ({profile.webEvents.length})</h3>
            <table>
              <thead>
                <tr>
                  <th>When</th>
                  <th>Page</th>
                  <th>UTM</th>
                </tr>
              </thead>
              <tbody>
                {profile.webEvents.slice(0, 50).map((ev, i) => (
                  <tr key={`${ev.timestamp}-${i}`}>
                    <td>{formatDate(ev.timestamp)}</td>
                    <td>{ev.page}</td>
                    <td>{ev.utmSource || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          <section className="panel">
            <h3 style={{ marginTop: 0 }}>App activity ({profile.appEvents.length})</h3>
            <table>
              <thead>
                <tr>
                  <th>When</th>
                  <th>Event</th>
                  <th>Device</th>
                  <th>Properties</th>
                </tr>
              </thead>
              <tbody>
                {profile.appEvents.map((ev) => (
                  <tr key={ev.eventId}>
                    <td>{formatDate(ev.timestamp)}</td>
                    <td>{ev.event}</td>
                    <td className="mono">{ev.deviceId}</td>
                    <td className="mono">{JSON.stringify(ev.properties)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </>
      ) : null}
    </>
  );
}
