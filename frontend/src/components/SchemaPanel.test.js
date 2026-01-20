// Import helper functions from React Testing Library.
// - render: mounts a React component into a fake DOM for testing.
// - screen: lets us find elements in that fake DOM (like a user would).
// - waitFor: repeatedly runs an assertion until it passes (or times out).
import { render, screen, waitFor } from '@testing-library/react';

// Import the component we want to test.
import SchemaPanel from './SchemaPanel';

// Define a test case.
// The test function is async because we will wait for async UI updates.
test('loads schema and shows loaded pill', async () => {
  // Create a fake API response that matches what /api/schema returns.
  const fakeSchema = {
    // A list of tables returned by the backend.
    tables: [{ TABLE_SCHEMA: 'dbo', TABLE_NAME: 'customers' }],
    // A map: table name -> list of column metadata.
    columnsByTable: {
      // The key is schema.table (like dbo.customers).
      // The value is an array of column objects.
      'dbo.customers': [{ COLUMN_NAME: 'id', DATA_TYPE: 'int' }],
    },
    // Foreign keys (empty for this test).
    fks: [],
    // Sample values by column (empty for this test).
    samplesByColumn: {},
  };

  // Replace the real network call (fetch) with a fake one.
  // This keeps the test fast and makes it not depend on the backend.
  global.fetch = jest.fn().mockResolvedValue({
    // Pretend the HTTP request succeeded.
    ok: true,
    // The component calls res.json(), so we provide a fake json() function.
    // It returns our fakeSchema when awaited.
    json: async () => fakeSchema,
  });

  // Render the SchemaPanel into the test DOM.
  // Rendering triggers useEffect(), so SchemaPanel will call loadSchema() on mount.
  render(<SchemaPanel />);

  // Check that the "Load Schema" button exists.
  // getByRole throws if it can't find it (which fails the test).
  expect(screen.getByRole('button', { name: /load schema/i })).toBeInTheDocument();

  // Wait until the UI shows the "✓ Loaded" pill.
  // findByText waits/retries for async updates (like after fetch finishes).
  expect(await screen.findByText('✓ Loaded')).toBeInTheDocument();

  // Check that the table key "dbo.customers" is shown.
  expect(screen.getByText('dbo.customers')).toBeInTheDocument();

  // Sanity check: confirm our mocked fetch was actually called.
  expect(global.fetch).toHaveBeenCalled();
});

test('handles fetch failure (error path)', async () => {
  // This test shows the basic pattern for a "failed fetch" scenario:
  // 1) Make fetch reject (or return ok:false)
  // 2) Assert the component reacts appropriately (here: logs an error)

  // Silence console.error output during this test (so the test output stays clean).
  // We still assert it was called.
  const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});

  // Make fetch fail by rejecting the promise.
  // (Alternative: resolve with { ok: false } and have your component handle that.)
  global.fetch = jest.fn().mockRejectedValue(new Error('Network down'));

  // Render the component; its useEffect() will attempt to fetch immediately.
  render(<SchemaPanel />);

  // Wait until the component's catch block runs and logs the error.
  await waitFor(() => {
    expect(consoleErrorSpy).toHaveBeenCalledWith('Failed to load schema:', expect.any(Error));
  });

  // Clean up the spy so other tests (or the app) behave normally.
  consoleErrorSpy.mockRestore();
});
