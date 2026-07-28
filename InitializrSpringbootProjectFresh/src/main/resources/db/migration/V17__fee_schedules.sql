-- Audit A5: versioned, effective-dated fee schedules with optional per-merchant overrides,
-- replacing the flat global settings keys (gw_<provider>_api_customer_charge_inbound etc.) that
-- had no history and no way to schedule a future rate change.
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
