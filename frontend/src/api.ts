async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers || {}),
    },
    ...init,
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
  login: (password: string) =>
    request<{ ok: boolean }>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ password }),
    }),
  me: () => request<{ authenticated: boolean }>("/api/auth/me"),
  logout: () => request<{ ok: boolean }>("/api/auth/logout", { method: "POST" }),
  snapshot: () => request<SnapshotResponse>("/api/snapshot"),
  profile: (email: string) =>
    request<ProfileResponse>(`/api/profiles?email=${encodeURIComponent(email)}`),
  segments: () => request<{ segments: SegmentMeta[] }>("/api/segments"),
  runSegment: (id: string) => request<SegmentResult>(`/api/segments/${id}`),
  customSegment: (source: string, inactiveDays: string) => {
    const params = new URLSearchParams();
    if (source) params.set("source", source);
    if (inactiveDays) params.set("inactive_days", inactiveDays);
    return request<SegmentResult>(`/api/segments?${params}`);
  },
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

export type SegmentMeta = {
  id: string;
  name: string;
  description: string;
  count: number;
};

export type SegmentResult = {
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
