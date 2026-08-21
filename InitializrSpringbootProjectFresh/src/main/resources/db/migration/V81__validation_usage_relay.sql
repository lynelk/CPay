-- CPay Identification & Validation Module: validation usage relay flag.
-- Additive. Adds a relayed-flag to validation_usage so the billing relay (pattern mirrors
-- communication_message_deliveries.billed_flag) can sweep un-relayed billable attempts
-- without conflating "already relayed" with "never billable" (which billable_attempt means).

ALTER TABLE validation_usage
  ADD COLUMN relayed_flag CHAR(1) NOT NULL DEFAULT 'N' AFTER billable_attempt;

CREATE INDEX idx_validation_usage_relay
  ON validation_usage (relayed_flag, id);

-- Single-row cursor table mirrors communication_usage_watermark; the ShedLock-guarded
-- ValidationUsageRelay advances last_usage_id in bounded batches.
CREATE TABLE IF NOT EXISTS validation_usage_watermark (
  id BIGINT NOT NULL PRIMARY KEY,
  last_usage_id BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
