-- Audit I3: KYC-tier-bound transaction and daily caps for the risk rule engine.
--
-- Tier values come from compliance_profiles (profile_type='KYC', tier in
-- STARTER/BUSINESS/ENHANCED, default STANDARD) - see V9. RiskDecisionService now
-- resolves tier:<TIER> scoped rules with precedence MERCHANT > TIER > GLOBAL, so
-- these defaults bind any merchant without a per-merchant override. Seeded values
-- follow the compliance roadmap's tier model: Starter is a low daily cap with a
-- review (not block) on single transactions to keep friction visible, Business is
-- a medium cap, Enhanced is the high default where finance has signed off.

INSERT IGNORE INTO `risk_rules`
  (`rule_key`, `rule_type`, `scope_type`, `scope_reference`, `currency`, `threshold_amount`, `decision`, `enabled`)
VALUES
  ('tier-starter-single-review-ugx', 'SINGLE_TRANSACTION_CAP', 'TIER', 'tier:STARTER', 'UGX', 1000000.0000, 'REVIEW', 'YES'),
  ('tier-starter-daily-block-ugx',       'MERCHANT_DAILY_CAP',   'TIER', 'tier:STARTER', 'UGX',  5000000.0000, 'BLOCK',  'YES'),
  ('tier-business-single-review-ugx',    'SINGLE_TRANSACTION_CAP', 'TIER', 'tier:BUSINESS', 'UGX', 5000000.0000, 'REVIEW', 'YES'),
  ('tier-business-daily-block-ugx',      'MERCHANT_DAILY_CAP',   'TIER', 'tier:BUSINESS', 'UGX', 30000000.0000, 'BLOCK',  'YES'),
  ('tier-enhanced-single-review-ugx',    'SINGLE_TRANSACTION_CAP', 'TIER', 'tier:ENHANCED', 'UGX', 20000000.0000, 'REVIEW', 'YES'),
  ('tier-enhanced-daily-block-ugx',      'MERCHANT_DAILY_CAP',   'TIER', 'tier:ENHANCED', 'UGX', 100000000.0000, 'BLOCK', 'YES');
