"use strict";

const { signRequest } = require("./cpay-signing");

class CPayClient {
  constructor({ baseUrl, merchantNumber, privateKeyPem, fetchImpl = fetch }) {
    if (!baseUrl) throw new Error("baseUrl is required");
    if (!merchantNumber) throw new Error("merchantNumber is required");
    if (!privateKeyPem) throw new Error("privateKeyPem is required");
    this.baseUrl = baseUrl.replace(/\/+$/, "");
    this.merchantNumber = merchantNumber;
    this.privateKeyPem = privateKeyPem;
    this.fetch = fetchImpl;
  }

  collect(request) {
    return this.post("/api/v2/payments/collect", request);
  }

  payout(request) {
    return this.post("/api/v2/payments/payout", request);
  }

  validateAccount(request) {
    return this.post("/api/v2/accounts/validate", request);
  }

  createPaymentLink(request) {
    return this.post("/api/v2/payment-links", request);
  }

  statements({ startDate, endDate, format = "json", limit }) {
    const query = { merchantNumber: this.merchantNumber, startDate, endDate, format, limit };
    return this.get("/api/v2/statements", query);
  }

  async post(path, request) {
    const body = JSON.stringify({ merchantNumber: this.merchantNumber, ...request });
    const headers = {
      "Content-Type": "application/json",
      ...signRequest({
        merchantNumber: this.merchantNumber,
        privateKeyPem: this.privateKeyPem,
        method: "POST",
        path,
        body
      })
    };
    return this.send(path, { method: "POST", headers, body });
  }

  async get(path, query) {
    const cleanQuery = Object.fromEntries(Object.entries(query).filter(([, value]) => value !== undefined && value !== null));
    const search = new URLSearchParams(cleanQuery).toString();
    const headers = signRequest({
      merchantNumber: this.merchantNumber,
      privateKeyPem: this.privateKeyPem,
      method: "GET",
      path,
      query: cleanQuery
    });
    return this.send(`${path}?${search}`, { method: "GET", headers });
  }

  async send(pathAndQuery, init) {
    const response = await this.fetch(`${this.baseUrl}${pathAndQuery}`, init);
    const text = await response.text();
    const payload = text ? JSON.parse(text) : {};
    if (!response.ok) {
      const error = new Error(payload.message || `CPay request failed with ${response.status}`);
      error.status = response.status;
      error.payload = payload;
      throw error;
    }
    return payload;
  }
}

module.exports = { CPayClient };
