#!/usr/bin/env node
/*
 * RuneGlass mock backend.
 *
 * Stands in for the Convex HTTP actions so the plugin's pairing and transport can be built and
 * tested before the real backend exists. Implements the contract defined in
 * src/main/java/app/runeglass/plugin/RuneGlassApi.java — change them together.
 *
 * No dependencies. Run with: node tools/mock-server/server.js
 *
 * Everything lives in memory and nothing is authenticated beyond a token shape check. This is a
 * development fixture, not a reference implementation.
 */
'use strict';

const http = require('http');
const crypto = require('crypto');

const PORT = process.env.PORT || 8787;
const PAIRING_TTL_MS = 5 * 60 * 1000;
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no I/O/0/1

/** pairingId -> { code, clientNonce, status, userId, accountName, expiresAt } */
const pairings = new Map();
/** deviceToken -> { userId, accountName } */
const tokens = new Map();
/** accountHash -> { displayName, lastSeq, events, snapshot } */
const characters = new Map();

function code() {
  const pick = (n) =>
    Array.from({ length: n }, () => CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)]).join('');
  return `${pick(3)}-${pick(3)}`;
}

function send(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(payload),
  });
  res.end(payload);
}

function fail(res, status, error, message) {
  send(res, status, { error, message });
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 8 * 1024 * 1024) {
        reject(new Error('payload too large'));
        req.destroy();
      }
    });
    req.on('end', () => {
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (e) {
        reject(e);
      }
    });
    req.on('error', reject);
  });
}

function bearer(req) {
  const header = req.headers.authorization || '';
  const match = /^Bearer (.+)$/.exec(header);
  return match ? match[1] : null;
}

function sweep() {
  const now = Date.now();
  for (const [id, p] of pairings) {
    if (p.expiresAt < now) pairings.delete(id);
  }
}

const routes = {
  // ---------------------------------------------------------------- pairing

  'POST /v1/pair/start': async (req, res) => {
    const body = await readJson(req);
    if (!body.clientNonce || typeof body.clientNonce !== 'string') {
      return fail(res, 400, 'bad_request', 'clientNonce is required');
    }

    sweep();

    const pairingId = crypto.randomUUID();
    const pairing = {
      code: code(),
      clientNonce: body.clientNonce,
      status: 'pending',
      userId: null,
      accountName: null,
      expiresAt: Date.now() + PAIRING_TTL_MS,
    };
    pairings.set(pairingId, pairing);

    console.log(`\n  pairing ${pairing.code}  — claim it with:`);
    console.log(
      `  curl -s -XPOST localhost:${PORT}/dev/claim -d '{"code":"${pairing.code}","accountName":"Jeroen"}'\n`
    );

    send(res, 200, { pairingId, code: pairing.code, expiresAt: pairing.expiresAt });
  },

  'POST /v1/pair/poll': async (req, res) => {
    const body = await readJson(req);
    const pairing = pairings.get(body.pairingId);

    if (!pairing || pairing.expiresAt < Date.now()) {
      return send(res, 200, { status: 'expired' });
    }

    // The nonce is what stops a shoulder-surfer who saw the code from collecting the token.
    if (pairing.clientNonce !== body.clientNonce) {
      return fail(res, 403, 'nonce_mismatch', 'clientNonce does not match this pairing');
    }

    if (pairing.status !== 'claimed') {
      return send(res, 200, { status: 'pending' });
    }

    const deviceToken = `rg_${crypto.randomBytes(24).toString('base64url')}`;
    tokens.set(deviceToken, { userId: pairing.userId, accountName: pairing.accountName });
    pairings.delete(body.pairingId); // single use

    console.log(`  linked -> ${pairing.accountName} (token ${deviceToken.slice(0, 12)}…)`);
    send(res, 200, { status: 'claimed', deviceToken, accountName: pairing.accountName });
  },

  // ---------------------------------------------------------------- ingest

  'POST /v1/ingest': async (req, res) => {
    const token = bearer(req);
    if (!token || !tokens.has(token)) {
      return fail(res, 401, 'unauthorized', 'unknown or missing device token');
    }

    const body = await readJson(req);

    if (typeof body.accountHash !== 'string') {
      return fail(
        res,
        400,
        'bad_account_hash',
        'accountHash must be a decimal string — a JSON number loses precision above 2^53'
      );
    }

    let character = characters.get(body.accountHash);
    if (!character) {
      character = { displayName: body.displayName, lastSeq: -1, events: [], snapshot: null };
      characters.set(body.accountHash, character);
      console.log(`  new character: ${body.displayName} (${body.accountHash}) ${body.accountType}`);
    }
    character.displayName = body.displayName;

    let accepted = 0;
    let duplicates = 0;
    for (const event of body.events || []) {
      if (event.seq <= character.lastSeq) {
        duplicates++;
        continue;
      }
      if (event.seq > character.lastSeq + 1 && character.lastSeq >= 0) {
        console.log(`  ! gap: expected seq ${character.lastSeq + 1}, got ${event.seq}`);
      }
      character.lastSeq = event.seq;
      character.events.push(event);
      accepted++;
      console.log(`  event #${event.seq} ${event.kind} ${JSON.stringify(event.data)}`);
    }

    if (body.snapshot) {
      character.snapshot = body.snapshot;
      const skills = Object.keys(body.snapshot.skills || {}).length;
      const inv = (body.snapshot.inventory || []).length;
      console.log(`  snapshot: ${skills} skills, ${inv} inventory slots`);
    }

    if (duplicates) console.log(`  (${duplicates} duplicate events ignored)`);

    send(res, 200, { ok: true, ackSeq: character.lastSeq });
  },

  'GET /v1/commands': async (req, res) => {
    const token = bearer(req);
    if (!token || !tokens.has(token)) {
      return fail(res, 401, 'unauthorized', 'unknown or missing device token');
    }
    send(res, 200, []); // v2 scaffolding
  },

  // ---------------------------------------------------------------- dev helpers

  // Stands in for the phone app claiming a code.
  'POST /dev/claim': async (req, res) => {
    const body = await readJson(req);
    for (const [id, pairing] of pairings) {
      if (pairing.code === (body.code || '').toUpperCase()) {
        pairing.status = 'claimed';
        pairing.userId = body.userId || 'user_dev';
        pairing.accountName = body.accountName || 'Dev User';
        return send(res, 200, { ok: true, pairingId: id });
      }
    }
    fail(res, 404, 'no_such_code', 'no pending pairing with that code');
  },

  'GET /dev/state': async (req, res) => {
    send(res, 200, {
      pairings: [...pairings.entries()].map(([id, p]) => ({ id, code: p.code, status: p.status })),
      tokens: tokens.size,
      characters: [...characters.entries()].map(([hash, c]) => ({
        accountHash: hash,
        displayName: c.displayName,
        lastSeq: c.lastSeq,
        events: c.events.length,
        hasSnapshot: !!c.snapshot,
      })),
    });
  },
};

const server = http.createServer(async (req, res) => {
  const path = req.url.split('?')[0];
  const handler = routes[`${req.method} ${path}`];

  if (!handler) {
    return fail(res, 404, 'not_found', `${req.method} ${path}`);
  }

  try {
    await handler(req, res);
  } catch (e) {
    console.error(`  error handling ${req.method} ${path}:`, e.message);
    if (!res.headersSent) fail(res, 400, 'bad_request', e.message);
  }
});

server.listen(PORT, () => {
  console.log(`RuneGlass mock backend on http://localhost:${PORT}`);
  console.log(`Point the plugin's "API base URL" setting at it, then click Link account.`);
});
