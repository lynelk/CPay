-- Execution automation layer for the P1-P4 production-maturity foundations.
-- This migration intentionally keeps provider-specific contracts configurable so CPay can add
-- screening vendors, payout rails, settlement posting strategies and validation checks without
-- hardcoding one compliance or banking partner into the core model.

create table if not exists screening_provider_configs (
    id bigserial primary key,
    provider_code varchar(64) not null unique,
    display_name varchar(160) not null,
    environment varchar(32) not null default 'SANDBOX',
    base_url varchar(512),
    auth_mode varchar(64) not null default 'NONE',
    enabled boolean not null default false,
    supports_sanctions boolean not null default false,
    supports_pep boolean not null default false,
    supports_document_verification boolean not null default false,
    supports_beneficiary_screening boolean not null default false,
    supports_merchant_screening boolean not null default false,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table if not exists screening_provider_requests (
    id bigserial primary key,
    provider_code varchar(64) not null,
    subject_type varchar(64) not null,
    subject_reference varchar(128) not null,
    screening_type varchar(64) not null,
    request_status varchar(64) not null default 'CREATED',
    risk_level varchar(32),
    match_count integer not null default 0,
    external_reference varchar(160),
    request_payload text,
    response_payload text,
    requested_by varchar(160),
    requested_at timestamp not null default current_timestamp,
    completed_at timestamp,
    constraint fk_screening_request_provider
        foreign key (provider_code) references screening_provider_configs(provider_code)
);

create index if not exists idx_screening_provider_requests_subject
    on screening_provider_requests(subject_type, subject_reference);
create index if not exists idx_screening_provider_requests_status
    on screening_provider_requests(request_status, risk_level);

create table if not exists cross_border_payout_rail_dispatches (
    id bigserial primary key,
    transfer_id bigint not null,
    merchant_id bigint,
    corridor_code varchar(64) not null,
    route_code varchar(64) not null,
    payout_channel varchar(64) not null,
    payout_country varchar(3) not null,
    payout_currency varchar(3) not null,
    beneficiary_id bigint,
    beneficiary_instrument_id bigint,
    source_amount numeric(19, 4),
    destination_amount numeric(19, 4),
    provider_reference varchar(160),
    merchant_reference varchar(160),
    dispatch_status varchar(64) not null default 'CREATED',
    idempotency_key varchar(160) not null,
    dispatch_payload text,
    response_payload text,
    error_code varchar(96),
    error_message text,
    requested_by varchar(160),
    created_at timestamp not null default current_timestamp,
    dispatched_at timestamp,
    completed_at timestamp
);

create unique index if not exists uq_cross_border_payout_dispatch_idempotency
    on cross_border_payout_rail_dispatches(idempotency_key);
create index if not exists idx_cross_border_payout_dispatch_transfer
    on cross_border_payout_rail_dispatches(transfer_id, dispatch_status);

create table if not exists settlement_posting_runs (
    id bigserial primary key,
    settlement_batch_id bigint not null,
    settlement_batch_type varchar(64) not null default 'FINANCE',
    run_status varchar(64) not null default 'CREATED',
    posting_strategy varchar(64) not null default 'DOUBLE_ENTRY_LEDGER',
    currency varchar(3),
    expected_total numeric(19, 4),
    posted_total numeric(19, 4) not null default 0,
    entry_count integer not null default 0,
    variance_amount numeric(19, 4) not null default 0,
    requested_by varchar(160),
    approved_by varchar(160),
    posted_by varchar(160),
    failure_reason text,
    created_at timestamp not null default current_timestamp,
    posted_at timestamp,
    closed_at timestamp
);

create index if not exists idx_settlement_posting_runs_batch
    on settlement_posting_runs(settlement_batch_type, settlement_batch_id, run_status);

create table if not exists settlement_posting_entries (
    id bigserial primary key,
    posting_run_id bigint not null,
    settlement_item_id bigint,
    account_code varchar(96) not null,
    entry_side varchar(16) not null check (entry_side in ('DEBIT', 'CREDIT')),
    amount numeric(19, 4) not null,
    currency varchar(3) not null,
    reference varchar(160),
    memo text,
    created_at timestamp not null default current_timestamp,
    constraint fk_settlement_posting_entries_run
        foreign key (posting_run_id) references settlement_posting_runs(id)
);

create index if not exists idx_settlement_posting_entries_run
    on settlement_posting_entries(posting_run_id);

create table if not exists production_maturity_validation_runs (
    id bigserial primary key,
    run_type varchar(64) not null,
    run_status varchar(64) not null default 'CREATED',
    source_ref varchar(160),
    checked_by varchar(160),
    summary text,
    started_at timestamp not null default current_timestamp,
    completed_at timestamp
);

create table if not exists production_maturity_validation_results (
    id bigserial primary key,
    run_id bigint not null,
    check_code varchar(128) not null,
    check_name varchar(240) not null,
    check_status varchar(64) not null,
    severity varchar(32) not null default 'MEDIUM',
    details text,
    evidence_ref varchar(256),
    created_at timestamp not null default current_timestamp,
    constraint fk_production_maturity_validation_results_run
        foreign key (run_id) references production_maturity_validation_runs(id)
);

create index if not exists idx_production_maturity_validation_results_run
    on production_maturity_validation_results(run_id, check_status, severity);
