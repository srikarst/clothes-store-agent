#!/usr/bin/env node
/**
 * Fulfillment MCP server (JSON-RPC over stdio; one JSON object per line).
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
    name: 'estimate_delivery_eta',
    description: 'Estimate delivery ETA range by region and speed.',
    inputSchema: {
      type: 'object',
      properties: {
        destinationRegion: { type: 'string', enum: ['domestic', 'international'] },
        shippingSpeed: { type: 'string', enum: ['standard', 'express'] }
      },
      required: ['destinationRegion', 'shippingSpeed'],
      additionalProperties: false
    }
  },
  {
    name: 'recommend_shipping_option',
    description: 'Recommend standard or express shipping option.',
    inputSchema: {
      type: 'object',
      properties: {
        destinationRegion: { type: 'string', enum: ['domestic', 'international'] },
        urgencyDays: { type: 'number' },
        budgetPriority: { type: 'boolean' }
      },
      required: ['destinationRegion'],
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
        serverInfo: { name: 'fulfillment-mcp', version: '1.0.0' }
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

    if (name === 'estimate_delivery_eta') {
      const destinationRegion = String(args.destinationRegion || '').toLowerCase();
      const shippingSpeed = String(args.shippingSpeed || '').toLowerCase();
      if (!['domestic', 'international'].includes(destinationRegion) || !['standard', 'express'].includes(shippingSpeed)) {
        return write({
          jsonrpc: '2.0',
          id,
          result: {
            isError: true,
            content: [{ type: 'text', text: 'Invalid arguments for estimate_delivery_eta.' }]
          }
        });
      }
      let eta;
      if (destinationRegion === 'international' && shippingSpeed === 'express') {
        eta = '3-6 business days';
      } else if (destinationRegion === 'international') {
        eta = '7-12 business days';
      } else if (shippingSpeed === 'express') {
        eta = '1-2 business days';
      } else {
        eta = '3-5 business days';
      }
      return write({
        jsonrpc: '2.0',
        id,
        result: {
          isError: false,
          content: [{ type: 'text', text: `eta=${eta}` }]
        }
      });
    }

    if (name === 'recommend_shipping_option') {
      const destinationRegion = String(args.destinationRegion || '').toLowerCase();
      const urgencyDays = Number(args.urgencyDays);
      const budgetPriority = Boolean(args.budgetPriority);
      if (!['domestic', 'international'].includes(destinationRegion)) {
        return write({
          jsonrpc: '2.0',
          id,
          result: {
            isError: true,
            content: [{ type: 'text', text: 'Invalid arguments for recommend_shipping_option.' }]
          }
        });
      }
      let recommendation;
      let rationale;
      if (Number.isFinite(urgencyDays) && urgencyDays <= 2) {
        recommendation = 'express';
        rationale = 'Urgent delivery requested.';
      } else if (budgetPriority) {
        recommendation = 'standard';
        rationale = 'Budget-sensitive preference.';
      } else if (destinationRegion === 'international') {
        recommendation = 'express';
        rationale = 'International route reliability.';
      } else {
        recommendation = 'standard';
        rationale = 'Balanced cost and SLA.';
      }
      return write({
        jsonrpc: '2.0',
        id,
        result: {
          isError: false,
          content: [{ type: 'text', text: `recommendation=${recommendation}; rationale=${rationale}` }]
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
    process.stderr.write('non-json line ignored\n');
    return;
  }

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

