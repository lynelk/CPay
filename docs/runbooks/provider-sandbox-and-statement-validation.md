# Provider Sandbox and Statement Validation Runbook

## Purpose

This runbook explains how to validate provider adapters and provider statement files before enabling production traffic.

## Provider coverage

The sandbox workflow should be run for each channel:

- MTN MoMo
- Airtel Money
- Airtel OpenAPI
- Safaricom M-Pesa

## Sandbox execution

Use the provider sandbox endpoint to run adapter-level validation for a channel. Each run records provider code, channel code, scenario, status, request summary, and response summary.

Recommended scenarios:

- collect accepted
- payout accepted
- invalid account rejected
- duplicate reference rejected
- timeout simulated
- provider unavailable simulated

## Statement-file validation

Use the statement validation endpoint before importing files. Validation should confirm:

- provider reference is present
- amount is present
- currency is present
- duplicate provider references are detected
- row counts match expectations

## Required sample files

Before production, collect real sample files from each provider and store anonymized fixtures for regression testing. Include positive and negative samples for every provider format.

## Production gate

Do not enable production settlement reconciliation for a provider until sandbox validation and statement validation both pass with real provider samples.
