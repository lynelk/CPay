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

// CPay's current Java API verifies signatures using legacy concatenated
// field values rather than a key=value query-style canonical string.
// Keep this in sync with Api.java until the backend is migrated to a
// versioned canonical-signature contract.
const SIGNING_FIELDS = {
    collect: ['merchant_number', 'payer_number', 'amount', 'reference', 'description'],
    payout: ['merchant_number', 'payee_number', 'amount', 'reference', 'description'],
    status: ['merchant_number', 'reference'],
    balances: ['merchant_number'],
};

function canonicalize(payload, operation) {
    const fields = SIGNING_FIELDS[operation];
    if (!fields) throw new Error(`Unsupported CPay signing operation: ${operation}`);
    return fields
        .map((field) => payload[field])
        .filter((value) => value !== undefined && value !== null)
        .map((value) => String(value))
        .join('');
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

    async function call(path, operation, payload) {
        const body = {
            merchant_number: merchantNumber,
            ...payload,
        };

        if (!body.callback_url && defaultCallbackUrl) {
            body.callback_url = defaultCallbackUrl;
        }

        body.signature = sign(canonicalize(body, operation), signingKeyPem);
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
        collect: ({ amount, payerNumber, reference, description, callbackUrl }) => {
            const payload = {
                amount,
                payer_number: payerNumber,
                reference,
                description,
            };
            if (callbackUrl) payload.callback_url = callbackUrl;
            return call('/api/v1/doMobileMoneyPayIn', 'collect', payload);
        },
        payout: ({ amount, payeeNumber, reference, description, callbackUrl }) => {
            const payload = {
                amount,
                payee_number: payeeNumber,
                reference,
                description,
            };
            if (callbackUrl) payload.callback_url = callbackUrl;
            return call('/api/v1/doMobileMoneyPayOut', 'payout', payload);
        },
        status: ({ reference }) =>
            call('/api/v1/doTransactionCheckStatus', 'status', { reference }),
        balances: () => call('/api/v1/doGetBalances', 'balances', {}),
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
