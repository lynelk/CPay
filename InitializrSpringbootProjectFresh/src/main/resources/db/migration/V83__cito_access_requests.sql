-- Cito unified access gateway: privileged/public access requests.
-- Public requests are intentionally request-only. They never create a user or grant a role.

CREATE TABLE IF NOT EXISTS cito_access_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_reference VARCHAR(64) NOT NULL,
  full_name VARCHAR(160) NOT NULL,
  work_email VARCHAR(254) NOT NULL,
  organization VARCHAR(200) NOT NULL,
  requested_access_type VARCHAR(40) NOT NULL,
  reason VARCHAR(2000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  source_ip_hash VARCHAR(64) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  reviewed_at TIMESTAMP NULL,
  reviewed_by VARCHAR(160) NULL,
  review_notes VARCHAR(2000) NULL,
  UNIQUE KEY uk_cito_access_request_reference (request_reference),
  KEY idx_cito_access_request_status_created (status, created_at),
  KEY idx_cito_access_request_email_status (work_email, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
