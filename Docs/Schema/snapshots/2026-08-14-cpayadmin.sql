mysqldump: [Warning] Using a password on the command line interface can be insecure.
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SPRING_SESSION` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SPRING_SESSION_ATTRIBUTES` (
  `SESSION_PRIMARY_ID` char(36) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL,
  `ATTRIBUTE_NAME` varchar(200) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL,
  `ATTRIBUTE_BYTES` blob NOT NULL,
  PRIMARY KEY (`SESSION_PRIMARY_ID`,`ATTRIBUTE_NAME`),
  CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK` FOREIGN KEY (`SESSION_PRIMARY_ID`) REFERENCES `SPRING_SESSION` (`PRIMARY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounts_register` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_audit_events` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_impersonation_sessions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `admin_user_id` bigint unsigned NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` timestamp NOT NULL,
  `ended_at` timestamp NULL DEFAULT NULL,
  `ended_reason` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_impersonation_admin_active` (`admin_user_id`,`ended_at`),
  KEY `idx_admin_impersonation_merchant` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_mfa_totp` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` bigint unsigned NOT NULL,
  `secret_value` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NO',
  `verified_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_mfa_totp_admin` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_permissions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `role_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `permission_code` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_permission` (`role_name`,`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_privileges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` bigint unsigned DEFAULT NULL,
  `privilege` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_admin_priv` (`admin_id`,`privilege`),
  CONSTRAINT `admin_privileges_ibfk_1` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admins` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `api_rate_limits` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `rate_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `window_start` timestamp NOT NULL,
  `request_count` int NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_rate_limit_window` (`rate_key`,`window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_trail` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_name` varchar(255) NOT NULL DEFAULT '',
  `user_id` varchar(255) NOT NULL DEFAULT '',
  `action` text,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `prev_hash` varchar(64) DEFAULT NULL,
  `entry_hash` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`%`*/ /*!50003 TRIGGER `audit_trail_no_update` BEFORE UPDATE ON `audit_trail` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_trail is append-only: rows cannot be updated';
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`%`*/ /*!50003 TRIGGER `audit_trail_no_delete` BEFORE DELETE ON `audit_trail` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_trail is append-only: rows cannot be deleted';
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `balance_ledger_events` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `beneficial_owners` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `full_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `id_type` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `id_value_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ownership_percent` decimal(7,4) DEFAULT NULL,
  `screening_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_beneficial_owner_merchant` (`merchant_id`,`screening_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `beneficiaries` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_accounts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` bigint unsigned NOT NULL,
  `billing_customer_id` bigint unsigned NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_account_tenant` (`billing_tenant_id`),
  KEY `idx_billing_account_customer` (`billing_customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_completeness_gates` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_invoice_id` bigint unsigned NOT NULL,
  `billing_tenant_id` bigint unsigned NOT NULL,
  `gate_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_APPROVAL',
  `completeness_result` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `unstaged_charge_count` int NOT NULL DEFAULT '0',
  `requested_by` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `requested_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approved_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_at` timestamp NULL DEFAULT NULL,
  `rejection_reason` text COLLATE utf8mb4_unicode_ci,
  `waiver_reason` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_completeness_gate_invoice` (`billing_invoice_id`),
  KEY `idx_billing_completeness_gate_status` (`gate_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_credit_notes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` bigint unsigned NOT NULL,
  `billing_invoice_id` bigint unsigned NOT NULL,
  `credit_note_number` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ledger_transaction_id` bigint unsigned DEFAULT NULL,
  `issued_by` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_credit_note_number` (`credit_note_number`),
  KEY `idx_billing_credit_note_invoice` (`billing_invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_customers` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` bigint unsigned NOT NULL,
  `customer_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MERCHANT_SELF',
  `display_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `customer_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_customer_tenant` (`billing_tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_invoice_lines` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_invoice_id` bigint unsigned NOT NULL,
  `billing_rated_charge_id` bigint unsigned DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_invoice_line_charge` (`billing_rated_charge_id`),
  KEY `idx_billing_invoice_line_invoice` (`billing_invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_invoices` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` bigint unsigned NOT NULL,
  `invoice_number` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `subtotal_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `tax_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `total_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `finalized_at` timestamp NULL DEFAULT NULL,
  `finalized_by` varchar(191) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ledger_transaction_id` bigint unsigned DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_invoice_number` (`invoice_number`),
  KEY `idx_billing_invoice_tenant_period` (`billing_tenant_id`,`period_start`,`period_end`),
  KEY `idx_billing_invoice_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_ledger_links` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `ledger_transaction_id` bigint unsigned NOT NULL,
  `ledger_entry_id` bigint unsigned DEFAULT NULL,
  `billing_tenant_id` bigint unsigned NOT NULL,
  `link_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `billing_reference` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_ledger_link_transaction` (`ledger_transaction_id`),
  KEY `idx_billing_ledger_link_tenant_ref` (`billing_tenant_id`,`link_type`,`billing_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_meter_versions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `meter_id` bigint unsigned NOT NULL,
  `version_no` int NOT NULL,
  `dimension_keys` json DEFAULT NULL,
  `effective_from` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `effective_to` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_meter_version` (`meter_id`,`version_no`),
  KEY `idx_billing_meter_version_meter` (`meter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_meters` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `service_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meter_code` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meter_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregation_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COUNT',
  `meter_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_meter_code` (`meter_code`),
  KEY `idx_billing_meter_service` (`service_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_outbox` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `aggregate_type` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload` json NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT '0',
  `next_attempt_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_error` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_outbox_status_next_attempt` (`status`,`next_attempt_at`),
  KEY `idx_billing_outbox_aggregate` (`aggregate_type`,`aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_payment_allocations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` bigint unsigned NOT NULL,
  `billing_invoice_id` bigint unsigned NOT NULL,
  `payment_reference` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ledger_transaction_id` bigint unsigned DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_payment_allocation` (`billing_invoice_id`,`payment_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_price_book_versions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` bigint unsigned DEFAULT NULL,
  `service_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meter_code` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `charge_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CUSTOMER_CHARGE',
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `version_no` int NOT NULL,
  `effective_from` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `effective_to` timestamp NULL DEFAULT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_price_book_lookup` (`billing_tenant_id`,`service_code`,`meter_code`,`charge_type`,`effective_from`,`effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_price_components` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `price_book_version_id` bigint unsigned NOT NULL,
  `component_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sequence_no` int NOT NULL DEFAULT '1',
  `flat_amount` decimal(19,4) DEFAULT NULL,
  `percentage_rate` decimal(9,6) DEFAULT NULL,
  `tier_definition` json DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_billing_price_component_version` (`price_book_version_id`,`sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_rated_charges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` bigint unsigned NOT NULL,
  `price_book_version_id` bigint unsigned NOT NULL,
  `service_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meter_code` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `charge_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_reference` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_amount` decimal(19,4) NOT NULL,
  `rated_amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rounding_policy` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tier_path` json DEFAULT NULL,
  `formula_inputs` json DEFAULT NULL,
  `idempotency_key` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `computed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_rated_charge_idem` (`idempotency_key`),
  KEY `idx_billing_rated_charge_tenant` (`billing_tenant_id`,`service_code`,`meter_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_service_catalog` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `service_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `service_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `service_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_service_code` (`service_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_tenants` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `tenant_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CPAY_MERCHANT',
  `tenant_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_tenant_merchant` (`merchant_id`),
  KEY `idx_billing_tenant_status` (`tenant_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billing_usage_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `billing_tenant_id` bigint unsigned NOT NULL,
  `service_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `meter_code` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_time` timestamp NOT NULL,
  `quantity` decimal(19,4) NOT NULL DEFAULT '1.0000',
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dimensions` json DEFAULT NULL,
  `source_reference` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `idempotency_key` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_billing_usage_event_idempotency` (`idempotency_key`),
  KEY `idx_billing_usage_event_tenant_service` (`billing_tenant_id`,`service_code`,`event_time`),
  KEY `idx_billing_usage_event_source` (`source_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `callback_delivery_signatures` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `callback_task_id` bigint unsigned NOT NULL,
  `signature_algorithm` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `signature_value` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `nonce` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_callback_signature_task` (`callback_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `callback_task_claims` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id` bigint unsigned NOT NULL,
  `worker_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `claim_status` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_callback_task_active_claim` (`task_id`,`claim_status`),
  KEY `idx_callback_claim_worker` (`worker_name`,`claim_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `callback_tasks` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `channel_routing_prefixes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `gateway_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `msisdn_prefix` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `active_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_routing_prefix` (`gateway_id`,`msisdn_prefix`),
  KEY `idx_channel_routing_active` (`active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `charging_details` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `gateway_id` varchar(255) NOT NULL DEFAULT '',
  `service` enum('PAYIN','PAYOUT') NOT NULL DEFAULT 'PAYIN',
  `amount` double NOT NULL DEFAULT '0',
  `charging_method` enum('PERCENTAGE','FLAT_FEE','TIER') NOT NULL DEFAULT 'PERCENTAGE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_charge` (`gateway_id`,`service`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_callback_nonces` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(40) NOT NULL,
  `nonce` varchar(64) NOT NULL,
  `signature` varchar(256) DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `used_flag` char(1) NOT NULL DEFAULT 'N',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ccn_nonce` (`nonce`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_campaign_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `campaign_id` bigint NOT NULL,
  `recipient` varchar(64) NOT NULL,
  `message_body` text,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `trace` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_citem_campaign_status` (`campaign_id`,`status`),
  CONSTRAINT `fk_citem_campaign` FOREIGN KEY (`campaign_id`) REFERENCES `communication_campaigns` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_campaigns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint NOT NULL,
  `name` varchar(120) NOT NULL,
  `channel` varchar(20) NOT NULL,
  `template_key` varchar(120) DEFAULT NULL,
  `audience_sql` varchar(500) DEFAULT NULL,
  `total_recipients` int NOT NULL DEFAULT '0',
  `processed_recipients` int NOT NULL DEFAULT '0',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT',
  `scheduled_at` datetime DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_by` varchar(120) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ccampaign_merchant` (`merchant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_consent_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint NOT NULL,
  `channel` varchar(20) NOT NULL,
  `consent_type` varchar(40) NOT NULL,
  `source` varchar(40) NOT NULL,
  `changed_by` varchar(120) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cconsent_merchant_channel` (`merchant_id`,`channel`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_message_deliveries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint NOT NULL,
  `channel` varchar(20) NOT NULL,
  `provider_code` varchar(40) DEFAULT NULL,
  `reference_type` varchar(30) DEFAULT NULL,
  `reference_id` bigint DEFAULT NULL,
  `recipient` varchar(128) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `trace` varchar(500) DEFAULT NULL,
  `gw_response` text,
  `charged_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `billed_flag` char(1) NOT NULL DEFAULT 'N',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cmd_merchant_status` (`merchant_id`,`status`,`created_at`),
  KEY `idx_cmd_channel_status` (`channel`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_message_preferences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint NOT NULL,
  `channel` varchar(20) NOT NULL,
  `enabled_flag` char(1) NOT NULL DEFAULT 'Y',
  `quiet_hours_start` varchar(5) DEFAULT NULL,
  `quiet_hours_end` varchar(5) DEFAULT NULL,
  `updated_by` varchar(120) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmp_merchant_channel` (`merchant_id`,`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_message_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_key` varchar(120) NOT NULL,
  `channel` varchar(20) NOT NULL,
  `subject_template` varchar(500) DEFAULT NULL,
  `body_template` text NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmt_template_channel` (`template_key`,`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_provider_channels` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(40) NOT NULL,
  `channel` varchar(20) NOT NULL,
  `display_name` varchar(120) NOT NULL,
  `enabled_flag` char(1) NOT NULL DEFAULT 'N',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpc_provider_channel` (`provider_code`,`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_provider_credentials` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(40) NOT NULL,
  `credential_key` varchar(80) NOT NULL,
  `credential_value_encrypted` text NOT NULL,
  `updated_by` varchar(120) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpc_cred` (`provider_code`,`credential_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_provider_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(40) NOT NULL,
  `max_per_minute` int NOT NULL DEFAULT '60',
  `max_per_hour` int NOT NULL DEFAULT '1000',
  `connect_timeout_ms` int NOT NULL DEFAULT '10000',
  `read_timeout_ms` int NOT NULL DEFAULT '30000',
  `rate_limit_flag` char(1) NOT NULL DEFAULT 'Y',
  `enabled_flag` char(1) NOT NULL DEFAULT 'Y',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpp_provider` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_providers` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SMS',
  `adapter_class` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `credentials_ref` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comm_provider` (`provider_code`,`channel`),
  KEY `idx_comm_provider_channel` (`channel`,`enabled_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_routing_rules` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `channel` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SMS',
  `merchant_id` bigint unsigned DEFAULT NULL COMMENT 'NULL = platform default for the channel',
  `priority` int NOT NULL DEFAULT '100' COMMENT 'lower wins; ties broken by lowest id',
  `provider_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comm_route_lookup` (`channel`,`merchant_id`,`priority`,`enabled_flag`),
  KEY `idx_comm_route_provider` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_usage_watermark` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `channel` varchar(20) NOT NULL,
  `last_delivery_id` bigint NOT NULL DEFAULT '0',
  `processed_flag` char(1) NOT NULL DEFAULT 'N',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cuw_channel` (`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_ussd_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `phone_number` varchar(32) NOT NULL,
  `session_id` varchar(120) NOT NULL,
  `current_menu` varchar(80) NOT NULL DEFAULT 'MAIN',
  `state_json` text,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ussd_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_whatsapp_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint NOT NULL,
  `provider_code` varchar(40) NOT NULL,
  `wa_phone` varchar(32) NOT NULL,
  `template_name` varchar(120) DEFAULT NULL,
  `message_body` text,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `provider_message_id` varchar(120) DEFAULT NULL,
  `trace` varchar(500) DEFAULT NULL,
  `gw_response` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_wa_merchant_status` (`merchant_id`,`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_blocklist` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `value_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `value_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `active_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_blocklist_value` (`value_type`,`value_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_case_notes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `case_id` bigint unsigned NOT NULL,
  `note_type` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SYSTEM',
  `note_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'system',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_compliance_case_note_case` (`case_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_cases` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `case_reference` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `case_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint unsigned NOT NULL,
  `source_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `severity` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM',
  `case_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `assigned_to` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decision` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decision_reason` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `closed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_case_reference` (`case_reference`),
  KEY `idx_compliance_case_entity` (`entity_type`,`entity_id`,`case_status`),
  KEY `idx_compliance_case_queue` (`case_status`,`severity`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_profiles` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint unsigned NOT NULL,
  `profile_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tier` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STANDARD',
  `status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `risk_rating` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNKNOWN',
  `required_documents_json` text COLLATE utf8mb4_unicode_ci,
  `decision_reason` text COLLATE utf8mb4_unicode_ci,
  `verified_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `verified_at` timestamp NULL DEFAULT NULL,
  `expires_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compliance_profile_entity` (`entity_type`,`entity_id`,`profile_type`),
  KEY `idx_compliance_profile_status` (`status`,`risk_rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_screening_hits` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `request_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `direction` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `screened_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `screened_value_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `watchlist_entry_id` bigint unsigned NOT NULL,
  `decision` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `hit_summary` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_screening_hit_reference` (`merchant_id`,`request_reference`,`created_at`),
  KEY `idx_screening_hit_watchlist` (`watchlist_entry_id`,`decision`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_watchlist_entries` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `list_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entry_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entry_value_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entry_label` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `risk_rating` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'HIGH',
  `action` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REVIEW',
  `source_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_watchlist_entry` (`list_name`,`entry_type`,`entry_value_hash`),
  KEY `idx_watchlist_lookup` (`entry_type`,`entry_value_hash`,`active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cpay_idempotency_keys` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cpay_request_nonces` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_number` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nonce_value` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` timestamp NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cpay_request_nonce` (`merchant_number`,`nonce_value`),
  KEY `idx_cpay_request_nonce_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cross_border_corridors` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `source_country` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_country` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `corridor_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `daily_limit` decimal(19,4) DEFAULT NULL,
  `single_transfer_limit` decimal(19,4) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cross_border_corridor` (`source_country`,`target_country`,`source_currency`,`target_currency`,`provider_code`),
  KEY `idx_cross_border_corridor_lookup` (`source_country`,`target_country`,`corridor_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `daily_failure_reason_stats` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL,
  `gateway_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `error_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `failure_reason` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `tx_count` int NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_failure_stats` (`stat_date`,`gateway_id`,`error_code`,`failure_reason`),
  KEY `idx_daily_failure_stats_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `daily_transaction_stats` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL,
  `merchant_id` bigint unsigned NOT NULL DEFAULT '0',
  `gateway_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `tx_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tx_count` int NOT NULL DEFAULT '0',
  `total_amount` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `total_charges` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_tx_stats` (`stat_date`,`merchant_id`,`gateway_id`,`tx_type`,`status`),
  KEY `idx_daily_tx_stats_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `db_changes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `query_id` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci NOT NULL DEFAULT '',
  `sql_text` text CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci,
  `roll_back` text CHARACTER SET utf8mb3 COLLATE utf8mb3_unicode_ci,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `query_id` (`query_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `efris_receipts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `receipt_reference` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `merchant_number` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `transaction_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_transaction_ref` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payer_msisdn_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UGX',
  `receipt_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT '0',
  `next_retry_at` timestamp NULL DEFAULT NULL,
  `efris_response_json` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_efris_receipt_reference` (`receipt_reference`),
  UNIQUE KEY `uk_efris_transaction_reference` (`transaction_reference`),
  KEY `idx_efris_receipt_queue` (`receipt_status`,`next_retry_at`),
  KEY `idx_efris_receipt_merchant` (`merchant_id`,`receipt_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feature_flags` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `flag_key` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feature_flags_key` (`flag_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fee_schedules` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `gateway_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `service` enum('PAYIN','PAYOUT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `charge_type` enum('CUSTOMER_CHARGE','COST_OF_PAYMENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `charging_method` enum('PERCENTAGE','FLAT_FEE','TIER') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PERCENTAGE',
  `amount` decimal(18,4) NOT NULL,
  `effective_from` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `effective_to` timestamp NULL DEFAULT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fee_schedule_lookup` (`gateway_id`,`merchant_id`,`service`,`charge_type`,`effective_from`,`effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `float_balance_snapshots` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL,
  `account_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `balance` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_float_balance_snapshot` (`stat_date`,`account_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `float_topups` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `topup_date` date NOT NULL,
  `account` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(18,4) NOT NULL,
  `recorded_by` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'system',
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_float_topups_date` (`topup_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fx_quotes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `quote_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `source_currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_amount` decimal(19,4) NOT NULL,
  `target_amount` decimal(19,4) NOT NULL,
  `rate` decimal(24,10) NOT NULL,
  `quote_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `expires_at` timestamp NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fx_quote_reference` (`quote_reference`),
  KEY `idx_fx_quote_merchant` (`merchant_id`,`quote_status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fx_rates` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `source_currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rate` decimal(24,10) NOT NULL,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INTERNAL',
  `rate_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `valid_from` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `valid_until` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_fx_rate_lookup` (`source_currency`,`target_currency`,`rate_status`,`valid_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `hosted_checkout_attempts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `payment_link_id` bigint unsigned NOT NULL,
  `payer_account` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attempt_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_checkout_attempt_link` (`payment_link_id`,`attempt_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `identity_verification_audit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `request_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `action_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `performed_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_identity_audit_merchant` (`merchant_id`,`created_at`),
  KEY `idx_identity_audit_reference` (`request_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `identity_verification_requests` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `request_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `subject_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `subject_msisdn` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `identity_number_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `identity_number_mask` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `consent_granted` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NO',
  `consent_recorded_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `consent_recorded_at` timestamp NULL DEFAULT NULL,
  `request_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `provider_result_json` text COLLATE utf8mb4_unicode_ci,
  `provider_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requested_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_identity_request_reference` (`request_reference`),
  KEY `idx_identity_request_merchant` (`merchant_id`,`request_status`),
  KEY `idx_identity_request_lookup` (`identity_number_hash`,`request_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoices` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `payer_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payer_contact` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `public_token_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_merchant_reference` (`merchant_id`,`reference`),
  UNIQUE KEY `uk_invoice_public_token` (`public_token_hash`),
  KEY `idx_invoice_merchant_status` (`merchant_id`,`status`),
  KEY `idx_invoice_due_date` (`status`,`due_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_account_balances` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `account_id` bigint unsigned NOT NULL,
  `account_code` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `debit_total` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `credit_total` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `net_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `last_ledger_entry_id` bigint unsigned DEFAULT NULL,
  `refreshed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_account_balance` (`account_id`,`currency`),
  KEY `idx_ledger_account_balance_code` (`account_code`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_accounts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `account_code` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_type` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_type` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` bigint unsigned DEFAULT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_account_code` (`account_code`),
  KEY `idx_ledger_account_owner` (`owner_type`,`owner_id`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_entries` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `ledger_transaction_id` bigint unsigned NOT NULL,
  `account_id` bigint unsigned NOT NULL,
  `entry_direction` varchar(2) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entry_memo` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ledger_entry_transaction` (`ledger_transaction_id`),
  KEY `idx_ledger_entry_account` (`account_id`,`currency`),
  CONSTRAINT `fk_ledger_entry_account` FOREIGN KEY (`account_id`) REFERENCES `ledger_accounts` (`id`),
  CONSTRAINT `fk_ledger_entry_transaction` FOREIGN KEY (`ledger_transaction_id`) REFERENCES `ledger_transactions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_period_locks` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `locked_by` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `released_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ledger_period_lock_active` (`currency`,`period_start`,`period_end`,`released_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_reservations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `reservation_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `source_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reservation_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RESERVED',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_reservation_ref` (`reservation_reference`),
  KEY `idx_ledger_reservation_merchant` (`merchant_id`,`reservation_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_transactions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `transaction_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `transaction_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'POSTED',
  `description` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_transaction_ref` (`transaction_reference`),
  KEY `idx_ledger_transaction_source` (`source_type`,`source_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_trial_balance_runs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `run_date` date NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_debits` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `total_credits` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `balanced_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NO',
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trial_balance_run` (`run_date`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_admin_privileges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` bigint unsigned DEFAULT NULL,
  `privilege` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_admin_priv` (`admin_id`,`privilege`),
  CONSTRAINT `merchant_admin_privileges_ibfk_1` FOREIGN KEY (`admin_id`) REFERENCES `merchant_admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_admins` (
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
  `role` enum('OWNER','FINANCE','DEVELOPER','VIEWER') NOT NULL DEFAULT 'OWNER',
  `email_verified_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_merchant_user` (`merchant_id`,`email`),
  CONSTRAINT `merchant_admins_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_batch_transactions_log` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_callback_secrets` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_channel_audit_events` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_channel_balances` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_channel_credentials` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_email_verification_tokens` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_admin_id` bigint unsigned NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `consumed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_mevt_merchant_admin_id` (`merchant_admin_id`),
  CONSTRAINT `merchant_email_verification_tokens_ibfk_1` FOREIGN KEY (`merchant_admin_id`) REFERENCES `merchant_admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_environment_preferences` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `merchant_user_id` bigint unsigned DEFAULT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '*',
  `active_environment` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SANDBOX',
  `production_limit_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `production_transaction_limit` int NOT NULL DEFAULT '10',
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_environment_preference` (`merchant_id`,`merchant_user_id`,`channel_code`),
  KEY `idx_merchant_environment_active` (`merchant_id`,`active_environment`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_feature_flags` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `flag_key` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '0',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_feature_flag` (`merchant_id`,`flag_key`),
  KEY `idx_merchant_feature_flag_key` (`flag_key`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_kyc_documents` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `document_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_ref` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `document_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `verification_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `verified_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `verified_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kyc_document_merchant` (`merchant_id`,`verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_mfa_totp` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_admin_id` bigint unsigned NOT NULL,
  `secret_value` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NO',
  `verified_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_mfa_totp_admin` (`merchant_admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_notification_preferences` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `event_type` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email_enabled` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `sms_enabled` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NO',
  `webhook_enabled` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `channel` enum('EMAIL','SMS','NONE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EMAIL',
  `notify_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_notification_pref` (`merchant_id`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_settings` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_settlement_preferences` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `settlement_frequency` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DAILY',
  `settlement_day_of_week` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `minimum_settlement_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_settlement_preference` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_sms` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_statement` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_transactions_log` (
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
  `archived_on` timestamp NULL DEFAULT NULL,
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
  KEY `idx_mtl_status_created_on` (`status`,`created_on`),
  KEY `idx_mtl_archived_on` (`archived_on`),
  CONSTRAINT `merchant_transactions_log_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE SET NULL,
  CONSTRAINT `merchant_transactions_log_ibfk_2` FOREIGN KEY (`merchant_batch_transactions_log_id`) REFERENCES `merchant_batch_transactions_log` (`id`) ON DELETE SET NULL,
  CONSTRAINT `merchant_transactions_log_ibfk_3` FOREIGN KEY (`beneficiary_id`) REFERENCES `beneficiaries` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_transactions_log_archive` (
  `id` bigint unsigned NOT NULL,
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
  `created_on` datetime NOT NULL,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
  `archived_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_mtla_merchant_created_on` (`merchant_id`,`created_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_webhook_deliveries` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `endpoint_id` bigint unsigned NOT NULL,
  `event_type` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `delivery_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT '0',
  `next_attempt_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_http_status` int DEFAULT NULL,
  `last_response_summary` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_webhook_delivery` (`endpoint_id`,`event_reference`),
  KEY `idx_merchant_webhook_delivery_due` (`delivery_status`,`next_attempt_at`),
  KEY `idx_merchant_webhook_delivery_merchant` (`merchant_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_webhook_endpoints` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `event_type` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `endpoint_url` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `secret_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `secret_value` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `endpoint_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_webhook_event` (`merchant_id`,`event_type`),
  KEY `idx_merchant_webhook_status` (`endpoint_status`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchants` (
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
  `key_encryption_version` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_id` (`account_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchants_audit_trail` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned DEFAULT NULL,
  `user_name` varchar(255) NOT NULL DEFAULT '',
  `user_id` varchar(255) NOT NULL DEFAULT '',
  `action` text,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `prev_hash` varchar(64) DEFAULT NULL,
  `entry_hash` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`%`*/ /*!50003 TRIGGER `merchants_audit_trail_no_update` BEFORE UPDATE ON `merchants_audit_trail` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchants_audit_trail is append-only: rows cannot be updated';
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`%`*/ /*!50003 TRIGGER `merchants_audit_trail_no_delete` BEFORE DELETE ON `merchants_audit_trail` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'merchants_audit_trail is append-only: rows cannot be deleted';
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `normalized_balance_backfill_runs` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operating_control_events` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operations_alerts` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `entity_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint unsigned NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `token_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_ip` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `consumed_at` timestamp NULL DEFAULT NULL,
  `expires_at` timestamp NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_password_reset_token_hash` (`token_hash`),
  KEY `idx_password_reset_entity` (`entity_type`,`entity_id`,`consumed_at`,`expires_at`),
  KEY `idx_password_reset_email` (`email`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_intents` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `intent_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `country` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `intent_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CREATED',
  `description` text COLLATE utf8mb4_unicode_ci,
  `expires_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_intent_reference` (`intent_reference`),
  KEY `idx_payment_intent_merchant` (`merchant_id`,`intent_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_links` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `link_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `merchant_number` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `country` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `callback_url` text COLLATE utf8mb4_unicode_ci,
  `token_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `link_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `expires_at` timestamp NULL DEFAULT NULL,
  `paid_transaction_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_link_reference` (`link_reference`),
  UNIQUE KEY `uk_payment_link_token` (`token_hash`),
  KEY `idx_payment_link_merchant` (`merchant_id`,`link_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payout_approval_queue` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `payout_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `merchant_number` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `country` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `beneficiary_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trigger_reason` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `queue_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_APPROVAL',
  `requested_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requested_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approved_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_at` timestamp NULL DEFAULT NULL,
  `rejection_reason` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_approval_reference` (`payout_reference`),
  KEY `idx_payout_approval_queue` (`queue_status`,`created_at`),
  KEY `idx_payout_approval_merchant` (`merchant_id`,`queue_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payout_compensation_sagas` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `transactions_log_id` bigint unsigned NOT NULL,
  `tx_unique_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `total_steps` int NOT NULL,
  `completed_steps` int NOT NULL DEFAULT '0',
  `last_step_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `saga_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STARTED',
  `last_error` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_saga_tx` (`transactions_log_id`),
  KEY `idx_payout_saga_status` (`saga_status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payout_controls` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `country` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UG',
  `daily_amount_limit` decimal(19,4) DEFAULT NULL,
  `monthly_amount_limit` decimal(19,4) DEFAULT NULL,
  `per_transaction_limit` decimal(19,4) DEFAULT NULL,
  `beneficiary_velocity_limit` int DEFAULT NULL,
  `approval_required_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NO',
  `enabled_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payout_control` (`merchant_id`,`channel_code`,`currency`,`country`),
  KEY `idx_payout_control_enabled` (`enabled_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pii_inventory_entries` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `data_class` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_location` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `retention_days` int DEFAULT NULL,
  `masking_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'HASHED_AT_REST',
  `notes` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pii_inventory_class` (`data_class`,`storage_location`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_certification_evidence` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scenario_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `evidence_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `run_id` bigint unsigned DEFAULT NULL,
  `statement_run_id` bigint unsigned DEFAULT NULL,
  `evidence_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CAPTURED',
  `evidence_summary` text COLLATE utf8mb4_unicode_ci,
  `storage_ref` text COLLATE utf8mb4_unicode_ci,
  `approved_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_provider_cert_evidence_provider` (`provider_code`,`channel_code`,`scenario_name`),
  KEY `idx_provider_cert_evidence_status` (`evidence_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_certification_requirements` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '*',
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '*',
  `scenario_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `required_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_cert_requirement` (`provider_code`,`channel_code`,`scenario_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_conversation_references` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `conversation_id` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tx_reference` varchar(191) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_conversation` (`provider_code`,`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_endpoint_runs` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_sandbox_runs` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_statement_validation_runs` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_tokens` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `segment` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `environment` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SANDBOX',
  `token_value` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` timestamp NULL DEFAULT NULL,
  `lease_owner` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_token` (`provider_code`,`segment`,`environment`),
  KEY `idx_provider_token_expiry` (`expires_at`),
  KEY `idx_provider_token_lease` (`lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reconciliation_daily_closes` (
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
  `requested_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requested_at` timestamp NULL DEFAULT NULL,
  `approved_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_at` timestamp NULL DEFAULT NULL,
  `approved_variance_amount` decimal(19,4) DEFAULT NULL,
  `rejection_reason` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_daily_close_currency` (`close_date`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reconciliation_imports` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `imported_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_records` int NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reconciliation_records` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reconciliation_reviews` (
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reconciliation_settlement_batches` (
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
  `close_requested_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `close_requested_at` timestamp NULL DEFAULT NULL,
  `close_approved_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `close_approved_at` timestamp NULL DEFAULT NULL,
  `close_rejection_reason` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_settlement_batch` (`batch_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refunds` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `refund_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `original_transaction_id` bigint unsigned NOT NULL,
  `original_merchant_ref` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payout_transaction_id` bigint unsigned DEFAULT NULL,
  `requested_amount` decimal(18,4) NOT NULL,
  `refund_status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REQUESTED',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_reference` (`merchant_id`,`refund_reference`),
  KEY `idx_refund_original_tx` (`original_transaction_id`),
  KEY `idx_refund_status` (`refund_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `regulator_reports` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `report_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `report_date` date NOT NULL,
  `report_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GENERATED',
  `row_count` int NOT NULL DEFAULT '0',
  `total_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `report_json` longtext COLLATE utf8mb4_unicode_ci,
  `file_ref` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_regulator_report_run` (`report_type`,`report_date`),
  KEY `idx_regulator_report_type` (`report_type`,`report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_decision_scores` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `risk_decision_id` bigint unsigned DEFAULT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `request_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `score_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `score_value` decimal(9,4) NOT NULL DEFAULT '0.0000',
  `features_json` text COLLATE utf8mb4_unicode_ci,
  `score_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SHADOW',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_risk_decision_score_reference` (`merchant_id`,`request_reference`,`created_at`),
  KEY `idx_risk_decision_score_status` (`score_type`,`score_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_decisions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `request_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `direction` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,4) NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `decision` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason_code` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `decision_summary` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_risk_decision_merchant` (`merchant_id`,`created_at`),
  KEY `idx_risk_decision_reference` (`request_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_rules` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `rule_key` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rule_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GLOBAL',
  `scope_reference` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '*',
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `threshold_amount` decimal(19,4) DEFAULT NULL,
  `threshold_count` int DEFAULT NULL,
  `decision` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REVIEW',
  `enabled` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_rule_key` (`rule_key`),
  KEY `idx_risk_rule_lookup` (`rule_type`,`scope_type`,`scope_reference`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settings` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `label` varchar(255) NOT NULL DEFAULT '',
  `name` varchar(255) NOT NULL DEFAULT '',
  `setting_value` text,
  `description` varchar(255) NOT NULL DEFAULT '',
  `setting_group` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlement_schedules` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `provider_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `schedule_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `minimum_retained_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `sweep_hour` int NOT NULL DEFAULT '2',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_schedule` (`merchant_id`,`provider_code`,`channel_code`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settlement_sweep_runs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `schedule_id` bigint unsigned NOT NULL,
  `run_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `run_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPENED',
  `sweep_amount` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `message` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_sweep_run` (`run_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shedlock` (
  `name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lock_until` timestamp(3) NOT NULL,
  `locked_at` timestamp(3) NOT NULL,
  `locked_by` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transfer_intents` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `intent_reference` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `quote_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_country` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_country` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_amount` decimal(19,4) NOT NULL,
  `target_amount` decimal(19,4) DEFAULT NULL,
  `beneficiary_account` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `beneficiary_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `intent_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CREATED',
  `risk_decision` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_intent_reference` (`intent_reference`),
  KEY `idx_transfer_intent_merchant` (`merchant_id`,`intent_status`),
  KEY `idx_transfer_intent_quote` (`quote_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `treasury_positions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `available_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `reserved_balance` decimal(19,4) NOT NULL DEFAULT '0.0000',
  `position_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_treasury_position_currency` (`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_assets` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `device_id` bigint unsigned DEFAULT NULL,
  `asset_code` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_type` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `slot_number` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AVAILABLE',
  `battery_percent` int DEFAULT NULL,
  `last_seen_at` timestamp NULL DEFAULT NULL,
  `metadata_json` json DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_asset_tenant_code` (`merchant_id`,`asset_code`),
  KEY `idx_vending_asset_tenant_device` (`merchant_id`,`device_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_callback_nonces` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `connector_code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nonce_value` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_callback_nonce` (`merchant_id`,`connector_code`,`nonce_value`),
  KEY `idx_vending_callback_nonce_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_commands` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `device_id` bigint unsigned NOT NULL,
  `rental_id` bigint unsigned DEFAULT NULL,
  `command_reference` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `command_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `connector_code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `provider_reference` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_json` json DEFAULT NULL,
  `response_json` json DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_command_ref` (`command_reference`),
  KEY `idx_vending_command_tenant_status` (`merchant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_connector_configs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `connector_code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `command_base_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `release_path` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `release_request_template` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `auth_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BEARER',
  `auth_header_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `auth_timestamp_header` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `auth_key_header` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `auth_signature_encoding` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BASE64',
  `auth_signing_template` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `auth_value_ciphertext` text COLLATE utf8mb4_unicode_ci,
  `auth_secret_ciphertext` text COLLATE utf8mb4_unicode_ci,
  `response_success_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_success_value` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_reference_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_message_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `callback_secret_ciphertext` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `callback_signature_mode` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'HMAC_SHA256_TS_NONCE_BODY',
  `callback_signature_encoding` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BASE64',
  `callback_signature_header` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'X-CPay-Vending-Signature',
  `callback_timestamp_header` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'X-CPay-Vending-Timestamp',
  `callback_nonce_header` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'X-CPay-Vending-Nonce',
  `callback_event_type_field` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'eventType',
  `callback_event_id_field` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'eventId',
  `callback_device_field` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'deviceId',
  `callback_rental_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `callback_command_reference_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `callback_provider_reference_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `callback_asset_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `callback_available_count_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `callback_heartbeat_value` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'HEARTBEAT',
  `callback_return_value` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ASSET_RETURNED',
  `callback_release_value` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ASSET_RELEASED',
  `callback_offline_value` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DEVICE_OFFLINE',
  `active_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NO',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_connector_tenant_code` (`merchant_id`,`connector_code`),
  KEY `idx_vending_connector_active` (`merchant_id`,`active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_connector_operations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `connector_code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `command_type` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `http_method` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'POST',
  `command_path` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_template` text COLLATE utf8mb4_unicode_ci,
  `idempotency_header_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_success_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_success_value` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_reference_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_message_field` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completion_mode` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CALLBACK',
  `active_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_connector_operation` (`merchant_id`,`connector_code`,`command_type`),
  KEY `idx_vending_connector_operation_active` (`merchant_id`,`connector_code`,`active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_customer_balances` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `customer_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `surcharge_balance` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `blocked_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NO',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_customer_balance` (`merchant_id`,`customer_hash`,`currency`),
  KEY `idx_vending_customer_blocked` (`merchant_id`,`blocked_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_device_callbacks` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `connector_code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `external_event_id` varchar(220) COLLATE utf8mb4_unicode_ci NOT NULL,
  `external_device_id` varchar(220) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `event_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `body_sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `signature_status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `processing_status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RECEIVED',
  `raw_body` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `processed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_device_callback_event` (`merchant_id`,`connector_code`,`external_event_id`),
  KEY `idx_vending_device_callback_status` (`merchant_id`,`processing_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_devices` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `location_id` bigint unsigned DEFAULT NULL,
  `pricing_policy_id` bigint unsigned DEFAULT NULL,
  `device_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `device_type` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `connector_code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SIMULATED',
  `external_device_id` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `public_token` varchar(96) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REGISTERED',
  `slot_count` int NOT NULL DEFAULT '0',
  `available_count` int NOT NULL DEFAULT '0',
  `heartbeat_at` timestamp NULL DEFAULT NULL,
  `metadata_json` json DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_device_tenant_code` (`merchant_id`,`device_code`),
  UNIQUE KEY `uk_vending_device_public_token` (`public_token`),
  KEY `idx_vending_device_tenant_location` (`merchant_id`,`location_id`),
  KEY `idx_vending_device_tenant_status` (`merchant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_events` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `event_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_reference` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount` decimal(20,4) DEFAULT NULL,
  `currency` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detail_json` json DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vending_event_tenant_time` (`merchant_id`,`created_at`),
  KEY `idx_vending_event_entity` (`merchant_id`,`entity_type`,`entity_reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_hosted_sessions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `device_id` bigint unsigned NOT NULL,
  `rental_reference` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `session_token_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `expires_at` timestamp NOT NULL,
  `last_seen_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_hosted_session_token` (`session_token_hash`),
  UNIQUE KEY `uk_vending_hosted_session_rental` (`merchant_id`,`rental_reference`),
  KEY `idx_vending_hosted_session_expiry` (`expires_at`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_locations` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `location_code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `latitude` decimal(10,7) DEFAULT NULL,
  `longitude` decimal(10,7) DEFAULT NULL,
  `business_hours_json` json DEFAULT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_location_tenant_code` (`merchant_id`,`location_code`),
  KEY `idx_vending_location_tenant_status` (`merchant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_pricing_policies` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `policy_code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `deposit_amount` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `free_minutes` int NOT NULL DEFAULT '0',
  `unit_price` decimal(20,4) NOT NULL,
  `billing_block_minutes` int NOT NULL DEFAULT '60',
  `minimum_billing_blocks` int NOT NULL DEFAULT '1',
  `daily_cap_amount` decimal(20,4) DEFAULT NULL,
  `overtime_amount` decimal(20,4) DEFAULT NULL,
  `overtime_days` int DEFAULT NULL,
  `refund_mode` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ORIGINAL_ROUTE',
  `active_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YES',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_pricing_tenant_code` (`merchant_id`,`policy_code`),
  KEY `idx_vending_pricing_tenant_active` (`merchant_id`,`active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vending_rentals` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `rental_reference` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `device_id` bigint unsigned NOT NULL,
  `asset_id` bigint unsigned DEFAULT NULL,
  `pricing_policy_id` bigint unsigned NOT NULL,
  `customer_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_mask` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_ciphertext` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `channel_code` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `deposit_amount` decimal(20,4) NOT NULL,
  `surcharge_settled_from_deposit` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `escrow_amount` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `usage_amount` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `refund_amount` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `surcharge_created` decimal(20,4) NOT NULL DEFAULT '0.0000',
  `billed_blocks` int NOT NULL DEFAULT '0',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `collect_reference` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `collect_transaction_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refund_reference` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refund_transaction_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` timestamp NULL DEFAULT NULL,
  `ended_at` timestamp NULL DEFAULT NULL,
  `billing_suspended_at` timestamp NULL DEFAULT NULL,
  `billing_suspended_seconds` bigint NOT NULL DEFAULT '0',
  `bill_suspended_time_flag` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NO',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vending_rental_tenant_ref` (`merchant_id`,`rental_reference`),
  UNIQUE KEY `uk_vending_rental_collect_ref` (`collect_reference`),
  UNIQUE KEY `uk_vending_rental_refund_ref` (`refund_reference`),
  KEY `idx_vending_rental_tenant_status` (`merchant_id`,`status`,`created_at`),
  KEY `idx_vending_rental_customer` (`merchant_id`,`customer_hash`,`status`),
  KEY `idx_vending_rental_device` (`merchant_id`,`device_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `verified_profiles` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `identity_number_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `identity_number_mask` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `full_name_mask` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `msisdn_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `msisdn_mask` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `verification_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `verified_service` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'gnugrid',
  `provider_reference` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `verified_at` timestamp NULL DEFAULT NULL,
  `expires_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_verified_profile_identity` (`identity_number_hash`),
  KEY `idx_verified_profile_merchant` (`merchant_id`,`verification_status`),
  KEY `idx_verified_profile_lookup` (`identity_number_hash`,`verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
