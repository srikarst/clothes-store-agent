import React, { useEffect, useMemo, useRef, useState } from 'react';

const STORAGE_KEY = 'chatConversationId';

function ChatPanel() {
  const [conversationId, setConversationId] = useState(() => localStorage.getItem(STORAGE_KEY) || '');
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState([]);
  const [isSending, setIsSending] = useState(false);
  const [domainHint, setDomainHint] = useState('auto'); // auto | general | analytics_sql
  const [showToolTrace, setShowToolTrace] = useState(false);
  const [lastToolTrace, setLastToolTrace] = useState(null);
  const listRef = useRef(null);

  const hasConversation = useMemo(() => !!conversationId && conversationId.trim().length > 0, [conversationId]);

  useEffect(() => {
    if (hasConversation) {
      localStorage.setItem(STORAGE_KEY, conversationId);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }, [conversationId, hasConversation]);

  useEffect(() => {
    // Scroll to bottom when messages update
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [messages]);

  const send = async () => {
    const text = input.trim();
    if (!text || isSending) return;

    setIsSending(true);
    setInput('');
    setLastToolTrace(null);
    setMessages((prev) => [...prev, { role: 'user', content: text }]);

    try {
      const headers = { 'Content-Type': 'application/json' };
      if (hasConversation) {
        headers['X-Conversation-Id'] = conversationId;
      }

      const res = await fetch('/api/chat', {
        method: 'POST',
        headers,
        body: JSON.stringify({ message: text, domainHint, showToolTrace })
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data?.message || `Chat failed: HTTP ${res.status}`);
      }

      if (data.conversationId && data.conversationId !== conversationId) {
        setConversationId(data.conversationId);
      }

      if (showToolTrace) {
        setLastToolTrace(Array.isArray(data.toolTrace) ? data.toolTrace : []);
      }
      setMessages((prev) => [...prev, { role: 'assistant', content: data.assistantMessage || '' }]);
    } catch (err) {
      console.error(err);
      setMessages((prev) => [...prev, { role: 'assistant', content: `Error: ${err.message}` }]);
    } finally {
      setIsSending(false);
    }
  };

  const newConversation = async () => {
    const oldId = conversationId;
    setConversationId('');
    setMessages([]);
    setInput('');
    setLastToolTrace(null);

    // Optional: clear server-side too
    if (oldId) {
      try {
        await fetch(`/api/chat/conversation/${encodeURIComponent(oldId)}`, { method: 'DELETE' });
      } catch {
        // ignore
      }
    }
  };

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  return (
    <div className="card">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0 }}>Chat</h2>
        <div className="row">
          {hasConversation && <span className="pill mono">cid: {conversationId.slice(0, 8)}…</span>}
          <button className="secondary" onClick={newConversation} disabled={isSending}>
            New conversation
          </button>
        </div>
      </div>

      <div className="row" style={{ marginTop: 8, alignItems: 'center', gap: 10 }}>
        <label className="mono" style={{ fontSize: 12 }}>
          Domain
          <select
            value={domainHint}
            onChange={(e) => setDomainHint(e.target.value)}
            disabled={isSending}
            style={{ marginLeft: 8 }}
          >
            <option value="auto">Auto</option>
            <option value="general">General</option>
            <option value="analytics_sql">Analytics (SQL)</option>
          </select>
        </label>
        <label className="mono" style={{ fontSize: 12 }}>
          <input
            type="checkbox"
            checked={showToolTrace}
            onChange={(e) => setShowToolTrace(e.target.checked)}
            disabled={isSending}
            style={{ marginRight: 6 }}
          />
          Show tool trace
        </label>
      </div>

      <div ref={listRef} className="chatList" role="log" aria-label="Chat messages">
        {messages.length === 0 ? (
          <div className="chatEmpty">
            <em>Ask anything about the clothes store. Conversation continues across refresh.</em>
          </div>
        ) : (
          messages.map((m, idx) => (
            <div key={idx} className={`chatMsg ${m.role === 'user' ? 'chatUser' : 'chatAssistant'}`}>
              <div className="chatRole mono">{m.role}</div>
              <div className="chatContent">{m.content}</div>
            </div>
          ))
        )}
      </div>

      {showToolTrace && lastToolTrace && (
        <details style={{ marginTop: 10 }}>
          <summary className="mono" style={{ cursor: 'pointer' }}>
            Tool Trace {lastToolTrace.length ? `(${lastToolTrace.length})` : ''}
          </summary>
          <div style={{ marginTop: 8 }} className="mono">
            {lastToolTrace.length === 0 ? (
              <div style={{ opacity: 0.7 }}>No tools ran for the last assistant response.</div>
            ) : (
              lastToolTrace.map((t) => (
                <div key={`${t.step}-${t.tool}`} style={{ padding: '6px 0', borderTop: '1px solid rgba(255,255,255,0.08)' }}>
                  <div>
                    step {t.step}: <strong>{t.tool}</strong> ok={String(t.ok)}
                  </div>
                  <div style={{ opacity: 0.75 }}>args: {JSON.stringify(t.args || {})}</div>
                  <div style={{ opacity: 0.75 }}>result: {t.resultPreview}</div>
                </div>
              ))
            )}
          </div>
        </details>
      )}

      <div className="row" style={{ marginTop: 12 }}>
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder="Type a message… (Enter to send)"
          style={{ minHeight: 90 }}
          disabled={isSending}
        />
        <button onClick={send} disabled={isSending || !input.trim()}>
          Send
        </button>
      </div>
    </div>
  );
}

export default ChatPanel;



