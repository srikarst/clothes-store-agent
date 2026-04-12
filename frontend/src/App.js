import React, { useMemo, useState } from 'react';

function App() {
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [messages, setMessages] = useState([]);
  const [lastMeta, setLastMeta] = useState(null);

  const canSend = useMemo(() => input.trim().length > 0 && !isSending, [input, isSending]);

  const sendMessage = async () => {
    const text = input.trim();
    if (!text || isSending) return;

    setInput('');
    setIsSending(true);
    setMessages((prev) => [...prev, { role: 'user', content: text }]);

    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text })
      });

      const data = await response.json();
      if (!response.ok) {
        throw new Error(data?.message || `Request failed with ${response.status}`);
      }

      setMessages((prev) => [...prev, { role: 'assistant', content: data.assistantMessage || '' }]);
      setLastMeta({
        skill: data.skill,
        route: data.route,
        ragContext: Array.isArray(data.ragContext) ? data.ragContext : [],
        localTools: Array.isArray(data.localTools) ? data.localTools : [],
        mcpCalls: Array.isArray(data.mcpCalls) ? data.mcpCalls : []
      });
    } catch (error) {
      setMessages((prev) => [...prev, { role: 'assistant', content: `Error: ${error.message}` }]);
      setLastMeta(null);
    } finally {
      setIsSending(false);
    }
  };

  const onInputKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      sendMessage();
    }
  };

  return (
    <main className="appShell">
      <section className="card">
        <h1>Clothes Store Agent</h1>
        <p className="hint">
          Deterministic skill routing with local extraction tools and multi-MCP orchestration.
        </p>

        <div className="chatList">
          {messages.length === 0 ? (
            <p className="empty">Try: "Can I return this after 12 days?" or "How fast can this ship internationally?"</p>
          ) : (
            messages.map((message, index) => (
              <article key={`${message.role}-${index}`} className={`chatMessage ${message.role}`}>
                <strong>{message.role === 'user' ? 'You' : 'Agent'}</strong>
                <p>{message.content}</p>
              </article>
            ))
          )}
        </div>

        {lastMeta && (
          <div className="meta">
            <p><strong>Skill:</strong> {lastMeta.skill || 'general_help'}</p>
            <p><strong>Route:</strong> {lastMeta.route || 'none'}</p>
            <p>
              <strong>RAG:</strong>{' '}
              {lastMeta.ragContext.length > 0 ? lastMeta.ragContext.join(' | ') : 'No matching context'}
            </p>
            <p>
              <strong>Local Tools:</strong>{' '}
              {lastMeta.localTools.length > 0
                ? lastMeta.localTools.map((tool) => `${tool.toolName} -> ${tool.output}`).join(' | ')
                : 'No local tool execution'}
            </p>
            <p>
              <strong>MCP Calls:</strong>{' '}
              {lastMeta.mcpCalls.length > 0
                ? lastMeta.mcpCalls
                  .map((call) => `${call.serverId}/${call.toolName} -> ${call.output} (${call.ok ? 'ok' : 'failed'})`)
                  .join(' | ')
                : 'No MCP call for this message'}
            </p>
          </div>
        )}

        <div className="composer">
          <textarea
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={onInputKeyDown}
            placeholder="Type a message..."
            disabled={isSending}
          />
          <button onClick={sendMessage} disabled={!canSend}>
            {isSending ? 'Sending...' : 'Send'}
          </button>
        </div>
      </section>
    </main>
  );
}

export default App;
