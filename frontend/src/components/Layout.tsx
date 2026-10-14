import { NavLink, Outlet } from "react-router-dom";
import { api } from "../api";

export default function Layout({ onLogout }: { onLogout: () => void }) {
  async function logout() {
    try {
      await api.logout();
    } catch {
      /* ignore */
    }
    onLogout();
  }

  return (
    <div className="shell">
      <header className="site-header">
        <div className="site-header__intro">
          <h1 className="brand">The Pour Over CDP</h1>
          <p className="sub">Espresso-shot customer data platform for Growth</p>
        </div>
        <nav className="nav" aria-label="Primary">
          <div className="nav__links">
            <NavLink to="/" end>
              Snapshot
            </NavLink>
            <NavLink to="/lookup">Lookup</NavLink>
            <NavLink to="/analytics">Analytics</NavLink>
            <NavLink to="/assistant">Assistant</NavLink>
          </div>
          <button type="button" className="nav__logout" onClick={logout}>
            Log out
          </button>
        </nav>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  );
}
