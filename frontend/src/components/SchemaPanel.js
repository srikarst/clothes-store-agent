import React, { useState, useEffect } from 'react';

function SchemaPanel() {
  const [schema, setSchema] = useState(null);
  const [loaded, setLoaded] = useState(false);

  const loadSchema = async () => {
    try {
      const res = await fetch('/api/schema');
      const data = await res.json();
      setSchema(data);
      setLoaded(true);
    } catch (err) {
      console.error('Failed to load schema:', err);
    }
  };

  useEffect(() => {
    loadSchema();
  }, []);

  const tables = schema?.tables || [];
  const columnsByTable = schema?.columnsByTable || {};
  const fks = schema?.fks || [];
  const samplesByColumn = schema?.samplesByColumn || {};

  return (
    <div className="card">
      <h2>Schema</h2>
      <div className="row" style={{ marginBottom: '8px' }}>
        <button onClick={loadSchema}>Load Schema</button>
        {loaded && <span className="pill">✓ Loaded</span>}
      </div>

      <table>
        <thead>
          <tr>
            <th>Table</th>
            <th>Columns</th>
          </tr>
        </thead>
        <tbody>
          {tables.map((t, idx) => {
            const key = (t.TABLE_SCHEMA || 'dbo') + '.' + t.TABLE_NAME;
            const cols = (columnsByTable[key] || [])
              .map(c => `${c.COLUMN_NAME} (${String(c.DATA_TYPE).replace(/\s+/g, ' ')})`)
              .join(', ');
            return (
              <tr key={idx}>
                <td className="mono">{key}</td>
                <td className="mono">{cols}</td>
              </tr>
            );
          })}
        </tbody>
      </table>

      <h3 style={{ marginTop: '16px' }}>Foreign Keys</h3>
      <table>
        <thead>
          <tr>
            <th>From</th>
            <th>→</th>
            <th>To</th>
          </tr>
        </thead>
        <tbody>
          {fks.length === 0 ? (
            <tr>
              <td colSpan="3">
                <em>No FKs found (or filtered by allowTables)</em>
              </td>
            </tr>
          ) : (
            fks.map((fk, idx) => {
              const from = `${fk.from_schema}.${fk.from_table}.${fk.from_column}`;
              const to = `${fk.to_schema}.${fk.to_table}.${fk.to_column}`;
              return (
                <tr key={idx}>
                  <td className="mono">{from}</td>
                  <td>→</td>
                  <td className="mono">{to}</td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <h3 style={{ marginTop: '16px' }}>Sample Values</h3>
      <table>
        <thead>
          <tr>
            <th>Column</th>
            <th>Samples</th>
          </tr>
        </thead>
        <tbody>
          {Object.entries(samplesByColumn).length === 0 ? (
            <tr>
              <td colSpan="2">
                <em>No samples (increase app.schemaSamplesPerColumn?)</em>
              </td>
            </tr>
          ) : (
            Object.entries(samplesByColumn).map(([col, vals], idx) => (
              <tr key={idx}>
                <td className="mono">{col}</td>
                <td>
                  {(vals || []).map((v, i) => (
                    <small key={i} className="tag" style={{ marginRight: '4px' }}>
                      {v}
                    </small>
                  ))}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export default SchemaPanel;
