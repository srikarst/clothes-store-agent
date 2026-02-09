#!/usr/bin/env node
/**
 * Demo MCP server (JSON-RPC over stdio; one JSON object per line).
 * No external dependencies.
 */

const readline = require('readline');

function write(obj) {
  process.stdout.write(JSON.stringify(obj) + '\n');
}

function jsonrpcError(id, code, message, data) {
  const err = { code, message };
  if (data !== undefined) err.data = data;
  write({ jsonrpc: '2.0', id, error: err });
}

const TOOLS = [
  {
    name: 'echo',
    description: 'Echo back the provided text.',
    inputSchema: {
      type: 'object',
      properties: {
        text: { type: 'string', description: 'Text to echo.' }
      },
      required: ['text'],
      additionalProperties: false
    }
  },
  {
    name: 'math_add',
    description: 'Add two numbers and return the sum.',
    inputSchema: {
      type: 'object',
      properties: {
        a: { type: 'number', description: 'First number.' },
        b: { type: 'number', description: 'Second number.' }
      },
      required: ['a', 'b'],
      additionalProperties: false
    }
  }
];

function handleRequest(msg) {
  const id = msg.id;
  const method = msg.method;
  const params = msg.params || {};

  if (method === 'initialize') {
    const pv = params.protocolVersion || '2024-11-05';
    return write({
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: pv,
        capabilities: { tools: {} },
        serverInfo: { name: 'demo-mcp', version: '0.0.1' }
      }
    });
  }

  if (method === 'tools/list') {
    return write({
      jsonrpc: '2.0',
      id,
      result: { tools: TOOLS }
    });
  }

  if (method === 'tools/call') {
    const name = params.name;
    const args = params.arguments || {};

    if (name === 'echo') {
      const text = args.text !== undefined ? String(args.text) : '';
      return write({
        jsonrpc: '2.0',
        id,
        result: {
          isError: false,
          content: [{ type: 'text', text }]
        }
      });
    }

    if (name === 'math_add') {
      const a = Number(args.a);
      const b = Number(args.b);
      if (!Number.isFinite(a) || !Number.isFinite(b)) {
        return write({
          jsonrpc: '2.0',
          id,
          result: {
            isError: true,
            content: [{ type: 'text', text: 'Invalid arguments: expected numbers a and b.' }]
          }
        });
      }
      const sum = a + b;
      return write({
        jsonrpc: '2.0',
        id,
        result: {
          isError: false,
          content: [{ type: 'text', text: String(sum) }]
        }
      });
    }

    return write({
      jsonrpc: '2.0',
      id,
      result: {
        isError: true,
        content: [{ type: 'text', text: `Unknown tool: ${name}` }]
      }
    });
  }

  return jsonrpcError(id, -32601, `Method not found: ${method}`);
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });

rl.on('line', (line) => {
  if (!line || !line.trim()) return;
  let msg;
  try {
    msg = JSON.parse(line);
  } catch (e) {
    // Ignore non-JSON lines.
    process.stderr.write(`non-json line ignored\n`);
    return;
  }

  // Notifications have no id; ignore.
  if (!msg || typeof msg !== 'object' || msg.id === undefined || msg.id === null) {
    return;
  }

  try {
    handleRequest(msg);
  } catch (e) {
    jsonrpcError(msg.id, -32000, 'Server error', { message: String(e && e.message ? e.message : e) });
  }
});

rl.on('close', () => process.exit(0));


