import { Navigate, Route, Routes } from "react-router-dom";
import { useEffect, useState } from "react";
import { api } from "./api";
import Layout from "./components/Layout";
import LoginPage from "./pages/LoginPage";
import SnapshotPage from "./pages/SnapshotPage";
import LookupPage from "./pages/LookupPage";
import SegmentsPage from "./pages/SegmentsPage";
import AssistantPage from "./pages/AssistantPage";

export default function App() {
  const [auth, setAuth] = useState<boolean | null>(null);

  useEffect(() => {
    api
      .me()
      .then((r) => setAuth(r.authenticated))
      .catch(() => setAuth(false));
  }, []);

  if (auth === null) {
    return (
      <div className="login-wrap">
        <p className="sub">Loading…</p>
      </div>
    );
  }

  return (
    <Routes>
      <Route
        path="/login"
        element={
          auth ? <Navigate to="/" replace /> : <LoginPage onLogin={() => setAuth(true)} />
        }
      />
      <Route
        element={auth ? <Layout onLogout={() => setAuth(false)} /> : <Navigate to="/login" replace />}
      >
        <Route path="/" element={<SnapshotPage />} />
        <Route path="/lookup" element={<LookupPage />} />
        <Route path="/analytics" element={<SegmentsPage />} />
        <Route path="/segments" element={<Navigate to="/analytics" replace />} />
        <Route path="/assistant" element={<AssistantPage />} />
      </Route>
      <Route path="*" element={<Navigate to={auth ? "/" : "/login"} replace />} />
    </Routes>
  );
}
