function readCookie(name: string): string | null {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = document.cookie.match(new RegExp(`(?:^|; )${escaped}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

function csrfHeaders(method: string): Record<string, string> {
  const upper = method.toUpperCase();
  if (upper === "GET" || upper === "HEAD" || upper === "OPTIONS" || upper === "TRACE") {
    return {};
  }
  const token = readCookie("XSRF-TOKEN");
  return token ? { "X-XSRF-TOKEN": token } : {};
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? "GET";
  const res = await fetch(path, {
    credentials: "include",
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...csrfHeaders(method),
      ...(init?.headers || {}),
    },
  });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      message = body.error || body.message || message;
    } catch {
      /* ignore */
    }
    throw new Error(message || `HTTP ${res.status}`);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export const api = {
  authConfig: () => request<AuthConfig>("/api/auth/config"),
  login: (username: string, password: string) =>
    request<{ ok: boolean }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
  me: () => request<AuthMe>("/api/auth/me"),
  logout: () => request<{ ok: boolean }>("/api/auth/logout", { method: "POST" }),
  snapshot: () => request<SnapshotResponse>("/api/snapshot"),
  profile: (email: string) =>
    request<ProfileResponse>(`/api/profiles?email=${encodeURIComponent(email)}`),
  analyticsTabs: () => request<{ tabs: AnalyticsMeta[] }>("/api/analytics"),
  openActivity: (source: string, activity: "active" | "inactive", days: string) => {
    const params = new URLSearchParams();
    if (source) params.set("source", source);
    params.set("activity", activity);
    params.set("days", days);
    return request<AnalyticsResult>(`/api/analytics/open-activity?${params}`);
  },
  mostEngaged: () => request<AnalyticsResult>("/api/analytics/most-engaged"),
  newSubscribers: (days: string) =>
    request<AnalyticsResult>(`/api/analytics/new-subscribers?days=${encodeURIComponent(days)}`),
  chat: (message: string, conversationId: string, reset = false) =>
    request<{ answer: string; conversationId: string }>("/api/chat", {
      method: "POST",
      body: JSON.stringify({ message, conversationId, reset }),
    }),
  clearChat: (conversationId: string) =>
    request<{ ok: boolean }>(`/api/chat?conversationId=${encodeURIComponent(conversationId)}`, {
      method: "DELETE",
    }),
};

export type AuthConfig = {
  githubEnabled: boolean;
  githubAuthorizationUrl: string | null;
  demoUsername: string | null;
  demoPassword: string | null;
};

export type AuthMe = {
  authenticated: boolean;
  username: string | null;
};

export type SnapshotResponse = {
  snapshot: {
    today: string;
    newsletterSubscribers: number;
    appUsers: number;
    webEventsLinked: number;
    sourceMix: Record<string, number>;
    coldInstagram30d: number;
    recentWebhookEvents: number;
  };
  recentEvents: Array<{
    eventId: string;
    event: string;
    deviceId: string;
    userId: string | null;
    timestamp: string;
    subscriberRef: string;
  }>;
};

export type ProfileResponse = {
  id: number;
  email: string;
  status: string | null;
  signupDate: string | null;
  acquisitionSource: string | null;
  lastOpenDate: string | null;
  stub: boolean;
  userIds: string[];
  deviceIds: string[];
  webEvents: Array<{
    visitorId: string;
    page: string;
    timestamp: string;
    utmSource: string | null;
  }>;
  appEvents: Array<{
    eventId: string;
    event: string;
    timestamp: string;
    deviceId: string;
    userId: string | null;
    properties: Record<string, unknown>;
  }>;
};

export type AnalyticsMeta = {
  id: string;
  name: string;
  description: string;
  count: number;
};

export type AnalyticsResult = {
  id: string;
  name: string;
  description: string;
  count: number;
  members: Array<{
    subscriberRef: string;
    maskedEmail: string;
    status: string | null;
    acquisitionSource: string | null;
    signupDate: string | null;
    lastOpenDate: string | null;
    engagementScore: number;
  }>;
};
