import { FormEvent, useMemo, useState } from "react";
import { api } from "../api";

type Msg = { role: "user" | "assistant"; content: string };

const SUGGESTIONS = [
  "How many Instagram signups went cold last month?",
  "Build me a list of our most engaged readers",
  "Which pages do new subscribers hit first?",
];

function newConversationId() {
  return crypto.randomUUID();
}

export default function AssistantPage() {
  // Fresh id per tab/mount so tabs don't share server-side memory.
  const [conversationId, setConversationId] = useState(newConversationId);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<Msg[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const shortId = useMemo(() => conversationId.slice(0, 8), [conversationId]);

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
    setConversationId(newConversationId());
    setMessages([]);
    setError("");
    setInput("");
  }

  return (
    <>
      <section className="panel">
        <div className="row" style={{ alignItems: "center" }}>
          <div>
            <h2 style={{ marginTop: 0 }}>Growth assistant</h2>
            <p className="sub">
              Plain-English questions with a sliding memory window for this tab only (conversation{" "}
              <span className="mono">{shortId}</span>). Other users/tabs stay isolated.
            </p>
          </div>
          <button type="button" className="secondary" onClick={startNewChat}>
            New chat
          </button>
        </div>
        <div className="suggestions">
          {SUGGESTIONS.map((s) => (
            <button key={s} type="button" className="secondary" onClick={() => ask(s)}>
              {s}
            </button>
          ))}
        </div>
      </section>

      <section className="panel chat">
        {messages.length === 0 ? (
          <p className="sub">Ask something Growth would ask over coffee.</p>
        ) : null}
        {messages.map((m, i) => (
          <div key={i} className={`bubble ${m.role}`}>
            {m.content}
          </div>
        ))}
        {loading ? <p className="sub">Thinking with tools…</p> : null}
        {error ? <p className="err">{error}</p> : null}
        <form className="row" onSubmit={onSubmit}>
          <div style={{ flex: 3 }}>
            <label htmlFor="q">Question</label>
            <input
              id="q"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="follow up: what about Google signups?"
            />
          </div>
          <button type="submit" disabled={loading}>
            Ask
          </button>
        </form>
      </section>
    </>
  );
}
