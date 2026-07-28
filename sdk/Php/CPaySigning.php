<?php

final class CPaySigning
{
    public static function sha256Hex(string $value): string
    {
        return hash('sha256', $value);
    }

    public static function canonicalQuery(array $query = []): string
    {
        if (!$query) {
            return '';
        }
        ksort($query);
        $parts = [];
        foreach ($query as $key => $value) {
            if ($value === null) {
                continue;
            }
            $parts[] = rawurlencode((string) $key) . '=' . rawurlencode((string) $value);
        }
        return implode('&', $parts);
    }

    public static function canonicalString(
        string $method,
        string $path,
        array $query,
        string $timestamp,
        string $nonce,
        string $body = ''
    ): string {
        return implode("\n", [
            strtoupper($method ?: 'GET'),
            $path ?: '/',
            self::canonicalQuery($query),
            $timestamp,
            $nonce,
            self::sha256Hex($body),
        ]);
    }

    public static function signRequest(
        string $merchantNumber,
        string $privateKeyPem,
        string $method,
        string $path,
        array $query = [],
        string $body = '',
        ?string $timestamp = null,
        ?string $nonce = null,
        ?string $idempotencyKey = null
    ): array {
        if ($merchantNumber === '') {
            throw new InvalidArgumentException('merchantNumber is required');
        }
        if ($privateKeyPem === '') {
            throw new InvalidArgumentException('privateKeyPem is required');
        }
        $timestamp = $timestamp ?: gmdate('Y-m-d\TH:i:s\Z');
        $nonce = $nonce ?: bin2hex(random_bytes(16));
        $idempotencyKey = $idempotencyKey ?: bin2hex(random_bytes(16));
        $canonical = self::canonicalString($method, $path, $query, $timestamp, $nonce, $body);
        $ok = openssl_sign($canonical, $signature, $privateKeyPem, OPENSSL_ALGO_SHA256);
        if (!$ok) {
            throw new RuntimeException('Unable to sign CPay request');
        }

        return [
            'X-CPay-Merchant-Number' => $merchantNumber,
            'X-CPay-Signature-Version' => 'v2',
            'X-CPay-Timestamp' => $timestamp,
            'X-CPay-Nonce' => $nonce,
            'X-CPay-Signature' => base64_encode($signature),
            'X-CPay-Idempotency-Key' => $idempotencyKey,
        ];
    }
}
