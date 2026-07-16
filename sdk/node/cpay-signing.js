"use strict";

const crypto = require("crypto");

function sha256Hex(value) {
  return crypto.createHash("sha256").update(value || "", "utf8").digest("hex");
}

function canonicalQuery(query) {
  if (!query) return "";
  const entries = query instanceof URLSearchParams
    ? Array.from(query.entries())
    : Object.entries(query);
  return entries
    .filter(([, value]) => value !== undefined && value !== null)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join("&");
}

function canonicalString({ method, path, query, timestamp, nonce, body }) {
  return [
    String(method || "GET").toUpperCase(),
    path || "/",
    canonicalQuery(query),
    timestamp,
    nonce,
    sha256Hex(body || "")
  ].join("\n");
}

function signRequest({
  merchantNumber,
  privateKeyPem,
  method,
  path,
  query,
  body = "",
  timestamp = new Date().toISOString(),
  nonce = crypto.randomUUID(),
  idempotencyKey = crypto.randomUUID()
}) {
  if (!merchantNumber) throw new Error("merchantNumber is required");
  if (!privateKeyPem) throw new Error("privateKeyPem is required");

  const canonical = canonicalString({ method, path, query, timestamp, nonce, body });
  const signer = crypto.createSign("RSA-SHA256");
  signer.update(canonical, "utf8");
  signer.end();

  return {
    "X-CPay-Merchant-Number": merchantNumber,
    "X-CPay-Signature-Version": "v2",
    "X-CPay-Timestamp": timestamp,
    "X-CPay-Nonce": nonce,
    "X-CPay-Signature": signer.sign(privateKeyPem).toString("base64"),
    "X-CPay-Idempotency-Key": idempotencyKey
  };
}

module.exports = {
  canonicalQuery,
  canonicalString,
  sha256Hex,
  signRequest
};
