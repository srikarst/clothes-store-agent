import React, { useState } from 'react';

function QueryPanel({ setQueryResult }) {
  const [sql, setSql] = useState('SELECT TOP 5 name FROM sys.tables');
  const [params, setParams] = useState('');
  const [maxRows, setMaxRows] = useState(200);
  const [timeout, setTimeout] = useState(20);

  const runSelect = async () => {
    let parsedParams = {};
    const ptxt = params.trim();
    if (ptxt) {
      try {
        parsedParams = JSON.parse(ptxt);
      } catch (e) {
        alert('Params JSON is invalid');
        return;
      }
    }

    const body = {
      sql,
      params: parsedParams,
      maxRows: parseInt(maxRows || '200'),
      timeoutSeconds: parseInt(timeout || '20')
    };

    try {
      const res = await fetch('/api/query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      const data = await res.json();
      setQueryResult(data);
    } catch (err) {
      console.error('Query failed:', err);
      setQueryResult({ error: err.message });
    }
  };

  return (
    <div className="card">
      <h2>Run SELECT</h2>
      <textarea
        spellCheck="false"
        value={sql}
        onChange={(e) => setSql(e.target.value)}
      />
      <div className="row" style={{ marginTop: '8px' }}>
        <label className="mono" style={{ flex: 1 }}>
          Params (JSON):
          <input
            type="text"
            placeholder='{"nameLike":"%"}'
            value={params}
            onChange={(e) => setParams(e.target.value)}
          />
        </label>
        <label className="mono">
          Max Rows:
          <input
            type="number"
            value={maxRows}
            onChange={(e) => setMaxRows(e.target.value)}
            style={{ width: '120px' }}
          />
        </label>
        <label className="mono">
          Timeout (s):
          <input
            type="number"
            value={timeout}
            onChange={(e) => setTimeout(e.target.value)}
            style={{ width: '120px' }}
          />
        </label>
        <button onClick={runSelect}>Run</button>
      </div>
    </div>
  );
}

export default QueryPanel;
