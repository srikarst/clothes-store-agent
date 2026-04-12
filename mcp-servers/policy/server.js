#!/usr/bin/env node
/**
 * Policy MCP server (JSON-RPC over stdio; one JSON object per line).
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
    name: 'check_return_eligibility',
    description: 'Evaluate return eligibility from policy inputs.',
    inputSchema: {
      type: 'object',
      properties: {
        daysSinceDelivery: { type: 'number', description: 'Days since delivery date.' },
        itemCondition: { type: 'string', enum: ['unused', 'used', 'unknown'], description: 'Reported item condition.' },
        hasTags: { type: 'boolean', description: 'Whether tags are still attached.' }
      },
      required: ['daysSinceDelivery', 'itemCondition', 'hasTags'],
      additionalProperties: false
    }
  },
  {
    name: 'estimate_refund_timeline',
    description: 'Estimate refund settlement timeline.',
    inputSchema: {
      type: 'object',
      properties: {
        daysSinceDelivery: { type: 'number', description: 'Days since delivery date.' },
        paymentMethod: { type: 'string', description: 'Payment method (card, wallet, etc.).' }
      },
      required: ['daysSinceDelivery'],
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
        serverInfo: { name: 'policy-mcp', version: '1.0.0' }
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

    if (name === 'check_return_eligibility') {
      const daysSinceDelivery = Number(args.daysSinceDelivery);
      const itemCondition = String(args.itemCondition || '').toLowerCase();
      const hasTags = Boolean(args.hasTags);
      if (!Number.isFinite(daysSinceDelivery) || !['unused', 'used', 'unknown'].includes(itemCondition)) {
        return write({
          jsonrpc: '2.0',
          id,
          result: {
            isError: true,
            content: [{ type: 'text', text: 'Invalid arguments for check_return_eligibility.' }]
          }
        });
      }
      let text;
      if (daysSinceDelivery > 30) {
        text = 'eligible=false; reason=Outside 30-day return window.';
      } else if (itemCondition === 'used') {
        text = 'eligible=false; reason=Item is marked as used.';
      } else if (!hasTags) {
        text = 'eligible=false; reason=Tags are missing.';
      } else if (itemCondition === 'unknown') {
        text = 'eligible=review_required; reason=Need item condition confirmation.';
      } else {
        text = 'eligible=true; reason=Within policy window and condition requirements.';
      }
      return write({
        jsonrpc: '2.0',
        id,
        result: {
          isError: false,
          content: [{ type: 'text', text }]
        }
      });
    }

    if (name === 'estimate_refund_timeline') {
      const daysSinceDelivery = Number(args.daysSinceDelivery);
      const paymentMethod = String(args.paymentMethod || 'card').toLowerCase();
      if (!Number.isFinite(daysSinceDelivery)) {
        return write({
          jsonrpc: '2.0',
          id,
          result: {
            isError: true,
            content: [{ type: 'text', text: 'Invalid arguments for estimate_refund_timeline.' }]
          }
        });
      }
      let text;
      if (daysSinceDelivery > 30) {
        text = 'timeline=not_applicable; reason=Likely outside return window.';
      } else if (paymentMethod === 'wallet') {
        text = 'timeline=2-4 business days after inspection.';
      } else {
        text = 'timeline=3-5 business days after inspection + bank settlement.';
      }
      return write({
        jsonrpc: '2.0',
        id,
        result: {
          isError: false,
          content: [{ type: 'text', text }]
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


