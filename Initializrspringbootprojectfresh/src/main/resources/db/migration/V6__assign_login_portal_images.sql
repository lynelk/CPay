UPDATE `settings`
SET `setting_value`='https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=1600&q=80'
WHERE `name`='merchant_login_hero_image_url'
  AND (`setting_value`='' OR `setting_value`='https://images.unsplash.com/photo-1551836022-d5d88e9218df?auto=format&fit=crop&w=1600&q=80');

UPDATE `settings`
SET `setting_value`='https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=1600&q=80'
WHERE `name`='admin_login_hero_image_url'
  AND (`setting_value`='' OR `setting_value`='https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=1600&q=80');

INSERT INTO `settings` (`name`, `label`, `setting_value`, `setting_group`, `description`) VALUES
('merchant_login_benefit_insights_title', 'Customer Login Insights Benefit Title', 'Real-time Insights', 'Login Portal', 'Title for the insights benefit tile on the customer login portal.'),
('merchant_login_benefit_insights_copy', 'Customer Login Insights Benefit Copy', 'Data-driven decisions', 'Login Portal', 'Copy for the insights benefit tile on the customer login portal.'),
('merchant_login_control_title', 'Customer Login Control Benefit Title', 'Operational Control', 'Login Portal', 'Title for the operational control benefit tile on the customer login portal.'),
('merchant_login_control_copy', 'Customer Login Control Benefit Copy', 'Manage with confidence', 'Login Portal', 'Copy for the operational control benefit tile on the customer login portal.'),
('merchant_login_automation_title', 'Customer Login Automation Benefit Title', 'Automation Ready', 'Login Portal', 'Title for the automation benefit tile on the customer login portal.'),
('merchant_login_automation_copy', 'Customer Login Automation Benefit Copy', 'Powerful tools for efficiency', 'Login Portal', 'Copy for the automation benefit tile on the customer login portal.')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

UPDATE `settings` SET `setting_value`='Merchant operations workspace'
WHERE `name`='merchant_login_hero_title' AND `setting_value`='Merchant workspace';
UPDATE `settings` SET `setting_value`='Secure access to payments, insights, and support in one place.'
WHERE `name`='merchant_login_hero_copy' AND `setting_value`='Account access, balances, and activity in one place.';
UPDATE `settings` SET `setting_value`='Secure Platform'
WHERE `name`='merchant_login_secure_title' AND `setting_value`='Secure';
UPDATE `settings` SET `setting_value`='Enterprise-grade protection'
WHERE `name`='merchant_login_secure_copy' AND `setting_value`='Your data is protected';
UPDATE `settings` SET `setting_value`='Reliable & Scalable'
WHERE `name`='merchant_login_reliable_title' AND `setting_value`='Reliable';
UPDATE `settings` SET `setting_value`='Built for growth and trust'
WHERE `name`='merchant_login_reliable_copy' AND `setting_value`='Trusted by businesses';

UPDATE `settings` SET `setting_value`='Powerful control. Smarter operations.'
WHERE `name`='admin_login_hero_title' AND `setting_value`='Admin workspace';
UPDATE `settings` SET `setting_value`='Manage your platform, users, and transactions with confidence and clarity.'
WHERE `name`='admin_login_hero_copy' AND `setting_value`='Approvals, user management, analytics, and controls in one place.';
UPDATE `settings` SET `setting_value`='Secure Platform'
WHERE `name`='admin_login_approvals_title' AND `setting_value`='Approvals';
UPDATE `settings` SET `setting_value`='User & Role Management'
WHERE `name`='admin_login_users_title' AND `setting_value`='User Management';
UPDATE `settings` SET `setting_value`='Real-time Analytics'
WHERE `name`='admin_login_analytics_title' AND `setting_value`='Analytics';
UPDATE `settings` SET `setting_value`='System Management'
WHERE `name`='admin_login_merchant_title' AND `setting_value`='Merchant Oversight';
UPDATE `settings` SET `setting_value`='Audit & Compliance'
WHERE `name`='admin_login_security_title' AND `setting_value`='Security';
UPDATE `settings` SET `setting_value`='Secure'
WHERE `name`='admin_login_secure_title' AND `setting_value`='Secure Platform';
UPDATE `settings` SET `setting_value`='Protect platform and data'
WHERE `name`='admin_login_secure_copy' AND `setting_value`='Enterprise-grade protection';
UPDATE `settings` SET `setting_value`='Reliable'
WHERE `name`='admin_login_insights_title' AND `setting_value`='Real-time Insights';
UPDATE `settings` SET `setting_value`='High availability and performance'
WHERE `name`='admin_login_insights_copy' AND `setting_value`='Data-driven decisions';
UPDATE `settings` SET `setting_value`='Insightful'
WHERE `name`='admin_login_control_title' AND `setting_value`='Operational Control';
UPDATE `settings` SET `setting_value`='Real-time reports and analytics'
WHERE `name`='admin_login_control_copy' AND `setting_value`='Manage with confidence';
UPDATE `settings` SET `setting_value`='Efficient'
WHERE `name`='admin_login_automation_title' AND `setting_value`='Automation Ready';
UPDATE `settings` SET `setting_value`='Automate and simplify operations'
WHERE `name`='admin_login_automation_copy' AND `setting_value`='Powerful tools for efficiency';
UPDATE `settings` SET `setting_value`='Compliant'
WHERE `name`='admin_login_reliable_title' AND `setting_value`='Reliable & Scalable';
UPDATE `settings` SET `setting_value`='Meet regulatory requirements'
WHERE `name`='admin_login_reliable_copy' AND `setting_value`='Built for growth and trust';
