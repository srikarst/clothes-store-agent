import React, { useState } from 'react';

function NlqPanel() {
  const [prompt, setPrompt] = useState('');
  const [execute, setExecute] = useState(true);
  const [generatedSql, setGeneratedSql] = useState('(SQL will appear here)');
  const [nlqResult, setNlqResult] = useState('{}');
  const [intent, setIntent] = useState('');

  const runNlq = async () => {
    const trimmedPrompt = prompt.trim();
    if (!trimmedPrompt) {
      alert('Enter a prompt');
      return;
    }

    try {
      const res = await fetch('/api/nlq', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: trimmedPrompt, execute })
      });
      const data = await res.json();

      setIntent(data.recognizedIntent || '');
      setGeneratedSql(data.sql || '(no SQL)');

      // If unrecognized, show suggestions
      if (data.error === 'UNRECOGNIZED') {
        setNlqResult(JSON.stringify({ message: data.message, try: data.try }, null, 2));
        return;
      }

      // Show result or dry-run info
      if (execute && data.result) {
        setNlqResult(JSON.stringify(data.result, null, 2));
      } else {
        setNlqResult(JSON.stringify({ ran: data.ran, params: data.params || {} }, null, 2));
      }
    } catch (err) {
      console.error('NLQ failed:', err);
      setNlqResult(JSON.stringify({ error: err.message }, null, 2));
    }
  };

  const copyToEditor = () => {
    // This would need to communicate with QueryPanel
    // For now, just copying to clipboard
    navigator.clipboard.writeText(generatedSql);
    alert('SQL copied to clipboard! You can paste it in the Run SELECT box.');
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      runNlq();
    }
  };

  return (
    <div className="card">
      <h2>Ask in English (Text → SQL)</h2>
      <input
        type="search"
        placeholder='e.g., "top 5 products by revenue last month"'
        value={prompt}
        onChange={(e) => setPrompt(e.target.value)}
        onKeyDown={handleKeyDown}
      />
      <div className="row" style={{ margin: '8px 0 12px' }}>
        <label className="mono">
          <input
            type="checkbox"
            checked={execute}
            onChange={(e) => setExecute(e.target.checked)}
          />
          {' '}execute SQL
        </label>
        <button onClick={runNlq}>Run NLQ</button>
        <button className="secondary" onClick={copyToEditor} title="Copy generated SQL to clipboard">
          Copy SQL to editor
        </button>
      </div>
      <div className="cols">
        <div>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <strong>Generated SQL</strong>
            {intent && <small className="tag">{intent}</small>}
          </div>
          <pre className="code">{generatedSql}</pre>
        </div>
        <div>
          <strong>NLQ Result</strong>
          <pre className="code">{nlqResult}</pre>
        </div>
      </div>
    </div>
  );
}

export default NlqPanel;
