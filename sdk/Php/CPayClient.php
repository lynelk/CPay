<?php

require_once __DIR__ . '/CPaySigning.php';

final class CPayClient
{
    private string $baseUrl;
    private string $merchantNumber;
    private string $privateKeyPem;

    public function __construct(string $baseUrl, string $merchantNumber, string $privateKeyPem)
    {
        if ($baseUrl === '') {
            throw new InvalidArgumentException('baseUrl is required');
        }
        if ($merchantNumber === '') {
            throw new InvalidArgumentException('merchantNumber is required');
        }
        if ($privateKeyPem === '') {
            throw new InvalidArgumentException('privateKeyPem is required');
        }
        $this->baseUrl = rtrim($baseUrl, '/');
        $this->merchantNumber = $merchantNumber;
        $this->privateKeyPem = $privateKeyPem;
    }

    public function collect(array $request): array
    {
        return $this->post('/api/v2/payments/collect', $request);
    }

    public function payout(array $request): array
    {
        return $this->post('/api/v2/payments/payout', $request);
    }

    public function validateAccount(array $request): array
    {
        return $this->post('/api/v2/accounts/validate', $request);
    }

    public function createPaymentLink(array $request): array
    {
        return $this->post('/api/v2/payment-links', $request);
    }

    public function statements(string $startDate, string $endDate, string $format = 'json', ?int $limit = null): array
    {
        $query = [
            'merchantNumber' => $this->merchantNumber,
            'startDate' => $startDate,
            'endDate' => $endDate,
            'format' => $format,
        ];
        if ($limit !== null) {
            $query['limit'] = $limit;
        }
        return $this->get('/api/v2/statements', $query);
    }

    private function post(string $path, array $request): array
    {
        $payload = array_merge(['merchantNumber' => $this->merchantNumber], $request);
        $body = json_encode($payload, JSON_UNESCAPED_SLASHES);
        $headers = CPaySigning::signRequest($this->merchantNumber, $this->privateKeyPem, 'POST', $path, [], $body);
        $headers['Content-Type'] = 'application/json';
        return $this->send('POST', $path, $headers, $body);
    }

    private function get(string $path, array $query): array
    {
        $headers = CPaySigning::signRequest($this->merchantNumber, $this->privateKeyPem, 'GET', $path, $query);
        return $this->send('GET', $path . '?' . http_build_query($query), $headers, '');
    }

    private function send(string $method, string $pathAndQuery, array $headers, string $body): array
    {
        $curl = curl_init($this->baseUrl . $pathAndQuery);
        $headerLines = [];
        foreach ($headers as $name => $value) {
            $headerLines[] = $name . ': ' . $value;
        }
        curl_setopt_array($curl, [
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER => $headerLines,
            CURLOPT_POSTFIELDS => $body,
        ]);
        $responseBody = curl_exec($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_HTTP_CODE);
        if ($responseBody === false) {
            $message = curl_error($curl);
            curl_close($curl);
            throw new RuntimeException('CPay request failed: ' . $message);
        }
        curl_close($curl);
        $payload = json_decode($responseBody, true) ?: ['message' => $responseBody];
        if ($status >= 400) {
            throw new RuntimeException($payload['message'] ?? ('CPay request failed with ' . $status));
        }
        return $payload;
    }
}
