-- C1: DB-backed replacement for SafariComPaymentGateway's plaintext on-disk
-- ConversationID -> transaction reference correlation file.
--
-- SafariComPaymentGateway.checkStatus() issues a Safaricom TransactionStatusQuery for a
-- disbursement and registers this app's /doSafaricomPayOutCallback endpoint as the async
-- ResultURL/QueueTimeOutURL. Safaricom's callback to that endpoint only carries the
-- ConversationID it minted for the query - not our own transaction reference - so
-- Api.getPayoutConversationIdToken must resolve ConversationID back to our reference to look
-- up and update the right transaction, then remove the mapping once processed
-- (Api.getPayoutConversationIdDeleteFile).
--
-- This mapping used to live in a per-instance local file under custom.lockfiledirectory,
-- which doesn't survive across multiple app instances/pods and isn't the DB-backed pattern
-- already used for provider tokens (see provider_tokens / ProviderTokenStoreService).
CREATE TABLE IF NOT EXISTS `provider_conversation_references` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `provider_code` VARCHAR(100) NOT NULL,
  `conversation_id` VARCHAR(191) NOT NULL,
  `tx_reference` VARCHAR(191) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_conversation` (`provider_code`, `conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
