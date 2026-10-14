import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, AnalyticsMeta, AnalyticsResult } from "../api";
import { formatDate } from "../formatDate";

type Member = AnalyticsResult["members"][number];
type SortKey = "subscriberRef" | "maskedEmail" | "acquisitionSource" | "lastOpenDate" | "engagementScore";

const SORT_LABELS: Record<SortKey, string> = {
  subscriberRef: "Ref",
  maskedEmail: "Masked email",
  acquisitionSource: "Source",
  lastOpenDate: "Last open",
  engagementScore: "Score",
};

type ColumnFilters = {
  subscriberRef: string;
  maskedEmail: string;
  acquisitionSource: string;
  lastOpenDate: string;
  engagementScore: string;
};

const EMPTY_FILTERS: ColumnFilters = {
  subscriberRef: "",
  maskedEmail: "",
  acquisitionSource: "",
  lastOpenDate: "",
  engagementScore: "",
};

function compareMembers(a: Member, b: Member, key: SortKey, dir: 1 | -1): number {
  const av = a[key];
  const bv = b[key];
  if (key === "engagementScore") {
    return ((av as number) - (bv as number)) * dir;
  }
  if (key === "lastOpenDate") {
    const at = av ? Date.parse(String(av)) : 0;
    const bt = bv ? Date.parse(String(bv)) : 0;
    return (at - bt) * dir;
  }
  return String(av ?? "").localeCompare(String(bv ?? ""), undefined, { sensitivity: "base" }) * dir;
}

function matchesColumn(value: string | number | null | undefined, filter: string, isDate = false): boolean {
  const f = filter.trim().toLowerCase();
  if (!f) return true;
  if (isDate) {
    const formatted = formatDate(value == null ? null : String(value)).toLowerCase();
    const raw = value == null ? "" : String(value).toLowerCase();
    return formatted.includes(f) || raw.includes(f);
  }
  return String(value ?? "")
    .toLowerCase()
    .includes(f);
}

