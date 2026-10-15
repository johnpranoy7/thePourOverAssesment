import { FormEvent, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, AuthConfig } from "../api";

function GitHubIcon() {
  return (
    <svg
      className="github-btn__icon"
      viewBox="0 0 16 16"
      width="20"
      height="20"
      aria-hidden="true"
      fill="currentColor"
    >
      <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0 0 16 8c0-4.42-3.58-8-8-8z" />
    </svg>
  );
}

function EyeIcon({ open }: { open: boolean }) {
  if (open) {
    return (
      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
        <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
        <line x1="1" y1="1" x2="23" y2="23" />
      </svg>
    );
  }
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

export default function LoginPage({ onLogin }: { onLogin: () => void }) {
  const [searchParams] = useSearchParams();
  const [config, setConfig] = useState<AuthConfig | null>(null);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api
      .authConfig()
      .then(setConfig)
      .catch(() =>
        setConfig({
          githubEnabled: false,
          githubAuthorizationUrl: null,
          demoUsername: null,
          demoPassword: null,
        }),
      );
  }, []);

  useEffect(() => {
    if (searchParams.get("error") === "oauth") {
      setError("GitHub sign-in failed. Try username/password instead.");
    }
  }, [searchParams]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      await api.login(username, password);
      onLogin();
    } catch {
      setError("Wrong username or password");
    } finally {
      setLoading(false);
    }
  }

  function fillDemoCredentials() {
    if (!config?.demoUsername) {
      return;
    }
    setUsername(config.demoUsername);
    setPassword(config.demoPassword ?? "");
    setError("");
  }

  const showGithub = !!(config?.githubEnabled && config.githubAuthorizationUrl);
  const showDemoFill = !!(config?.demoUsername && config.demoPassword != null);

  return (
    <div className="login-wrap">
      <div className="login-layout">
        {showDemoFill ? (
          <button
            type="button"
            className="login-recruiter"
            onClick={fillDemoCredentials}
          >
            Recruiters — click here
          </button>
        ) : null}

        <div className="panel login-card">
          <header className="login-card__header">
            <h1 className="brand">The Pour Over CDP</h1>
            <p className="sub">Sign in to browse the Growth demo.</p>
          </header>

          {error ? <p className="err login-card__error">{error}</p> : null}

          <form className="login-form" onSubmit={onSubmit}>
            <div className="login-credentials">
              <div className="login-field">
                <label htmlFor="username">Username</label>
                <input
                  id="username"
                  type="text"
                  autoComplete="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Username"
                  autoFocus
                  required
                />
              </div>
              <div className="login-field">
                <label htmlFor="password">Password</label>
                <div className="password-input">
                  <input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Password"
                    required
                  />
                  <button
                    type="button"
                    className="password-toggle"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                    title={showPassword ? "Hide password" : "Show password"}
                  >
                    <EyeIcon open={showPassword} />
                  </button>
                </div>
              </div>
            </div>
            <button type="submit" className="login-submit" disabled={loading}>
              {loading ? "Checking…" : "Sign in"}
            </button>
          </form>

          {showGithub ? (
            <div className="login-alt">
              <p className="login-divider">or</p>
              <a
                className="github-btn"
                href={config!.githubAuthorizationUrl!}
                aria-label="Continue with GitHub"
                title="Continue with GitHub"
              >
                <GitHubIcon />
                <span>Continue with GitHub</span>
              </a>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
