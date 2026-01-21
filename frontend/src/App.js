import React from 'react';
import SchemaPanel from './components/SchemaPanel';
import QueryPanel from './components/QueryPanel';
import NlqPanel from './components/NlqPanel';
import ResultPanel from './components/ResultPanel';
import Playground from './components/Playground/Playground';

function App() {
  const [queryResult, setQueryResult] = React.useState({});

  return (
    <div>
      <Playground />
      <h1>Clothes Store Agent</h1>
      <p className="hint">
        No AI in the backend logic here — the <span className="mono">/api/nlq</span> endpoint is a simple
        rule-based mapper you can later replace with an LLM.
      </p>

      <div className="grid">
        <SchemaPanel />
        <QueryPanel setQueryResult={setQueryResult} />
        <NlqPanel />
        <ResultPanel queryResult={queryResult} />
      </div>
    </div>
  );
}

export default App;
