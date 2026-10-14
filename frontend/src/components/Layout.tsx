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
      <header>
        <h1 className="brand">The Pour Over CDP</h1>
        <p className="sub">Espresso-shot customer data platform for Growth</p>
        <nav className="nav">
          <NavLink to="/" end>
            Snapshot
          </NavLink>
          <NavLink to="/lookup">Lookup</NavLink>
          <NavLink to="/segments">Segments</NavLink>
          <NavLink to="/assistant">Assistant</NavLink>
          <button type="button" className="secondary" onClick={logout}>
            Log out
          </button>
        </nav>
      </header>
      <Outlet />
    </div>
  );
}
