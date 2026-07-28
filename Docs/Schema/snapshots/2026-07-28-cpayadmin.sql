-- CPay schema snapshot — reconstructed from Flyway migrations, not a live mysqldump.
--
-- Generation method: this environment has no live migrated MySQL database to dump from, so this
-- file was hand-reconstructed by reading every migration under
-- InitializrSpringbootProjectFresh/src/main/resources/db/migration (V1 through V18, as of
-- 2026-07-28) in order and applying each one's CREATE TABLE / ALTER TABLE statements to the
-- running schema. It supersedes Docs/Schema/snapshots/2026-07-16-cpayadmin.sql, which was a real
-- mysqldump taken on 2026-07-16 against a database that already had V1, V2, and V5 applied but
-- predated V3's column widening and everything from V7 onward — it is missing all 41 tables added
-- from V7 through V18 (ledger, risk, compliance, FX/treasury, payment links/checkout, webhooks,
-- sandbox/environment controls, channel routing, fee schedules, and the payout compensation saga).
--
-- Next time a real database is available, prefer regenerating with the mysqldump recipe in
-- Docs/Schema/Readme.md and replacing this file; that will catch any drift between the migrations
-- as authored and what actually got applied (e.g. hand-run DDL outside Flyway).
--
-- Migrations folded into this snapshot, in order:
--   V1  baseline_schema                          - the original legacy baseline (19 tables): accounts_register,
--                                                   admin_privileges, admins, audit_trail, beneficiaries,
--                                                   charging_details, db_changes, merchant_admin_privileges,
--                                                   merchant_admins, merchant_batch_transactions_log,
--                                                   merchant_settings, merchant_sms, merchant_statement,
--                                                   merchant_transactions_log, merchants, merchants_audit_trail,
--                                                   settings, SPRING_SESSION, SPRING_SESSION_ATTRIBUTES
--   V2  operational_tables                        - the modernization migration: adds columns to
--                                                   merchant_transactions_log (callback_status,
--                                                   callback_retry_count, callback_next_retry, currency,
--                                                   network_reference), merchants (hmac_secret), and
--                                                   merchant_statement (currency), plus 24 new tables
--                                                   (cpay_idempotency_keys, cpay_request_nonces,
--                                                   api_rate_limits, callback_tasks, callback_task_claims,
--                                                   callback_delivery_signatures, merchant_callback_secrets,
--                                                   merchant_channel_credentials, merchant_channel_audit_events,
--                                                   merchant_channel_balances, balance_ledger_events,
--                                                   normalized_balance_backfill_runs, reconciliation_imports,
--                                                   reconciliation_records, reconciliation_reviews,
--                                                   reconciliation_daily_closes, reconciliation_settlement_batches,
--                                                   provider_statement_validation_runs, provider_sandbox_runs,
--                                                   provider_endpoint_runs, operations_alerts,
--                                                   operating_control_events, admin_permissions,
--                                                   admin_audit_events). Written with IF NOT EXISTS / an
--                                                   add-column-if-missing procedure so it is safe to run
--                                                   both as a fresh-install step and as a legacy-DB upgrade.
--   V3  added_currency_to_merchant_transactions_log_ - widens merchant_transactions_log.currency (added as
--                                                   VARCHAR(10) by V2) to VARCHAR(25)
--   V4  seed_customer_login_appearance_settings   - `settings` data seed only, no DDL
--   V5  add_feature_flags                         - adds the `feature_flags` table (net-new, not in V1/V2)
--   V6  assign_login_portal_images                - `settings` data seed/update only, no DDL
--   V7  audit_roadmap_production_features         - adds ledger_*, risk_rules, compliance_blocklist,
--                                                   risk_decisions, payment_links, hosted_checkout_attempts,
--                                                   admin_mfa_totp, provider_tokens, settlement_* (14 tables)
--   V8  ensure_admin_mfa_totp                     - re-declares `admin_mfa_totp` (already in V7), no-op
--   V9  compliance_and_provider_evidence           - adds compliance_profiles, compliance_cases,
--                                                   compliance_case_notes, risk_decision_scores,
--                                                   provider_certification_requirements/evidence (6 tables)
--   V10 allow_pending_merchant_signup_status       - widens merchants.status enum with PENDING_APPROVAL
--   V11 configure_airtel_openapi_v2                - `settings` / `merchant_settings` data seed only, no DDL
--   V12 production_ledger_risk_fx_controls         - adds ledger_account_balances, compliance_watchlist_entries,
--                                                   compliance_screening_hits, beneficial_owners,
--                                                   merchant_kyc_documents, fx_rates, fx_quotes,
--                                                   treasury_positions, cross_border_corridors,
--                                                   payment_intents, transfer_intents, merchant_mfa_totp (12 tables)
--   V13 password_reset_token_hardening              - adds password_reset_tokens
--   V14 merchant_webhooks_and_notifications         - adds merchant_webhook_endpoints,
--                                                   merchant_webhook_deliveries,
--                                                   merchant_notification_preferences (3 tables)
--   V15 sandbox_environment_controls                - adds merchant_number/environment columns + index to
--                                                   provider_endpoint_runs; adds merchant_environment_preferences;
--                                                   `settings` data seed
--   V16 channel_routing_prefixes                    - adds channel_routing_prefixes (+ seed data)
--   V17 fee_schedules                               - adds fee_schedules
--   V18 payout_compensation_saga                    - adds payout_compensation_sagas
--
-- Net result: 19 tables (V1) + 24 tables (V2) + 1 table (V5, feature_flags) + 40 tables added
-- V7-V18 = 84 tables total. (V3/V8/V10/V15 modify existing tables rather than adding new ones;
-- V4/V6/V11 are data-only seeds with no DDL.)
-- AUTO_INCREMENT counters are intentionally omitted (no live data to reflect); see the
-- normalization step in Docs/Schema/Readme.md for why real dumps strip these too.

SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS=0;

--
-- Table structure for table `accounts_register`
--

