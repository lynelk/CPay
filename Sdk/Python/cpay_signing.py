import base64
import hashlib
import uuid
from datetime import datetime, timezone
from urllib.parse import quote

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding


def sha256_hex(value: str) -> str:
    return hashlib.sha256((value or "").encode("utf-8")).hexdigest()


def canonical_query(query=None) -> str:
    if not query:
        return ""
    items = query.items() if hasattr(query, "items") else query
    return "&".join(
        f"{quote(str(key), safe='')}={quote(str(value), safe='')}"
        for key, value in sorted(items, key=lambda item: str(item[0]))
        if value is not None
    )


def canonical_string(method: str, path: str, query, timestamp: str, nonce: str, body: str = "") -> str:
    return "\n".join([
        (method or "GET").upper(),
        path or "/",
        canonical_query(query),
        timestamp,
        nonce,
        sha256_hex(body or ""),
    ])


def sign_request(
    merchant_number: str,
    private_key_pem: str,
    method: str,
    path: str,
    query=None,
    body: str = "",
    timestamp: str = None,
    nonce: str = None,
    idempotency_key: str = None,
):
    if not merchant_number:
        raise ValueError("merchant_number is required")
    if not private_key_pem:
        raise ValueError("private_key_pem is required")

    timestamp = timestamp or datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    nonce = nonce or str(uuid.uuid4())
    idempotency_key = idempotency_key or str(uuid.uuid4())
    canonical = canonical_string(method, path, query, timestamp, nonce, body)
    private_key = serialization.load_pem_private_key(private_key_pem.encode("utf-8"), password=None)
    signature = private_key.sign(canonical.encode("utf-8"), padding.PKCS1v15(), hashes.SHA256())

    return {
        "X-CPay-Merchant-Number": merchant_number,
        "X-CPay-Signature-Version": "v2",
        "X-CPay-Timestamp": timestamp,
        "X-CPay-Nonce": nonce,
        "X-CPay-Signature": base64.b64encode(signature).decode("ascii"),
        "X-CPay-Idempotency-Key": idempotency_key,
    }
