CREATE TABLE IF NOT EXISTS `ledger_reservation_controls` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `merchant_id` BIGINT UNSIGNED NOT NULL,
  `currency` VARCHAR(10) NOT NULL,
  `control_status` VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_reservation_control_scope` (`merchant_id`, `currency`),
  KEY `idx_ledger_reservation_control_status` (`control_status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `ledger_reservations`
  ADD KEY `idx_ledger_reservation_scope_status` (`merchant_id`, `currency`, `reservation_status`);