CREATE TABLE IF NOT EXISTS `accounts_register` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `account` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `first_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `last_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `address` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `dob` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `account_type` enum('MSISDN','EMAIL') CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT 'MSISDN',
  `provided_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `merchant_name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `merchant_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `account` (`account`),
  KEY `merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;

--
-- Table structure for table `admin_audit_events`
--

CREATE TABLE IF NOT EXISTS `admin_audit_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `actor` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `permission_code` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_summary` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `admin_mfa_totp` (added V7)
--

CREATE TABLE IF NOT EXISTS `admin_mfa_totp` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `admin_id` BIGINT UNSIGNED NOT NULL,
  `secret_value` TEXT NOT NULL,
  `enabled_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `verified_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_mfa_totp_admin` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `admin_permissions`
--

CREATE TABLE IF NOT EXISTS `admin_permissions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `role_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission_code` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_permission` (`role_name`,`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `admin_privileges`
--

CREATE TABLE IF NOT EXISTS `admin_privileges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` bigint unsigned DEFAULT NULL,
  `privilege` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_admin_priv` (`admin_id`,`privilege`),
  CONSTRAINT `admin_privileges_ibfk_1` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `admins`
--

CREATE TABLE IF NOT EXISTS `admins` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL DEFAULT '',
  `email` varchar(255) NOT NULL DEFAULT '',
  `phone` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` enum('ACTIVE','SUSPENDED','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `password` varchar(255) NOT NULL DEFAULT '',
  `email_verification_code` varchar(255) NOT NULL DEFAULT '',
  `email_verification_sent_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_user` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `api_rate_limits`
--

CREATE TABLE IF NOT EXISTS `api_rate_limits` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `rate_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `window_start` timestamp NOT NULL,
  `request_count` int NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_rate_limit_window` (`rate_key`,`window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `audit_trail`
--

CREATE TABLE IF NOT EXISTS `audit_trail` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_name` varchar(255) NOT NULL DEFAULT '',
  `user_id` varchar(255) NOT NULL DEFAULT '',
  `action` text,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `balance_ledger_events`
--

CREATE TABLE IF NOT EXISTS `balance_ledger_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gateway_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount_delta` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `pending_delta` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `ledger_delta` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_balance_ledger_source` (`source_type`,`source_reference`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `beneficial_owners` (added V12)
--

CREATE TABLE IF NOT EXISTS `beneficial_owners` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `full_name` VARCHAR(255) NOT NULL,
  `id_type` VARCHAR(80) NULL,
  `id_value_hash` VARCHAR(64) NULL,
  `ownership_percent` DECIMAL(7,4) NULL,
  `screening_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_beneficial_owner_merchant` (`merchant_id`, `screening_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `beneficiaries`
--

CREATE TABLE IF NOT EXISTS `beneficiaries` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `batch_id` bigint unsigned DEFAULT NULL,
  `name` varchar(255) NOT NULL DEFAULT '',
  `account` varchar(255) NOT NULL DEFAULT '',
  `status` varchar(255) NOT NULL DEFAULT '',
  `amount` double NOT NULL DEFAULT '0',
  `account_type` varchar(255) NOT NULL DEFAULT '',
  `reason` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UNIQUE_PAYMENT_ACCOUNT` (`batch_id`,`account`),
  CONSTRAINT `beneficiaries_ibfk_1` FOREIGN KEY (`batch_id`) REFERENCES `merchant_batch_transactions_log` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `callback_delivery_signatures`
--

CREATE TABLE IF NOT EXISTS `callback_delivery_signatures` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `callback_task_id` bigint unsigned NOT NULL,
  `signature_algorithm` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `signature_value` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `nonce` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_callback_signature_task` (`callback_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `callback_task_claims`
--

CREATE TABLE IF NOT EXISTS `callback_task_claims` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id` bigint unsigned NOT NULL,
  `worker_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `claim_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_task_active_claim` (`task_id`,`claim_status`),
  KEY `idx_callback_claim_worker` (`worker_name`,`claim_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `callback_tasks`
--

CREATE TABLE IF NOT EXISTS `callback_tasks` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `transaction_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reference_value` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_url` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_body` json DEFAULT NULL,
  `task_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT '0',
  `attempt_limit` int NOT NULL DEFAULT '5',
  `next_run_at` timestamp NULL DEFAULT NULL,
  `last_run_at` timestamp NULL DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_task_ref` (`merchant_id`,`transaction_id`,`reference_value`),
  KEY `idx_callback_due` (`task_status`,`next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `channel_routing_prefixes` (added V16)
--

CREATE TABLE IF NOT EXISTS `channel_routing_prefixes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `gateway_id` VARCHAR(100) NOT NULL,
  `msisdn_prefix` VARCHAR(20) NOT NULL,
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_routing_prefix` (`gateway_id`, `msisdn_prefix`),
  KEY `idx_channel_routing_active` (`active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `charging_details`
--

CREATE TABLE IF NOT EXISTS `charging_details` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `gateway_id` varchar(255) NOT NULL DEFAULT '',
  `service` enum('PAYIN','PAYOUT') NOT NULL DEFAULT 'PAYIN',
  `amount` double NOT NULL DEFAULT '0',
  `charging_method` enum('PERCENTAGE','FLAT_FEE','TIER') NOT NULL DEFAULT 'PERCENTAGE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_charge` (`gateway_id`,`service`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `compliance_blocklist` (added V7)
--

CREATE TABLE IF NOT EXISTS `compliance_blocklist` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `value_type` VARCHAR(50) NOT NULL,
  `value_hash` VARCHAR(64) NOT NULL,
  `reason` TEXT NULL,
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_blocklist_value` (`value_type`, `value_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `compliance_case_notes` (added V9)
--

CREATE TABLE IF NOT EXISTS `compliance_case_notes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `case_id` BIGINT UNSIGNED NOT NULL,
  `note_type` VARCHAR(60) NOT NULL DEFAULT 'SYSTEM',
  `note_text` TEXT NOT NULL,
  `created_by` VARCHAR(255) NOT NULL DEFAULT 'system',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_compliance_case_note_case` (`case_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `compliance_cases` (added V9)
--

CREATE TABLE IF NOT EXISTS `compliance_cases` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `case_reference` VARCHAR(150) NOT NULL,
  `case_type` VARCHAR(80) NOT NULL,
  `entity_type` VARCHAR(40) NOT NULL,
  `entity_id` BIGINT UNSIGNED NOT NULL,
  `source_reference` VARCHAR(255) NULL,
  `severity` VARCHAR(40) NOT NULL DEFAULT 'MEDIUM',
  `case_status` VARCHAR(40) NOT NULL DEFAULT 'OPEN',
  `assigned_to` VARCHAR(255) NULL,
  `decision` VARCHAR(40) NULL,
  `decision_reason` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `closed_at` TIMESTAMP NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_case_reference` (`case_reference`),
  KEY `idx_compliance_case_entity` (`entity_type`, `entity_id`, `case_status`),
  KEY `idx_compliance_case_queue` (`case_status`, `severity`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `compliance_profiles` (added V9)
--

CREATE TABLE IF NOT EXISTS `compliance_profiles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `entity_type` VARCHAR(40) NOT NULL,
  `entity_id` BIGINT UNSIGNED NOT NULL,
  `profile_type` VARCHAR(80) NOT NULL,
  `tier` VARCHAR(40) NOT NULL DEFAULT 'STANDARD',
  `status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `risk_rating` VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
  `required_documents_json` TEXT NULL,
  `decision_reason` TEXT NULL,
  `verified_by` VARCHAR(255) NULL,
  `verified_at` TIMESTAMP NULL,
  `expires_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_profile_entity` (`entity_type`, `entity_id`, `profile_type`),
  KEY `idx_compliance_profile_status` (`status`, `risk_rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `compliance_screening_hits` (added V12)
--

CREATE TABLE IF NOT EXISTS `compliance_screening_hits` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `request_reference` VARCHAR(255) NOT NULL,
  `direction` VARCHAR(30) NOT NULL,
  `screened_type` VARCHAR(50) NOT NULL,
  `screened_value_hash` VARCHAR(64) NOT NULL,
  `watchlist_entry_id` BIGINT UNSIGNED NOT NULL,
  `decision` VARCHAR(40) NOT NULL,
  `hit_summary` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_screening_hit_reference` (`merchant_id`, `request_reference`, `created_at`),
  KEY `idx_screening_hit_watchlist` (`watchlist_entry_id`, `decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `compliance_watchlist_entries` (added V12)
--

CREATE TABLE IF NOT EXISTS `compliance_watchlist_entries` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `list_name` VARCHAR(120) NOT NULL,
  `entry_type` VARCHAR(50) NOT NULL,
  `entry_value_hash` VARCHAR(64) NOT NULL,
  `entry_label` VARCHAR(255) NULL,
  `risk_rating` VARCHAR(40) NOT NULL DEFAULT 'HIGH',
  `action` VARCHAR(40) NOT NULL DEFAULT 'REVIEW',
  `source_reference` VARCHAR(255) NULL,
  `active_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_watchlist_entry` (`list_name`, `entry_type`, `entry_value_hash`),
  KEY `idx_watchlist_lookup` (`entry_type`, `entry_value_hash`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `cpay_idempotency_keys`
--

CREATE TABLE IF NOT EXISTS `cpay_idempotency_keys` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_number` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `response_body` json NOT NULL,
  `status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpay_idempotency` (`merchant_number`,`idempotency_key`),
  KEY `idx_cpay_idempotency_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `cpay_request_nonces`
--

CREATE TABLE IF NOT EXISTS `cpay_request_nonces` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_number` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nonce_value` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` timestamp NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpay_request_nonce` (`merchant_number`,`nonce_value`),
  KEY `idx_cpay_request_nonce_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `cross_border_corridors` (added V12)
--

CREATE TABLE IF NOT EXISTS `cross_border_corridors` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `source_country` VARCHAR(10) NOT NULL,
  `target_country` VARCHAR(10) NOT NULL,
  `source_currency` VARCHAR(10) NOT NULL,
  `target_currency` VARCHAR(10) NOT NULL,
  `provider_code` VARCHAR(100) NOT NULL,
  `corridor_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `daily_limit` DECIMAL(19,4) NULL,
  `single_transfer_limit` DECIMAL(19,4) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cross_border_corridor` (`source_country`, `target_country`, `source_currency`, `target_currency`, `provider_code`),
  KEY `idx_cross_border_corridor_lookup` (`source_country`, `target_country`, `corridor_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `db_changes`
--

CREATE TABLE IF NOT EXISTS `db_changes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `query_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `sql_text` text CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci,
  `roll_back` text CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `query_id` (`query_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;

--
-- Table structure for table `fee_schedules` (added V17)
--

CREATE TABLE IF NOT EXISTS `fee_schedules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `gateway_id` VARCHAR(100) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NULL,
  `service` ENUM('PAYIN','PAYOUT') NOT NULL,
  `charge_type` ENUM('CUSTOMER_CHARGE','COST_OF_PAYMENT') NOT NULL,
  `charging_method` ENUM('PERCENTAGE','FLAT_FEE','TIER') NOT NULL DEFAULT 'PERCENTAGE',
  `amount` DECIMAL(18,4) NOT NULL,
  `effective_from` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `effective_to` TIMESTAMP NULL,
  `created_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fee_schedule_lookup` (`gateway_id`, `merchant_id`, `service`, `charge_type`, `effective_from`, `effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `feature_flags`
--

CREATE TABLE IF NOT EXISTS `feature_flags` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `flag_key` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feature_flags_key` (`flag_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `fx_quotes` (added V12)
--

CREATE TABLE IF NOT EXISTS `fx_quotes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `quote_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `source_currency` VARCHAR(10) NOT NULL,
  `target_currency` VARCHAR(10) NOT NULL,
  `source_amount` DECIMAL(19,4) NOT NULL,
  `target_amount` DECIMAL(19,4) NOT NULL,
  `rate` DECIMAL(24,10) NOT NULL,
  `quote_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` TIMESTAMP NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fx_quote_reference` (`quote_reference`),
  KEY `idx_fx_quote_merchant` (`merchant_id`, `quote_status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `fx_rates` (added V12)
--

CREATE TABLE IF NOT EXISTS `fx_rates` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `source_currency` VARCHAR(10) NOT NULL,
  `target_currency` VARCHAR(10) NOT NULL,
  `rate` DECIMAL(24,10) NOT NULL,
  `provider_code` VARCHAR(100) NOT NULL DEFAULT 'INTERNAL',
  `rate_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `valid_from` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `valid_until` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fx_rate_lookup` (`source_currency`, `target_currency`, `rate_status`, `valid_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `hosted_checkout_attempts` (added V7)
--

CREATE TABLE IF NOT EXISTS `hosted_checkout_attempts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `payment_link_id` BIGINT UNSIGNED NOT NULL,
  `payer_account` VARCHAR(255) NOT NULL,
  `channel_code` VARCHAR(100) NULL,
  `attempt_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `transaction_id` VARCHAR(255) NULL,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_checkout_attempt_link` (`payment_link_id`, `attempt_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `ledger_account_balances` (added V12)
--

CREATE TABLE IF NOT EXISTS `ledger_account_balances` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `account_id` BIGINT UNSIGNED NOT NULL,
  `account_code` VARCHAR(150) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `debit_total` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `credit_total` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `net_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `last_ledger_entry_id` BIGINT UNSIGNED NULL,
  `refreshed_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_account_balance` (`account_id`, `currency`),
  KEY `idx_ledger_account_balance_code` (`account_code`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `ledger_accounts` (added V7)
--

CREATE TABLE IF NOT EXISTS `ledger_accounts` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `account_code` VARCHAR(150) NOT NULL,
  `account_name` VARCHAR(255) NOT NULL,
  `account_type` VARCHAR(60) NOT NULL,
  `owner_type` VARCHAR(60) NOT NULL,
  `owner_id` BIGINT UNSIGNED NULL,
  `currency` VARCHAR(10) NOT NULL,
  `account_status` VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_account_code` (`account_code`),
  KEY `idx_ledger_account_owner` (`owner_type`, `owner_id`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `ledger_entries` (added V7)
--

CREATE TABLE IF NOT EXISTS `ledger_entries` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `ledger_transaction_id` BIGINT UNSIGNED NOT NULL,
  `account_id` BIGINT UNSIGNED NOT NULL,
  `entry_direction` VARCHAR(2) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `entry_memo` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ledger_entry_transaction` (`ledger_transaction_id`),
  KEY `idx_ledger_entry_account` (`account_id`, `currency`),
  CONSTRAINT `fk_ledger_entry_transaction` FOREIGN KEY (`ledger_transaction_id`) REFERENCES `ledger_transactions` (`id`),
  CONSTRAINT `fk_ledger_entry_account` FOREIGN KEY (`account_id`) REFERENCES `ledger_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `ledger_reservations` (added V7)
--

CREATE TABLE IF NOT EXISTS `ledger_reservations` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `reservation_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `source_reference` VARCHAR(255) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `reservation_status` VARCHAR(40) NOT NULL DEFAULT 'RESERVED',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_reservation_ref` (`reservation_reference`),
  KEY `idx_ledger_reservation_merchant` (`merchant_id`, `reservation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `ledger_transactions` (added V7)
--

CREATE TABLE IF NOT EXISTS `ledger_transactions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `transaction_reference` VARCHAR(255) NOT NULL,
  `source_type` VARCHAR(100) NOT NULL,
  `source_reference` VARCHAR(255) NOT NULL,
  `transaction_status` VARCHAR(40) NOT NULL DEFAULT 'POSTED',
  `description` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_transaction_ref` (`transaction_reference`),
  KEY `idx_ledger_transaction_source` (`source_type`, `source_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `ledger_trial_balance_runs` (added V7)
--

CREATE TABLE IF NOT EXISTS `ledger_trial_balance_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_date` DATE NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `total_debits` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `total_credits` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `balanced_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trial_balance_run` (`run_date`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_admin_privileges`
--

CREATE TABLE IF NOT EXISTS `merchant_admin_privileges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` bigint unsigned DEFAULT NULL,
  `privilege` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_admin_priv` (`admin_id`,`privilege`),
  CONSTRAINT `merchant_admin_privileges_ibfk_1` FOREIGN KEY (`admin_id`) REFERENCES `merchant_admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `merchant_admins`
--

CREATE TABLE IF NOT EXISTS `merchant_admins` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `name` varchar(255) NOT NULL DEFAULT '',
  `email` varchar(255) NOT NULL DEFAULT '',
  `phone` varchar(255) NOT NULL DEFAULT '',
  `password` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` enum('ACTIVE','SUSPENDED','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `email_verification_code` varchar(255) NOT NULL DEFAULT '',
  `email_verification_sent_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_merchant_user` (`merchant_id`,`email`),
  CONSTRAINT `merchant_admins_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `merchant_batch_transactions_log`
--

CREATE TABLE IF NOT EXISTS `merchant_batch_transactions_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `total_amount` double NOT NULL DEFAULT '0',
  `total_charges` double NOT NULL DEFAULT '0',
  `status` enum('PENDING','PROCESSING','PAUSED','DONE','STOPPED') DEFAULT 'PENDING',
  `tx_description` text,
  `batch_id` varchar(255) NOT NULL,
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `name` varchar(255) NOT NULL DEFAULT '',
  `created_by` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `batch_id` (`batch_id`),
  KEY `merchant_id` (`merchant_id`),
  CONSTRAINT `merchant_batch_transactions_log_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `merchant_callback_secrets`
--

CREATE TABLE IF NOT EXISTS `merchant_callback_secrets` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `secret_alias` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'default',
  `secret_value` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `active_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `rotated_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_callback_secret_merchant` (`merchant_id`,`active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_channel_audit_events`
--

CREATE TABLE IF NOT EXISTS `merchant_channel_audit_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `environment` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SANDBOX',
  `action` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_channel_audit_merchant` (`merchant_id`,`channel_code`,`environment`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_channel_balances`
--

CREATE TABLE IF NOT EXISTS `merchant_channel_balances` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gateway_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `available_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `ledger_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `pending_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_channel_balance` (`merchant_id`,`channel_code`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_channel_credentials`
--

CREATE TABLE IF NOT EXISTS `merchant_channel_credentials` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `environment` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SANDBOX',
  `display_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `credential_payload` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `credential_mask` json DEFAULT NULL,
  `status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CONFIGURED',
  `last_test_status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_test_message` text COLLATE utf8mb4_unicode_ci,
  `last_tested_at` timestamp NULL DEFAULT NULL,
  `submitted_for_approval_at` timestamp NULL DEFAULT NULL,
  `approved_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_at` timestamp NULL DEFAULT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_channel_credentials` (`merchant_id`,`channel_code`,`environment`),
  KEY `idx_merchant_channel_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_environment_preferences` (added V15)
--

CREATE TABLE IF NOT EXISTS `merchant_environment_preferences` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `merchant_user_id` BIGINT UNSIGNED NULL,
  `channel_code` VARCHAR(100) NOT NULL DEFAULT '*',
  `active_environment` VARCHAR(40) NOT NULL DEFAULT 'SANDBOX',
  `production_limit_enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `production_transaction_limit` INT NOT NULL DEFAULT 10,
  `updated_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_environment_preference` (`merchant_id`, `merchant_user_id`, `channel_code`),
  KEY `idx_merchant_environment_active` (`merchant_id`, `active_environment`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_kyc_documents` (added V12)
--

CREATE TABLE IF NOT EXISTS `merchant_kyc_documents` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `document_type` VARCHAR(100) NOT NULL,
  `storage_ref` TEXT NOT NULL,
  `document_hash` VARCHAR(64) NULL,
  `verification_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `verified_by` VARCHAR(255) NULL,
  `verified_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kyc_document_merchant` (`merchant_id`, `verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_mfa_totp` (added V12)
--

CREATE TABLE IF NOT EXISTS `merchant_mfa_totp` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_admin_id` BIGINT UNSIGNED NOT NULL,
  `secret_value` TEXT NOT NULL,
  `enabled_flag` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `verified_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_mfa_totp_admin` (`merchant_admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_notification_preferences` (added V14)
--

CREATE TABLE IF NOT EXISTS `merchant_notification_preferences` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(120) NOT NULL,
  `email_enabled` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `sms_enabled` VARCHAR(3) NOT NULL DEFAULT 'NO',
  `webhook_enabled` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `updated_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_notification_pref` (`merchant_id`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_settings`
--

CREATE TABLE IF NOT EXISTS `merchant_settings` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `label` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `name` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `setting_value` text CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci,
  `description` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `setting_group` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_merchant_setting_name` (`merchant_id`,`name`),
  CONSTRAINT `merchant_settings_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;

--
-- Table structure for table `merchant_sms`
--

CREATE TABLE IF NOT EXISTS `merchant_sms` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `charge` double NOT NULL DEFAULT '0',
  `cost` double NOT NULL DEFAULT '0',
  `total_recipients` int DEFAULT '0',
  `status` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `trace` mediumblob,
  `content` text CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci,
  `recipients` mediumblob,
  `gw_response` mediumblob,
  `smsgw` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `created_by` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `total_amount` double NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `merchant_id` (`merchant_id`),
  CONSTRAINT `merchant_sms_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;

--
-- Table structure for table `merchant_statement`
--

CREATE TABLE IF NOT EXISTS `merchant_statement` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `transactions_log_id` bigint unsigned DEFAULT NULL,
  `gateway_id` varchar(255) NOT NULL DEFAULT '',
  `description` text,
  `amount` double NOT NULL DEFAULT '0',
  `mtnmm_balance` double NOT NULL DEFAULT '0',
  `tx_type` enum('CR','DR') NOT NULL DEFAULT 'CR',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `airtelmm_balance` double NOT NULL DEFAULT '0',
  `narrative` varchar(255) NOT NULL DEFAULT '',
  `recorded_by` varchar(255) NOT NULL DEFAULT '',
  `sms_balance` double NOT NULL DEFAULT '0',
  `safaricom_balance` double NOT NULL DEFAULT '0',
  `currency` varchar(10) NOT NULL DEFAULT 'UGX',
  PRIMARY KEY (`id`),
  KEY `transactions_log_id` (`transactions_log_id`),
  KEY `idx_ms_merchant_id` (`merchant_id`),
  CONSTRAINT `merchant_statement_ibfk_1` FOREIGN KEY (`transactions_log_id`) REFERENCES `merchant_transactions_log` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `merchant_transactions_log`
-- (currency widened from VARCHAR(10) to VARCHAR(25) by V3)
--

CREATE TABLE IF NOT EXISTS `merchant_transactions_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `gateway_id` varchar(255) NOT NULL DEFAULT '',
  `original_amount` double NOT NULL DEFAULT '0',
  `charges` double NOT NULL DEFAULT '0',
  `status` enum('SUCCESSFUL','FAILED','PENDING','UNDETERMINED') DEFAULT 'PENDING',
  `charging_method` varchar(255) DEFAULT NULL,
  `tx_request_trace` blob,
  `tx_update_trace` blob,
  `tx_description` text,
  `tx_merchant_description` text,
  `tx_unique_id` varchar(255) NOT NULL,
  `tx_gateway_ref` varchar(255) NOT NULL,
  `tx_merchant_ref` varchar(255) DEFAULT NULL,
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `payer_number` varchar(255) NOT NULL DEFAULT '',
  `tx_type` enum('PAYOUT','PAYIN') NOT NULL DEFAULT 'PAYIN',
  `merchant_batch_transactions_log_id` bigint unsigned DEFAULT NULL,
  `tx_cost` double NOT NULL DEFAULT '0',
  `callback_url` varchar(255) NOT NULL DEFAULT '',
  `callback_trace` text,
  `name` varchar(255) NOT NULL DEFAULT '',
  `account_type` varchar(255) NOT NULL DEFAULT 'phone',
  `beneficiary_id` bigint unsigned DEFAULT NULL,
  `originate_ip` varchar(255) NOT NULL DEFAULT '',
  `resolved_by` varchar(255) NOT NULL DEFAULT '',
  `safaricom_request_reference` varchar(255) NOT NULL DEFAULT '',
  `callback_status` varchar(50) NOT NULL DEFAULT 'PENDING',
  `callback_retry_count` int NOT NULL DEFAULT '0',
  `callback_next_retry` datetime DEFAULT NULL,
  `currency` varchar(25) NOT NULL DEFAULT 'UGX',
  `network_reference` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `tx_unique_id` (`tx_unique_id`),
  UNIQUE KEY `unique_merchant_id` (`merchant_id`,`tx_merchant_ref`),
  UNIQUE KEY `unique_merchant_tx` (`merchant_id`,`tx_merchant_ref`),
  KEY `merchant_batch_transactions_log_id` (`merchant_batch_transactions_log_id`),
  KEY `tx_merchant_ref` (`tx_merchant_ref`),
  KEY `beneficiary_id` (`beneficiary_id`),
  KEY `idx_mtl_callback_status` (`callback_status`),
  KEY `idx_mtl_merchant_status` (`merchant_id`,`status`),
  KEY `idx_mtl_network_ref` (`network_reference`),
  KEY `idx_mtl_merchant_ref` (`tx_merchant_ref`),
  CONSTRAINT `merchant_transactions_log_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE SET NULL,
  CONSTRAINT `merchant_transactions_log_ibfk_2` FOREIGN KEY (`merchant_batch_transactions_log_id`) REFERENCES `merchant_batch_transactions_log` (`id`) ON DELETE SET NULL,
  CONSTRAINT `merchant_transactions_log_ibfk_3` FOREIGN KEY (`beneficiary_id`) REFERENCES `beneficiaries` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `merchant_webhook_deliveries` (added V14)
--

CREATE TABLE IF NOT EXISTS `merchant_webhook_deliveries` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `endpoint_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(120) NOT NULL,
  `event_reference` VARCHAR(255) NOT NULL,
  `payload_json` TEXT NOT NULL,
  `delivery_status` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `attempt_count` INT NOT NULL DEFAULT 0,
  `next_attempt_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_http_status` INT NULL,
  `last_response_summary` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_webhook_delivery` (`endpoint_id`, `event_reference`),
  KEY `idx_merchant_webhook_delivery_due` (`delivery_status`, `next_attempt_at`),
  KEY `idx_merchant_webhook_delivery_merchant` (`merchant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchant_webhook_endpoints` (added V14)
--

CREATE TABLE IF NOT EXISTS `merchant_webhook_endpoints` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(120) NOT NULL,
  `endpoint_url` TEXT NOT NULL,
  `secret_hash` VARCHAR(64) NOT NULL,
  `secret_value` TEXT NOT NULL,
  `endpoint_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_webhook_event` (`merchant_id`, `event_type`),
  KEY `idx_merchant_webhook_status` (`endpoint_status`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `merchants`
-- (status enum widened with PENDING_APPROVAL by V10)
--

CREATE TABLE IF NOT EXISTS `merchants` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL DEFAULT '',
  `status` enum('ACTIVE','PENDING_APPROVAL','SUSPENDED','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `account_number` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(255) NOT NULL DEFAULT '',
  `account_type` enum('business','personal') NOT NULL DEFAULT 'personal',
  `public_key` blob,
  `private_key` blob,
  `allowed_apis` text,
  `short_name` varchar(255) NOT NULL DEFAULT '',
  `hmac_secret` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_id` (`account_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `merchants_audit_trail`
--

CREATE TABLE IF NOT EXISTS `merchants_audit_trail` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `user_name` varchar(255) NOT NULL DEFAULT '',
  `user_id` varchar(255) NOT NULL DEFAULT '',
  `action` text,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `normalized_balance_backfill_runs`
--

CREATE TABLE IF NOT EXISTS `normalized_balance_backfill_runs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `started_by` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `run_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RUNNING',
  `merchants_processed` int NOT NULL DEFAULT '0',
  `balances_written` int NOT NULL DEFAULT '0',
  `message` text COLLATE utf8mb4_unicode_ci,
  `started_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `operating_control_events`
--

CREATE TABLE IF NOT EXISTS `operating_control_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM',
  `event_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `reference_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reviewed_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_control_event_status` (`event_status`,`severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `operations_alerts`
--

CREATE TABLE IF NOT EXISTS `operations_alerts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `alert_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `alert_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `severity` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM',
  `reference_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolved_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_operations_alert_status` (`alert_status`,`severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `password_reset_tokens` (added V13)
--

CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `entity_type` VARCHAR(40) NOT NULL,
  `entity_id` BIGINT UNSIGNED NOT NULL,
  `email` VARCHAR(255) NOT NULL,
  `token_hash` VARCHAR(64) NOT NULL,
  `request_ip` VARCHAR(100) NULL,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `consumed_at` TIMESTAMP NULL,
  `expires_at` TIMESTAMP NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_password_reset_token_hash` (`token_hash`),
  KEY `idx_password_reset_entity` (`entity_type`, `entity_id`, `consumed_at`, `expires_at`),
  KEY `idx_password_reset_email` (`email`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `payment_intents` (added V12)
--

CREATE TABLE IF NOT EXISTS `payment_intents` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `intent_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `country` VARCHAR(10) NOT NULL,
  `intent_status` VARCHAR(40) NOT NULL DEFAULT 'CREATED',
  `description` TEXT NULL,
  `expires_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_intent_reference` (`intent_reference`),
  KEY `idx_payment_intent_merchant` (`merchant_id`, `intent_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `payment_links` (added V7)
--

CREATE TABLE IF NOT EXISTS `payment_links` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `link_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `merchant_number` VARCHAR(100) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `country` VARCHAR(10) NOT NULL,
  `description` TEXT NULL,
  `callback_url` TEXT NULL,
  `token_hash` VARCHAR(64) NOT NULL,
  `link_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` TIMESTAMP NULL,
  `paid_transaction_id` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_link_reference` (`link_reference`),
  UNIQUE KEY `uk_payment_link_token` (`token_hash`),
  KEY `idx_payment_link_merchant` (`merchant_id`, `link_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `payout_compensation_sagas` (added V18)
--

CREATE TABLE IF NOT EXISTS `payout_compensation_sagas` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `transactions_log_id` BIGINT UNSIGNED NOT NULL,
  `tx_unique_id` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `total_steps` INT NOT NULL,
  `completed_steps` INT NOT NULL DEFAULT 0,
  `last_step_name` VARCHAR(100) NULL,
  `saga_status` VARCHAR(30) NOT NULL DEFAULT 'STARTED',
  `last_error` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_saga_tx` (`transactions_log_id`),
  KEY `idx_payout_saga_status` (`saga_status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `provider_certification_evidence` (added V9)
--

CREATE TABLE IF NOT EXISTS `provider_certification_evidence` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `scenario_name` VARCHAR(120) NOT NULL,
  `evidence_type` VARCHAR(80) NOT NULL,
  `run_id` BIGINT UNSIGNED NULL,
  `statement_run_id` BIGINT UNSIGNED NULL,
  `evidence_status` VARCHAR(40) NOT NULL DEFAULT 'CAPTURED',
  `evidence_summary` TEXT NULL,
  `storage_ref` TEXT NULL,
  `approved_by` VARCHAR(255) NULL,
  `approved_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_provider_cert_evidence_provider` (`provider_code`, `channel_code`, `scenario_name`),
  KEY `idx_provider_cert_evidence_status` (`evidence_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `provider_certification_requirements` (added V9)
--

CREATE TABLE IF NOT EXISTS `provider_certification_requirements` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL DEFAULT '*',
  `channel_code` VARCHAR(100) NOT NULL DEFAULT '*',
  `scenario_name` VARCHAR(120) NOT NULL,
  `required_flag` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_cert_requirement` (`provider_code`, `channel_code`, `scenario_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `provider_endpoint_runs`
-- (merchant_number, environment columns + index added by V15)
--

CREATE TABLE IF NOT EXISTS `provider_endpoint_runs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reference_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `endpoint_url` text COLLATE utf8mb4_unicode_ci,
  `http_status` int DEFAULT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_summary` text COLLATE utf8mb4_unicode_ci,
  `run_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `merchant_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `environment` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SANDBOX',
  PRIMARY KEY (`id`),
  KEY `idx_provider_runs_merchant_env` (`merchant_number`,`environment`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `provider_sandbox_runs`
--

CREATE TABLE IF NOT EXISTS `provider_sandbox_runs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scenario_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `run_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_summary` text COLLATE utf8mb4_unicode_ci,
  `response_summary` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `provider_statement_validation_runs`
--

CREATE TABLE IF NOT EXISTS `provider_statement_validation_runs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `validation_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_rows` int NOT NULL DEFAULT '0',
  `valid_rows` int NOT NULL DEFAULT '0',
  `invalid_rows` int NOT NULL DEFAULT '0',
  `duplicate_rows` int NOT NULL DEFAULT '0',
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `provider_tokens` (added V7)
--

CREATE TABLE IF NOT EXISTS `provider_tokens` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL,
  `segment` VARCHAR(100) NOT NULL,
  `environment` VARCHAR(40) NOT NULL DEFAULT 'SANDBOX',
  `token_value` TEXT NOT NULL,
  `expires_at` TIMESTAMP NULL,
  `lease_owner` VARCHAR(255) NULL,
  `lease_expires_at` TIMESTAMP NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_token` (`provider_code`, `segment`, `environment`),
  KEY `idx_provider_token_expiry` (`expires_at`),
  KEY `idx_provider_token_lease` (`lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `reconciliation_daily_closes`
--

CREATE TABLE IF NOT EXISTS `reconciliation_daily_closes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `close_date` date NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `close_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `matched_count` int NOT NULL DEFAULT '0',
  `unmatched_count` int NOT NULL DEFAULT '0',
  `exception_count` int NOT NULL DEFAULT '0',
  `variance_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `closed_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `closed_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_close_currency` (`close_date`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `reconciliation_imports`
--

CREATE TABLE IF NOT EXISTS `reconciliation_imports` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `imported_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_records` int NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `reconciliation_records`
--

CREATE TABLE IF NOT EXISTS `reconciliation_records` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `import_id` bigint unsigned DEFAULT NULL,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `merchant_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `match_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNMATCHED',
  `match_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `exception_category` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `settlement_batch` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recon_records_match` (`match_status`,`currency`),
  KEY `idx_recon_records_reference` (`merchant_reference`,`provider_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `reconciliation_reviews`
--

CREATE TABLE IF NOT EXISTS `reconciliation_reviews` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `reconciliation_record_id` bigint unsigned NOT NULL,
  `transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `review_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `requested_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `review_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `reviewed_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewed_at` timestamp NULL DEFAULT NULL,
  `review_note` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recon_reviews_status` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `reconciliation_settlement_batches`
--

CREATE TABLE IF NOT EXISTS `reconciliation_settlement_batches` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `batch_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expected_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `opened_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `closed_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `closed_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_settlement_batch` (`batch_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `risk_decision_scores` (added V9)
--

CREATE TABLE IF NOT EXISTS `risk_decision_scores` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `risk_decision_id` BIGINT UNSIGNED NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `request_reference` VARCHAR(255) NOT NULL,
  `score_type` VARCHAR(80) NOT NULL,
  `score_value` DECIMAL(9,4) NOT NULL DEFAULT 0,
  `features_json` TEXT NULL,
  `score_status` VARCHAR(40) NOT NULL DEFAULT 'SHADOW',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_risk_decision_score_reference` (`merchant_id`, `request_reference`, `created_at`),
  KEY `idx_risk_decision_score_status` (`score_type`, `score_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `risk_decisions` (added V7)
--

CREATE TABLE IF NOT EXISTS `risk_decisions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `request_reference` VARCHAR(255) NOT NULL,
  `direction` VARCHAR(30) NOT NULL,
  `amount` DECIMAL(19,4) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `decision` VARCHAR(40) NOT NULL,
  `reason_code` VARCHAR(120) NOT NULL,
  `decision_summary` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_risk_decision_merchant` (`merchant_id`, `created_at`),
  KEY `idx_risk_decision_reference` (`request_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `risk_rules` (added V7)
--

CREATE TABLE IF NOT EXISTS `risk_rules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `rule_key` VARCHAR(150) NOT NULL,
  `rule_type` VARCHAR(80) NOT NULL,
  `scope_type` VARCHAR(50) NOT NULL DEFAULT 'GLOBAL',
  `scope_reference` VARCHAR(150) NOT NULL DEFAULT '*',
  `currency` VARCHAR(10) NULL,
  `threshold_amount` DECIMAL(19,4) NULL,
  `threshold_count` INT NULL,
  `decision` VARCHAR(40) NOT NULL DEFAULT 'REVIEW',
  `enabled` VARCHAR(3) NOT NULL DEFAULT 'YES',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_rule_key` (`rule_key`),
  KEY `idx_risk_rule_lookup` (`rule_type`, `scope_type`, `scope_reference`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `settings`
--

CREATE TABLE IF NOT EXISTS `settings` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `label` varchar(255) NOT NULL DEFAULT '',
  `name` varchar(255) NOT NULL DEFAULT '',
  `setting_value` text,
  `description` varchar(255) NOT NULL DEFAULT '',
  `setting_group` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Table structure for table `settlement_schedules` (added V7)
--

CREATE TABLE IF NOT EXISTS `settlement_schedules` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `provider_code` VARCHAR(100) NOT NULL,
  `channel_code` VARCHAR(100) NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `schedule_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `minimum_retained_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `sweep_hour` INT NOT NULL DEFAULT 2,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_schedule` (`merchant_id`, `provider_code`, `channel_code`, `currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `settlement_sweep_runs` (added V7)
--

CREATE TABLE IF NOT EXISTS `settlement_sweep_runs` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `schedule_id` BIGINT UNSIGNED NOT NULL,
  `run_reference` VARCHAR(255) NOT NULL,
  `run_status` VARCHAR(40) NOT NULL DEFAULT 'OPENED',
  `sweep_amount` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `message` TEXT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_sweep_run` (`run_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `spring_session`
--

CREATE TABLE IF NOT EXISTS `spring_session` (
  `PRIMARY_ID` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL,
  `SESSION_ID` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL,
  `CREATION_TIME` bigint NOT NULL,
  `LAST_ACCESS_TIME` bigint NOT NULL,
  `MAX_INACTIVE_INTERVAL` int NOT NULL,
  `EXPIRY_TIME` bigint NOT NULL,
  `PRINCIPAL_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`PRIMARY_ID`),
  UNIQUE KEY `SPRING_SESSION_IX1` (`SESSION_ID`),
  KEY `SPRING_SESSION_IX2` (`EXPIRY_TIME`),
  KEY `SPRING_SESSION_IX3` (`PRINCIPAL_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci ROW_FORMAT=DYNAMIC;

--
-- Table structure for table `spring_session_attributes`
--

CREATE TABLE IF NOT EXISTS `spring_session_attributes` (
  `SESSION_PRIMARY_ID` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL,
  `ATTRIBUTE_NAME` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL,
  `ATTRIBUTE_BYTES` blob NOT NULL,
  PRIMARY KEY (`SESSION_PRIMARY_ID`,`ATTRIBUTE_NAME`),
  CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK` FOREIGN KEY (`SESSION_PRIMARY_ID`) REFERENCES `spring_session` (`PRIMARY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci ROW_FORMAT=DYNAMIC;

--
-- Table structure for table `transfer_intents` (added V12)
--

CREATE TABLE IF NOT EXISTS `transfer_intents` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `intent_reference` VARCHAR(255) NOT NULL,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `quote_reference` VARCHAR(255) NULL,
  `source_country` VARCHAR(10) NOT NULL,
  `target_country` VARCHAR(10) NOT NULL,
  `source_currency` VARCHAR(10) NOT NULL,
  `target_currency` VARCHAR(10) NOT NULL,
  `source_amount` DECIMAL(19,4) NOT NULL,
  `target_amount` DECIMAL(19,4) NULL,
  `beneficiary_account` VARCHAR(255) NOT NULL,
  `beneficiary_name` VARCHAR(255) NULL,
  `intent_status` VARCHAR(40) NOT NULL DEFAULT 'CREATED',
  `risk_decision` VARCHAR(40) NOT NULL DEFAULT 'PENDING',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_intent_reference` (`intent_reference`),
  KEY `idx_transfer_intent_merchant` (`merchant_id`, `intent_status`),
  KEY `idx_transfer_intent_quote` (`quote_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Table structure for table `treasury_positions` (added V12)
--

CREATE TABLE IF NOT EXISTS `treasury_positions` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `currency` VARCHAR(10) NOT NULL,
  `available_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `reserved_balance` DECIMAL(19,4) NOT NULL DEFAULT 0,
  `position_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `updated_by` VARCHAR(255) NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_treasury_position_currency` (`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dump reconstructed on 2026-07-28
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
