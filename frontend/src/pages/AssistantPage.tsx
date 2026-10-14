import { FormEvent, useEffect, useRef, useState } from "react";
import { api } from "../api";
import ChatMessage from "../components/ChatMessage";

type Msg = { role: "user" | "assistant"; content: string };

type StoredChat = {
  conversationId: string;
  messages: Msg[];
};

const SUGGESTIONS = [
  "How many Instagram signups went cold last month?",
  "Build me a list of our most engaged readers",
  "Which pages do new subscribers hit first?",
];

const STORAGE_KEY = "tpo-growth-assistant-chat";

function newConversationId() {
  return crypto.randomUUID();
}

function loadStoredChat(): StoredChat | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredChat;
    if (
      typeof parsed?.conversationId !== "string" ||
      !parsed.conversationId ||
      !Array.isArray(parsed.messages)
    ) {
      return null;
    }
    const messages = parsed.messages.filter(
      (m): m is Msg =>
        (m?.role === "user" || m?.role === "assistant") && typeof m?.content === "string",
    );
    return { conversationId: parsed.conversationId, messages };
  } catch {
    return null;
  }
}

function saveStoredChat(conversationId: string, messages: Msg[]) {
  try {
    const payload: StoredChat = { conversationId, messages };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    /* ignore quota / private mode */
  }
}

function clearStoredChat() {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore */
  }
}

export default function AssistantPage() {
  const [conversationId, setConversationId] = useState(() => {
    return loadStoredChat()?.conversationId ?? newConversationId();
  });
  const [messages, setMessages] = useState<Msg[]>(() => loadStoredChat()?.messages ?? []);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [showSuggestions, setShowSuggestions] = useState(true);
  const messagesRef = useRef<HTMLDivElement>(null);

  const hasMessages = messages.length > 0;
  const placeholder = hasMessages
    ? "Ask a follow-up…"
    : "Type a question, or pick a suggestion above…";

  useEffect(() => {
    saveStoredChat(conversationId, messages);
  }, [conversationId, messages]);

  useEffect(() => {
    const el = messagesRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
  }, [messages, loading]);

  async function ask(question: string) {
    setLoading(true);
    setError("");
    setMessages((m) => [...m, { role: "user", content: question }]);
    try {
      const data = await api.chat(question, conversationId, false);
      if (data.conversationId && data.conversationId !== conversationId) {
        setConversationId(data.conversationId);
      }
      setMessages((m) => [...m, { role: "assistant", content: data.answer }]);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Assistant unavailable");
    } finally {
      setLoading(false);
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const q = input.trim();
    if (!q) return;
    setInput("");
    await ask(q);
  }

  async function startNewChat() {
    try {
      await api.clearChat(conversationId);
    } catch {
      /* ignore — still rotate local id */
    }
    clearStoredChat();
    setConversationId(newConversationId());
    setMessages([]);
    setError("");
    setInput("");
    setShowSuggestions(true);
  }

  return (
    <>
      <section className="panel">
        <div className="row assistant-header">
          <div>
            <h2 style={{ marginTop: 0 }}>Growth assistant</h2>
            <p className="sub">Ask plain-English questions about subscribers, visits, and engagement.</p>
          </div>
          <button type="button" className="btn-new-chat" onClick={startNewChat}>
            New chat
          </button>
        </div>
      </section>

      <section className="panel chat">
        <div className="chat-messages" ref={messagesRef}>
          {messages.length === 0 ? (
            <p className="sub">Ask something Growth would ask over coffee.</p>
          ) : null}
          {messages.map((m, i) => (
            <ChatMessage key={i} role={m.role} content={m.content} />
          ))}
          {loading ? <p className="sub">Thinking with tools…</p> : null}
          {error ? <p className="err">{error}</p> : null}
        </div>

        <div className="chat-composer">
          <div className="suggestions-bar">
            <button
              type="button"
              className="suggestions-toggle"
              aria-expanded={showSuggestions}
              onClick={() => setShowSuggestions((v) => !v)}
            >
              {showSuggestions ? "Hide suggestions" : "Show suggestions"}
            </button>
            {showSuggestions ? (
              <div className="suggestions">
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    className="suggestion-chip"
                    disabled={loading}
                    onClick={() => ask(s)}
                  >
                    {s}
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <form className="row chat-form" onSubmit={onSubmit}>
            <div className="chat-input-wrap">
              <input
                id="q"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder={placeholder}
                aria-label="Message"
                disabled={loading}
              />
            </div>
            <button type="submit" disabled={loading}>
              Send
            </button>
          </form>
        </div>
      </section>
    </>
  );
}
