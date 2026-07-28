CREATE TABLE admins (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name varchar(255) NOT NULL DEFAULT '',
    email varchar(255) NOT NULL DEFAULT '',
    phone varchar(255) NOT NULL DEFAULT '',
    password varchar(255) not null default '',
    created_on datetime not null default CURRENT_TIMESTAMP,
    updated_on timestamp not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    status ENUM ('ACTIVE','SUSPENDED', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY `unique_user` (`email`)
) Engine = Innodb;

CREATE TABLE admin_privileges (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT UNSIGNED,
    privilege varchar(255) NOT NULL DEFAULT '',
    created_on datetime not null default CURRENT_TIMESTAMP,
    updated_on timestamp not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `unique_admin_priv`(`admin_id`, `privilege`),
    FOREIGN KEY (admin_id) REFERENCES `admins`(`id`) ON DELETE CASCADE
) ENGINE = INNODB;

/*Add a User*/
INSERT INTO admins (name, email, phone, password, status) VALUE("Super Admin", "joseph.tabajjwa@gmail.com", "256783086794", password("admin"), "ACTIVE");
/*Add user's privileges*/
INSERT INTO admin_privileges(admin_id, privilege) VALUES (1, 'CREATE_MERCHANT'),
(1, 'UPDATE_MERCHANT'),
(1, 'UPDATED_MERCHANT'),
(1, 'ACCESS_SETTINGS'),
(1, 'UPDATE_SETTINGS'),
(1, 'ACCESS_REPORTS'),
(1, 'ACTIVATE_MERCHANT'),
(1, 'CREDIT_MERCHANT'),
(1, 'DEBIT_MERCHANT');
INSERT INTO admin_privileges(admin_id, privilege) VALUES (1, 'ACCESS_ADMIN'),
(1, 'UPDATE_ADMIN');

CREATE TABLE audit_trail (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_name varchar(255) not null default '',
    user_id varchar(255) not null default '',
    action TEXT,
    created_on datetime not null default CURRENT_TIMESTAMP,
    updated_on timestamp not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = INNODB;

/*Stores Merchant Basic Info*/
CREATE TABLE merchants (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name varchar(255) NOT NULL DEFAULT '',
    status ENUM ('ACTIVE','SUSPENDED', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    account_number varchar(255) NOT NULL DEFAULT '',
    created_on datetime not null default CURRENT_TIMESTAMP,
    updated_on timestamp not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT '',
    UNIQUE KEY  `unique_id` (account_number)
) Engine = Innodb;
ALTER TABLE merchants ADD COLUMN account_type ENUM('business', 'personal') NOT NULL DEFAULT 'personal';

/*STORES merchants users who can login to Merchant portal*/
CREATE TABLE merchant_admins (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT UNSIGNED,
    name varchar(255) NOT NULL DEFAULT '',
    email varchar(255) NOT NULL DEFAULT '',
    phone varchar(255) NOT NULL DEFAULT '',
    password varchar(255) not null default '',
    created_on datetime not null default CURRENT_TIMESTAMP,
    updated_on timestamp not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    status ENUM ('ACTIVE','SUSPENDED', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY `unique_user` (`email`),
    FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE CASCADE
) Engine = Innodb;

/*Stored Charging Details*/
CREATE TABLE charging_details (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    gateway_id VARCHAR(255) NOT NULL DEFAULT '',
    service ENUM('PAYIN', 'PAYOUT') NOT NULL DEFAULT 'PAYIN',
    amount double NOT NULL DEFAULT 0,
    charging_method ENUM('PERCENTAGE', 'FLAT_FEE', 'TIER') NOT NULL DEFAULT "PERCENTAGE",
    UNIQUE KEY `unique_charge`(`gateway_id`, `service`)
) Engine=Innodb;

/*Merchant Transaction Log*/
CREATE TABLE merchant_transactions_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT UNSIGNED,
    gateway_id VARCHAR(255) NOT NULL DEFAULT '',
    original_amount double not null default 0,
    charges double not null default 0,
    status enum('SUCCESS', 'FAILED', 'PENDING', 'UNDETERMINED') DEFAULT 'PENDING',
    charging_method VARCHAR(255),
    tx_request_trace blob,
    tx_update_trace blob,
    tx_description text,
    tx_merchant_description text, /*stores merchant description*/
    tx_unique_id varchar(255) NOT NULL, /*Our Gateway ID*/
    tx_gateway_ref varchar(255) NOT NULL,/*Stores network reference*/
    tx_merchant_ref varchar(255) NULL,
    created_on datetime not null default CURRENT_TIMESTAMP,
    updated_on timestamp not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `unique_merchant_id`(`merchant_id`, `tx_merchant_ref`),
    UNIQUE KEY (`tx_unique_id`)
) Engine = Innodb;
ALTER TABLE merchant_transactions_log ADD COLUMN payer_number VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE merchant_transactions_log ADD FOREIGN KEY (merchant_id) REFERENCES merchants(id) ON DELETE SET NULL;


/*Merchant Statement (Always the last row keeps the available balance*/
CREATE TABLE merchant_statement(
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT UNSIGNED,
    transactions_log_id BIGINT UNSIGNED NULL,
    gateway_id VARCHAR(255) NOT NULL DEFAULT '',
    description text,
    amount double not null default 0,
    balance double not null default 0,
    tx_type ENUM('CR', 'DR') NOT NULL DEFAULT 'CR',
    created_on datetime not null default CURRENT_TIMESTAMP,
    updated_on timestamp not null default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (transactions_log_id) REFERENCES merchant_transactions_log (id) ON DELETE SET NULL
) Engine = Innodb;

ALTER TABLE admins ADD COLUMN email_verification_code VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE admins ADD COLUMN email_verification_sent_on DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;



/*settings Table to store settings*/
CREATE TABLE settings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    label VARCHAR(255) NOT NULL DEFAULT '',
    name VARCHAR(255) NOT NULL DEFAULT '',
    setting_value VARCHAR(255) NOT NULL DEFAULT '',
    description VARCHAR(255) NOT NULL DEFAULT '',
    UNIQUE KEY (name)
) Engine =Innodb;
ALTER TABLE settings ADD COLUMN setting_group VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE settings CHANGE setting_value setting_value TEXT;

ALTER TABLE audit_trail CHANGE reated_on created_on timestamp not null default CURRENT_TIMESTAMP;


ALTER TABLE merchant_admins ADD COLUMN `email_verification_code` varchar(255) NOT NULL DEFAULT '';
ALTER TABLE merchant_admins ADD COLUMN `email_verification_sent_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP;


ALTER TABLE merchant_transactions_log ADD COLUMN tx_type ENUM('PAYOUT', 'PAYIN') NOT NULL DEFAULT 'PAYIN';




/*
* To store batch processing transactions
*/
CREATE TABLE `merchant_batch_transactions_log` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint(20) unsigned DEFAULT NULL,
  `total_amount` double NOT NULL DEFAULT '0',
  `total_charges` double NOT NULL DEFAULT '0',
  `status` enum('PENDING','PROCESSING','PAUSED','DONE', 'STOPPED') DEFAULT 'PENDING',
  `tx_description` text,
  `batch_id` varchar(255) NOT NULL,
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `batch_id` (`batch_id`),
  FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB;

ALTER TABLE merchant_transactions_log ADD COLUMN merchant_batch_transactions_log_id BIGINT UNSIGNED NULL;
ALTER TABLE merchant_transactions_log ADD FOREIGN KEY (merchant_batch_transactions_log_id) REFERENCES `merchant_batch_transactions_log`(`id`) ON DELETE SET NULL;


CREATE TABLE `merchant_admin_privileges` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` bigint(20) unsigned DEFAULT NULL,
  `privilege` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_admin_priv` (`admin_id`,`privilege`),
  FOREIGN KEY (`admin_id`) REFERENCES `merchant_admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB;

ALTER TABLE merchant_statement CHANGE balance mtnmm_balance double not null default 0;
ALTER TABLE merchant_statement ADD COLUMN airtelmm_balance double not null default 0;

ALTER TABLE merchant_admins DROP KEY `unique_user`;
ALTER TABLE merchant_admins ADD UNIQUE KEY `unique_merchant_user`(`merchant_id`, `email`);
ALTER TABLE merchant_statement ADD COLUMN narrative VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE merchant_statement ADD COLUMN recorded_by VARCHAR(255) NOT NULL DEFAULT '';


ALTER TABLE merchants ADD COLUMN public_key BLOB DEFAULT NULL;
ALTER TABLE merchants ADD COLUMN private_key BLOB DEFAULT NULL;
ALTER TABLE merchant_transactions_log ADD COLUMN tx_cost DOUBLE NOT NULL DEFAULT 0;

ALTER TABLE merchant_transactions_log ADD COLUMN callback_url VARCHAR(255) NOT NULL DEFAULT "";
ALTER TABLE merchants ADD COLUMN allowed_apis TEXT NULL;

ALTER TABLE merchant_transactions_log ADD UNIQUE KEY `unique_merchant_tx`(`merchant_id`, `tx_merchant_ref`);
ALTER TABLE merchant_transactions_log ADD INDEX (`tx_merchant_ref`);

ALTER TABLE merchant_transactions_log ADD COLUMN callback_trace TEXT NULL;


ALTER TABLE merchant_transactions_log CHANGE `status` `status` enum('SUCCESSFUL','FAILED','PENDING','UNDETERMINED') DEFAULT 'PENDING';




CREATE TABLE `merchants_audit_trail` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned null,
  `user_name` varchar(255) NOT NULL DEFAULT '',
  `user_id` varchar(255) NOT NULL DEFAULT '',
  `action` text,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB;
ALTER TABLE merchants_audit_trail ADD INDEX (merchant_id);

ALTER TABLE merchant_batch_transactions_log ADD COLUMN name varchar(255) not null default '';
ALTER TABLE merchant_transactions_log ADD COLUMN name VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE merchant_transactions_log ADD COLUMN account_type VARCHAR(255) NOT NULL DEFAULT 'phone';

CREATE TABLE beneficiaries (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT UNSIGNED NULL,
    name VARCHAR(255) NOT NULL DEFAULT '',
    account VARCHAR(255) NOT NULL DEFAULT '',
    status VARCHAR(255) NOT NULL DEFAULT '',
    amount double not null default 0,
    FOREIGN KEY (`batch_id`) REFERENCES `merchant_batch_transactions_log`(`id`) ON DELETE CASCADE,
    UNIQUE KEY `UNIQUE_PAYMENT_ACCOUNT`(`batch_id`,`account`)
) Engine = Innodb;

ALTER TABLE merchant_transactions_log ADD COLUMN beneficiary_id BIGINT UNSIGNED NULL;
ALTER TABLE merchant_transactions_log ADD FOREIGN KEY (`beneficiary_id`) REFERENCES `beneficiaries`(`id`) ON DELETE SET NULL;

ALTER TABLE merchant_batch_transactions_log ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE beneficiaries ADD COLUMN account_type VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE beneficiaries ADD COLUMN reason VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE merchants ADD COLUMN short_name VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE merchant_transactions_log ADD COLUMN resolved_by VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE merchant_transactions_log DROP COLUMN resolved_by;


ALTER TABLE merchant_transactions_log ADD COLUMN source_ip_address VARCHAR(255) NOT NULL DEFAULT '';

CREATE TABLE `db_changes` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `query_id` varchar(255) NOT NULL DEFAULT '',
    `sql_text` text,
    `roll_back` text,
    `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `query_id` (`query_id`)
) ENGINE=InnoDB;


CREATE TABLE merchant_sms (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `merchant_id` BIGINT UNSIGNED,
    `charge` DOUBLE NOT NULL DEFAULT 0,
    `cost` DOUBLE NOT NULL DEFAULT 0,
    `total_recipients` INT DEFAULT 0,
    `status` VARCHAR(255) NOT NULL DEFAULT '',
    `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `trace` MEDIUMBLOB,
    `content` TEXT, 
    `recipients` MEDIUMBLOB,
    `gw_response` MEDIUMBLOB,
    `smsgw` VARCHAR(255) NOT NULL DEFAULT '',
    FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB;

DROP TABLE IF EXISTS merchant_sms;

ALTER TABLE merchant_sms ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE merchant_sms DROP COLUMN created_by;

ALTER TABLE merchant_statement ADD COLUMN sms_balance DOUBLE NOT NULL DEFAULT 0;
ALTER TABLE merchant_statement DROP COLUMN sms_balance 

ALTER TABLE merchant_sms ADD COLUMN send_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE merchant_sms DROP COLUMN send_time;

ALTER TABLE merchant_sms ADD COLUMN total_amount DOUBLE NOT NULL DEFAULT 0;
ALTER TABLE merchant_sms DROP COLUMN total_amount;

ALTER TABLE merchant_statement ADD COLUMN sms_balance DOUBLE NOT NULL DEFAULT 0;
ALTER TABLE merchant_statement DROP COLUMN sms_balance;


CREATE TABLE `merchant_settings` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `merchant_id` bigint unsigned,
    `label` varchar(255) NOT NULL DEFAULT '',
    `name` varchar(255) NOT NULL DEFAULT '',
    `setting_value` text,
    `description` varchar(255) NOT NULL DEFAULT '',
    `setting_group` varchar(255) NOT NULL DEFAULT '',
    PRIMARY KEY (`id`),
    UNIQUE KEY `unique_merchant_setting_name` (`merchant_id`,`name`),
    FOREIGN KEY (`merchant_id`) REFERENCES `merchants`(`id`) ON DELETE CASCADE  
) ENGINE=InnoDB;

DROP TABLE IF EXISTS merchant_settings;


CREATE TABLE `accounts_register` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
    `account` varchar(255) NOT NULL DEFAULT '',
    `first_name` varchar(255) NOT NULL DEFAULT '',
    `last_name` varchar(255) NOT NULL DEFAULT '',
    `address` varchar(255) NOT NULL DEFAULT '',
    `dob` varchar(255) NOT NULL DEFAULT '',
    `account_type` enum ('MSISDN', 'EMAIL') NOT NULL DEFAULT 'MSISDN',
    PRIMARY KEY (`id`),
    UNIQUE KEY `unique_account_name` (`account`,`account_type`)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS accounts_register;

ALTER TABLE `accounts_register` ADD COLUMN provided_name VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE `accounts_register` DROP COLUMN provided_name;

ALTER TABLE `accounts_register` ADD INDEX (`merchant_id`);
ALTER TABLE `accounts_register` DROP KEY `merchant_id`;

ALTER TABLE `accounts_register` ADD UNIQUE KEY (`account`);
ALTER TABLE `accounts_register` DROP KEY `account`;

ALTER TABLE `accounts_register` DROP KEY `unique_account_name`;
ALTER TABLE `accounts_register` ADD UNIQUE KEY `unique_account_name`(`account`,`account_type`);
