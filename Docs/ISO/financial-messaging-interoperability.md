# Financial Messaging and Identifier Interoperability

## 1. Purpose

This policy defines the engineering boundary for ISO 20022, ISO 8583 and ISO 9362 within CPay. It avoids a dangerous category error: CPay's internal payment domain does not become ISO 20022 or ISO 8583 simply because those standards exist. Standards are applied at external interfaces whose counterparty/network profile requires them.

## 2. Canonical internal model

CPay maintains a provider-neutral internal payment/ledger model. External financial messages are translated through dedicated adapters. The canonical model must preserve sufficient identifiers, amounts, currency, parties, timestamps, status, provider/network references, purpose/remittance information and reconciliation references to allow deterministic translation and audit.

Adapter translation must not silently discard data required for downstream settlement, regulatory reporting, reconciliation or investigation.

## 3. ISO 20022 boundary

For an interface using ISO 20022, the integration profile records:

- business area and message type(s), such as a specific `pacs`, `camt` or other registered definition where actually contracted;
- message-definition/schema version;
- usage guideline or market practice profile;
- business application header requirements where applicable;
- sender/receiver identifiers including BIC/other IDs where required;
- transport, signing/encryption and certificate requirements;
- character/length/semantic restrictions imposed by the counterparty;
- acknowledgement/status/error message mapping;
- idempotency/business-message identifiers and replay policy;
- reconciliation and settlement mapping;
- certification evidence and production effective date.

### XML/security requirements

XML processing must disable external entities and DTD resolution unless a controlled profile explicitly requires a safe alternative. Schema files come from a controlled licensed/authorized source, are versioned by integration profile, and are never fetched dynamically from an untrusted location during transaction processing.

Schema validation alone is insufficient. Semantic/usage validation must enforce the counterparty's market-practice rules.

## 4. ISO 8583 boundary

ISO 8583 connections are network/profile-specific. A production adapter requires a controlled data dictionary or packager profile from the connected acquirer/issuer/switch/network and records:

- ISO 8583 edition/profile;
- MTIs supported;
- bitmap/data-element definitions, lengths, encoding and field-specific semantics;
- network management, reversal, advice and timeout/retry behavior;
- response-code mapping;
- key/MAC/PIN/security requirements;
- transport framing;
- reconciliation/settlement identifiers;
- test/certification evidence.

Generic code must not guess network-specific field meanings beyond the profile.

### Sensitive field handling

ISO 8583/card-oriented integrations can contain highly sensitive information. CPay's generic ISO 8583 boundary therefore:

- validates MTI/field-number structure;
- separates message parsing from business processing;
- redacts PAN/track/PIN/security-sensitive fields before diagnostic logging;
- never persists PIN blocks, CVV/CVC or full track data in normal application tables/logs;
- requires approved cryptographic/HSM controls where the connected profile handles keys, MACs or PIN operations;
- applies PCI DSS or scheme obligations when the actual processing scope makes them applicable.

## 5. BIC / ISO 9362

`BicValidator` provides syntactic validation and normalization for 8- and 11-character BIC forms. Syntactic validity does not establish that a BIC was assigned, is currently active, belongs to the claimed institution, or is valid for a specific service.

Production onboarding/routing that relies on BIC identity must verify the value against an authoritative licensed directory, counterparty source or provider validation service and retain the verification reference/date.

BIC should be stored separately from free-form bank names and must not be treated as an individual-person identifier.

## 6. Message envelope and lineage

Every standards-based adapter should preserve:

- `standard` and profile/version;
- message type/MTI;
- CPay correlation/reference;
- network/business message identifier;
- sender/receiver identifiers;
- received/sent timestamp;
- normalized status/result;
- immutable hash or controlled archive reference where retention is legally/contractually required;
- reconciliation linkage.

Logs use masked/normalized representations rather than raw message dumps by default.

## 7. Translation controls

Mappings between CPay and external message formats are treated as financial controls. Changes require:

- peer/domain review;
- positive and negative contract tests;
- precision/currency/amount tests;
- status/error/reversal tests;
- sensitive-data logging tests;
- backward compatibility or coordinated cutover;
- provider/network certification where required;
- reconciliation validation before production release.

## 8. ISO 20022 versus ISO 8583

The two standards are not interchangeable drop-in encodings. An adapter may bridge them only when there is a documented business mapping and loss/ambiguity analysis. The mapping record identifies fields without equivalent semantics and how they are handled.

## 9. Provider APIs that use neither standard

MTN, Airtel, Safaricom or other mobile-money REST/OpenAPI interfaces remain governed by their actual provider contract. They are not relabelled ISO 20022 or ISO 8583. Their payment data still maps to CPay's canonical domain and follows the same security, audit, idempotency and reconciliation controls.

## 10. Conformance evidence

A production interface may be described as conforming to an ISO message standard only when:

- its exact profile/version is identified;
- tests cover required messages/fields and failure paths;
- external/provider/network certification is complete where required;
- unresolved deviations are documented and approved;
- the deployed configuration/profile matches the tested revision.

CI can verify code structure and local test vectors. It cannot replace an external network certification programme.