export default function AnalyticsPage() {
  const [list, setList] = useState<AnalyticsMeta[]>([]);
  const [sources, setSources] = useState<string[]>([]);
  const [active, setActive] = useState<AnalyticsResult | null>(null);
  type TabId = "open-activity" | "most-engaged" | "new-subscribers";

  const [focus, setFocus] = useState<TabId>("open-activity");
  const [source, setSource] = useState("instagram");
  const [activity, setActivity] = useState<"active" | "inactive">("inactive");
  const [days, setDays] = useState("30");
  const [signupDays, setSignupDays] = useState("30");
  const [lastOpenActivity, setLastOpenActivity] = useState<{
    source: string;
    activity: "active" | "inactive";
    days: string;
  } | null>(null);
  const [lastSignupDays, setLastSignupDays] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [loadingId, setLoadingId] = useState<string | null>(null);

  const [columnFilters, setColumnFilters] = useState<ColumnFilters>(EMPTY_FILTERS);
  const [sortKey, setSortKey] = useState<SortKey>("engagementScore");
  const [sortDir, setSortDir] = useState<1 | -1>(-1);

  const loading = loadingId !== null;

  useEffect(() => {
    Promise.all([api.analyticsTabs(), api.snapshot()])
      .then(([tabs, snap]) => {
        setList(tabs.tabs || []);
        const fromData = Object.keys(snap.snapshot?.sourceMix || {})
          .filter((s) => s && s !== "unknown")
          .sort((a, b) => a.localeCompare(b));
        setSources(fromData);
      })
      .catch(() => setError("Failed to load analytics"));
  }, []);

  function resetResultView() {
    setColumnFilters(EMPTY_FILTERS);
    setSortKey("engagementScore");
    setSortDir(-1);
  }

  function applyFocusDefaults(id: TabId) {
    if (id === "open-activity") {
      setSource("instagram");
      setActivity("inactive");
      setDays("30");
    } else if (id === "new-subscribers") {
      setSignupDays("30");
    }
  }

  function normalizeWindow(value: string, fallback: string, setter: (v: string) => void): string {
    const trimmed = value.trim();
    if (trimmed === "" || !/^\d+$/.test(trimmed)) {
      setter(fallback);
      return fallback;
    }
    return trimmed;
  }

  async function selectTab(id: TabId) {
    setFocus(id);
    applyFocusDefaults(id);
    if (id === "open-activity") {
      await runOpenActivity("instagram", "inactive", "30");
    } else if (id === "most-engaged") {
      await runMostEngaged();
    } else {
      await runNewSubscribers("30");
    }
  }

  function onDaysChange(value: string) {
    if (value === "" || /^\d+$/.test(value)) {
      setDays(value);
    }
  }

  function onSignupDaysChange(value: string) {
    if (value === "" || /^\d+$/.test(value)) {
      setSignupDays(value);
    }
  }

  function onActivityChange(next: "active" | "inactive") {
    setActivity(next);
    setDays(next === "active" ? "2" : "10");
  }

  async function runOpenActivity(
    nextSource = source,
    nextActivity = activity,
    nextDays = days,
  ) {
    const windowDays = normalizeWindow(
      nextDays,
      nextActivity === "active" ? "2" : "30",
      setDays,
    );

    setError("");
    setLoadingId("open-activity");
    try {
      setActive(await api.openActivity(nextSource, nextActivity, windowDays));
      setLastOpenActivity({ source: nextSource, activity: nextActivity, days: windowDays });
      setLastSignupDays(null);
      setFocus("open-activity");
      resetResultView();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Open activity query failed");
    } finally {
      setLoadingId(null);
    }
  }

  async function runMostEngaged() {
    setError("");
    setLoadingId("most-engaged");
    try {
      setActive(await api.mostEngaged());
      setLastOpenActivity(null);
      setLastSignupDays(null);
      resetResultView();
    } catch {
      setError("Most engaged query failed");
    } finally {
      setLoadingId(null);
    }
  }

  async function runNewSubscribers(explicitDays?: string) {
    const windowDays = normalizeWindow(explicitDays ?? signupDays, "30", setSignupDays);
    setError("");
    setLoadingId("new-subscribers");
    try {
      setActive(await api.newSubscribers(windowDays));
      setLastOpenActivity(null);
      setLastSignupDays(windowDays);
      setFocus("new-subscribers");
      resetResultView();
    } catch {
      setError("New subscribers query failed");
    } finally {
      setLoadingId(null);
    }
  }

  const usesOpenActivityForm = focus === "open-activity";
  const usesNewSubscribersForm = focus === "new-subscribers";

  function exportHref(): string | null {
    if (!active) return null;
    if (active.id === "open-activity") {
      const q = lastOpenActivity ?? { source, activity, days };
      const params = new URLSearchParams();
      if (q.source) params.set("source", q.source);
      params.set("activity", q.activity);
      params.set("days", q.days);
      return `/api/analytics/open-activity/export?${params}`;
    }
    if (active.id === "new-subscribers") {
      const d = lastSignupDays ?? signupDays;
      return `/api/analytics/new-subscribers/export?days=${encodeURIComponent(d)}`;
    }
    if (active.id === "most-engaged") {
      return "/api/analytics/most-engaged/export";
    }
    return null;
  }

  async function onOpenActivitySubmit(e: FormEvent) {
    e.preventDefault();
    if (loading) return;
    await runOpenActivity();
  }

  async function onNewSubscribersSubmit(e: FormEvent) {
    e.preventDefault();
    if (loading) return;
    await runNewSubscribers();
  }

  const resultSources = useMemo(() => {
    if (!active) return [];
    const set = new Set<string>();
    for (const m of active.members) {
      if (m.acquisitionSource) set.add(m.acquisitionSource);
    }
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [active]);

  const visibleMembers = useMemo(() => {
    if (!active) return [];
    return active.members
      .filter(
        (m) =>
          matchesColumn(m.subscriberRef, columnFilters.subscriberRef) &&
          matchesColumn(m.maskedEmail, columnFilters.maskedEmail) &&
          matchesColumn(m.acquisitionSource, columnFilters.acquisitionSource) &&
          matchesColumn(m.lastOpenDate, columnFilters.lastOpenDate, true) &&
          matchesColumn(m.engagementScore, columnFilters.engagementScore),
      )
      .sort((a, b) => compareMembers(a, b, sortKey, sortDir));
  }, [active, columnFilters, sortKey, sortDir]);

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === 1 ? -1 : 1));
    } else {
      setSortKey(key);
      setSortDir(key === "engagementScore" || key === "lastOpenDate" ? -1 : 1);
    }
  }

  function setFilter(key: keyof ColumnFilters, value: string) {
    setColumnFilters((prev) => ({ ...prev, [key]: value }));
  }

  const hasActiveFilters = Object.values(columnFilters).some((v) => v.trim());

  const tabs: Array<{ id: TabId; fallback: string }> = [
    { id: "open-activity", fallback: "Source & open activity" },
    { id: "most-engaged", fallback: "Most engaged readers" },
    { id: "new-subscribers", fallback: "New subscribers" },
  ];

  return (
    <>
      <section className="panel analytics-tabs-panel">
        <h2 style={{ marginTop: 0 }}>Analytics</h2>

        <div className="seg-tabs" role="tablist" aria-label="Analytics">
          {tabs.map((tab) => {
            const selected = focus === tab.id;
            const meta = list.find((s) => s.id === tab.id);
            return (
              <button
                key={tab.id}
                type="button"
                role="tab"
                id={`tab-${tab.id}`}
                aria-selected={selected}
                aria-controls="analytics-tab-panel"
                className={`seg-tab${selected ? " is-selected" : ""}${
                  loadingId === tab.id ? " is-loading" : ""
                }`}
                disabled={loading}
                onClick={() => selectTab(tab.id)}
              >
                <span className="seg-tab__label">{meta?.name ?? tab.fallback}</span>
                {meta ? <span className="seg-tab__count">{meta.count}</span> : null}
              </button>
            );
          })}
        </div>

        <div
          className="seg-tab-panel"
          role="tabpanel"
          id="analytics-tab-panel"
          aria-labelledby={`tab-${focus}`}
        >
          {usesNewSubscribersForm ? (
            <form className="row" onSubmit={onNewSubscribersSubmit}>
              <div>
                <label htmlFor="signup-days">Signed up within (days)</label>
                <input
                  id="signup-days"
                  inputMode="numeric"
                  pattern="\d*"
                  min={0}
                  step={1}
                  placeholder="30"
                  value={signupDays}
                  disabled={loading}
                  onChange={(e) => onSignupDaysChange(e.target.value)}
                  onBlur={() => {
                    if (signupDays.trim() === "") setSignupDays("30");
                  }}
                  onPaste={(e) => {
                    e.preventDefault();
                    const text = e.clipboardData.getData("text").trim();
                    setSignupDays(/^\d+$/.test(text) ? text : "30");
                  }}
                />
              </div>
              <button type="submit" disabled={loading}>
                {loading ? (
                  <span className="inline-busy">
                    <span className="spinner spinner-sm" aria-hidden="true" />
                    Running…
                  </span>
                ) : (
                  "Run filter"
                )}
              </button>
            </form>
          ) : null}

          {usesOpenActivityForm ? (
            <form className="row" onSubmit={onOpenActivitySubmit}>
              <div>
                <label htmlFor="source">Acquisition source</label>
                <select
                  id="source"
                  value={source}
                  disabled={loading}
                  onChange={(e) => setSource(e.target.value)}
                >
                  <option value="">Any source</option>
                  {sources.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="activity">Look for</label>
                <select
                  id="activity"
                  value={activity}
                  disabled={loading}
                  onChange={(e) => onActivityChange(e.target.value as "active" | "inactive")}
                >
                  <option value="inactive">Inactive (cold)</option>
                  <option value="active">Recently active</option>
                </select>
              </div>
              <div>
                <label htmlFor="days">
                  {activity === "active" ? "Active within (days)" : "Inactive for at least (days)"}
                </label>
                <input
                  id="days"
                  inputMode="numeric"
                  pattern="\d*"
                  min={0}
                  step={1}
                  placeholder={activity === "active" ? "2" : "10"}
                  value={days}
                  disabled={loading}
                  onChange={(e) => onDaysChange(e.target.value)}
                  onBlur={() => {
                    if (days.trim() === "") {
                      setDays(activity === "active" ? "2" : "10");
                    }
                  }}
                  onPaste={(e) => {
                    e.preventDefault();
                    const text = e.clipboardData.getData("text").trim();
                    setDays(/^\d+$/.test(text) ? text : activity === "active" ? "2" : "10");
                  }}
                />
              </div>
              <button type="submit" disabled={loading}>
                {loading ? (
                  <span className="inline-busy">
                    <span className="spinner spinner-sm" aria-hidden="true" />
                    Running…
                  </span>
                ) : (
                  "Run filter"
                )}
              </button>
            </form>
          ) : null}
        </div>
      </section>

      {error ? <p className="err">{error}</p> : null}

      {loading ? (
        <section className="panel loading-panel" aria-live="polite" aria-busy="true">
          <span className="spinner" aria-hidden="true" />
          <p className="sub" style={{ margin: 0 }}>
            Loading…
          </p>
        </section>
      ) : null}

      {!loading && active ? (
        <section className="panel">
          <div className="row" style={{ alignItems: "center" }}>
            <div>
              <h3 style={{ margin: 0 }}>{active.name}</h3>
              <p className="sub">{active.description}</p>
              <p>
                <strong>{active.count}</strong> total matches
                {active.members.length < active.count
                  ? ` · table shows first ${active.members.length}`
                  : null}
                {hasActiveFilters
                  ? ` · ${visibleMembers.length} visible after column filters`
                  : null}
                {hasActiveFilters ? (
                  <>
                    {" · "}
                    <button
                      type="button"
                      className="linkish"
                      onClick={() => setColumnFilters(EMPTY_FILTERS)}
                    >
                      Clear column filters
                    </button>
                  </>
                ) : null}
              </p>
            </div>
            {exportHref() ? (
              <a className="btn" href={exportHref()!}>
                Export masked CSV
              </a>
            ) : null}
          </div>

          <table className="sortable-table">
            <thead>
              <tr>
                {(Object.keys(SORT_LABELS) as SortKey[]).map((key) => (
                  <th key={key}>
                    <button
                      type="button"
                      className={`sort-th${sortKey === key ? " is-active" : ""}`}
                      onClick={() => toggleSort(key)}
                    >
                      {SORT_LABELS[key]}
                      {sortKey === key ? (sortDir === 1 ? " ↑" : " ↓") : ""}
                    </button>
                  </th>
                ))}
              </tr>
              <tr className="column-filters">
                <th>
                  <input
                    aria-label="Filter by ref"
                    placeholder="Filter ref…"
                    value={columnFilters.subscriberRef}
                    onChange={(e) => setFilter("subscriberRef", e.target.value)}
                  />
                </th>
                <th>
                  <input
                    aria-label="Filter by masked email"
                    placeholder="Filter email…"
                    value={columnFilters.maskedEmail}
                    onChange={(e) => setFilter("maskedEmail", e.target.value)}
                  />
                </th>
                <th>
                  <select
                    aria-label="Filter by source"
                    value={columnFilters.acquisitionSource}
                    onChange={(e) => setFilter("acquisitionSource", e.target.value)}
                  >
                    <option value="">All</option>
                    {resultSources.map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                </th>
                <th>
                  <input
                    aria-label="Filter by last open"
                    placeholder="Filter date…"
                    value={columnFilters.lastOpenDate}
                    onChange={(e) => setFilter("lastOpenDate", e.target.value)}
                  />
                </th>
                <th>
                  <input
                    aria-label="Filter by score"
                    placeholder="Filter score…"
                    value={columnFilters.engagementScore}
                    onChange={(e) => setFilter("engagementScore", e.target.value)}
                  />
                </th>
              </tr>
            </thead>
            <tbody>
              {visibleMembers.length === 0 ? (
                <tr>
                  <td colSpan={5} className="sub">
                    No rows match these column filters.
                  </td>
                </tr>
              ) : (
                visibleMembers.map((m) => (
                  <tr key={m.subscriberRef}>
                    <td className="mono">{m.subscriberRef}</td>
                    <td>{m.maskedEmail}</td>
                    <td>{m.acquisitionSource}</td>
                    <td>{formatDate(m.lastOpenDate)}</td>
                    <td>{m.engagementScore}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </section>
      ) : null}
    </>
  );
}
