import React from 'react';

function ResultPanel({ queryResult }) {
  return (
    <div className="card">
      <h2>Result</h2>
      <pre className="code">{JSON.stringify(queryResult, null, 2)}</pre>
    </div>
  );
}

export default ResultPanel;
