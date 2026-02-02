import React, { useEffect, useMemo, useRef, useState } from 'react';

const STORAGE_KEY = 'chatConversationId';

function ChatPanel() {
  const [conversationId, setConversationId] = useState(() => localStorage.getItem(STORAGE_KEY) || '');
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState([]);
  const [isSending, setIsSending] = useState(false);
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
    setMessages((prev) => [...prev, { role: 'user', content: text }]);

    try {
      const headers = { 'Content-Type': 'application/json' };
      if (hasConversation) {
        headers['X-Conversation-Id'] = conversationId;
      }

      const res = await fetch('/api/chat', {
        method: 'POST',
        headers,
        body: JSON.stringify({ message: text })
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data?.message || `Chat failed: HTTP ${res.status}`);
      }

      if (data.conversationId && data.conversationId !== conversationId) {
        setConversationId(data.conversationId);
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



