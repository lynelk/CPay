import json
from urllib.parse import urlencode

import requests

from cpay_signing import sign_request


class CPayClient:
    def __init__(self, base_url: str, merchant_number: str, private_key_pem: str, session=None):
        if not base_url:
            raise ValueError("base_url is required")
        if not merchant_number:
            raise ValueError("merchant_number is required")
        if not private_key_pem:
            raise ValueError("private_key_pem is required")
        self.base_url = base_url.rstrip("/")
        self.merchant_number = merchant_number
        self.private_key_pem = private_key_pem
        self.session = session or requests.Session()

    def collect(self, request: dict):
        return self._post("/api/v2/payments/collect", request)

    def payout(self, request: dict):
        return self._post("/api/v2/payments/payout", request)

    def validate_account(self, request: dict):
        return self._post("/api/v2/accounts/validate", request)

    def create_payment_link(self, request: dict):
        return self._post("/api/v2/payment-links", request)

    def statements(self, start_date: str, end_date: str, format: str = "json", limit: int = None):
        query = {
            "merchantNumber": self.merchant_number,
            "startDate": start_date,
            "endDate": end_date,
            "format": format,
        }
        if limit is not None:
            query["limit"] = limit
        return self._get("/api/v2/statements", query)

    def _post(self, path: str, request: dict):
        payload = {"merchantNumber": self.merchant_number, **(request or {})}
        body = json.dumps(payload, separators=(",", ":"))
        headers = {
            "Content-Type": "application/json",
            **sign_request(self.merchant_number, self.private_key_pem, "POST", path, body=body),
        }
        return self._send("POST", path, headers=headers, data=body)

    def _get(self, path: str, query: dict):
        headers = sign_request(self.merchant_number, self.private_key_pem, "GET", path, query=query)
        return self._send("GET", f"{path}?{urlencode(query)}", headers=headers)

    def _send(self, method: str, path_and_query: str, **kwargs):
        response = self.session.request(method, f"{self.base_url}{path_and_query}", **kwargs)
        try:
            payload = response.json()
        except ValueError:
            payload = {"message": response.text}
        if response.status_code >= 400:
            raise CPayError(response.status_code, payload)
        return payload


class CPayError(Exception):
    def __init__(self, status_code: int, payload: dict):
        super().__init__(payload.get("message") or f"CPay request failed with {status_code}")
        self.status_code = status_code
        self.payload = payload
