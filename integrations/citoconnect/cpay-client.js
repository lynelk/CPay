// Reference Node.js client for invoking the CPay /api/v1 endpoints.
// This is the same logic that CitoConnect's `cpay` base44 function uses
// to talk to the CPay Spring Boot service. Distributed here for any
// other Node/JS service that needs to integrate.
//
// Usage:
//
//   import { createCPayClient } from './cpay-client.js';
//
//   const cpay = createCPayClient({
//     baseUrl: process.env.CPAY_BASE_URL,
//     merchantNumber: process.env.CPAY_MERCHANT_NUMBER,
//     signingKeyPem: process.env.CPAY_SIGNING_KEY_PEM,
//     defaultCallbackUrl: process.env.CPAY_DEFAULT_CALLBACK_URL,
//   });
//
//   await cpay.collect({ amount: 50000, payerNumber: '256771234567', reference: 'TXN-1', description: 'Order #1' });

import crypto from 'node:crypto';

const FIELD_ORDER = ['amount', 'description', 'reference', 'merchant_number',
    'payer_number', 'payee_number', 'callback_url'];

function canonicalize(payload) {
    return FIELD_ORDER
        .filter((f) => payload[f] !== undefined && payload[f] !== null && payload[f] !== '')
        .map((f) => `${f}=${payload[f]}`)
        .join('&');
}

function sign(canonical, signingKeyPem) {
    const signer = crypto.createSign('RSA-SHA256');
    signer.update(canonical);
    signer.end();
    return signer.sign(signingKeyPem, 'base64');
}

export function createCPayClient({ baseUrl, merchantNumber, signingKeyPem, defaultCallbackUrl }) {
    if (!baseUrl) throw new Error('baseUrl is required');
    if (!merchantNumber) throw new Error('merchantNumber is required');
    if (!signingKeyPem) throw new Error('signingKeyPem is required');

    async function call(path, payload) {
        const body = {
            merchant_number: merchantNumber,
            callback_url: defaultCallbackUrl,
            ...payload,
        };
        body.signature = sign(canonicalize(body), signingKeyPem);
        const res = await fetch(`${baseUrl.replace(/\/$/, '')}${path}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        const text = await res.text();
        let parsed;
        try { parsed = JSON.parse(text); } catch { parsed = { raw: text }; }
        if (!res.ok) {
            const err = new Error(parsed?.message || `CPay ${path} failed (${res.status})`);
            err.status = res.status;
            err.body = parsed;
            throw err;
        }
        return parsed;
    }

    return {
        collect: ({ amount, payerNumber, reference, description, callbackUrl }) =>
            call('/api/v1/doMobileMoneyPayIn', {
                amount,
                payer_number: payerNumber,
                reference,
                description,
                callback_url: callbackUrl,
            }),
        payout: ({ amount, payeeNumber, reference, description, callbackUrl }) =>
            call('/api/v1/doMobileMoneyPayOut', {
                amount,
                payee_number: payeeNumber,
                reference,
                description,
                callback_url: callbackUrl,
            }),
        status: ({ reference }) =>
            call('/api/v1/doTransactionCheckStatus', { reference }),
        balances: () => call('/api/v1/doGetBalances', {}),
    };
}

// Helper for verifying CPay webhook signatures on the merchant side.
export function verifyCPayWebhookSignature({ reference, transactionStatus, signatureB64, publicKeyPem }) {
    const canonical = `reference=${reference}&transaction_status=${transactionStatus}`;
    const verifier = crypto.createVerify('RSA-SHA256');
    verifier.update(canonical);
    verifier.end();
    return verifier.verify(publicKeyPem, signatureB64, 'base64');
}
