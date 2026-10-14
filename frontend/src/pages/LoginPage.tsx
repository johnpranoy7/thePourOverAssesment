import { FormEvent, useState } from "react";
import { api } from "../api";

export default function LoginPage({ onLogin }: { onLogin: () => void }) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      await api.login(password);
      onLogin();
    } catch {
      setError("Wrong password");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-wrap">
      <div className="panel login-card">
        <h1 className="brand">The Pour Over CDP</h1>
        <p className="sub">UI password required. Webhooks use a separate bearer secret.</p>
        <form onSubmit={onSubmit} style={{ marginTop: "1.25rem" }}>
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoFocus
          />
          {error ? <p className="err">{error}</p> : null}
          <button type="submit" disabled={loading} style={{ marginTop: "0.85rem", width: "100%" }}>
            {loading ? "Checking…" : "Enter"}
          </button>
        </form>
      </div>
    </div>
  );
}
