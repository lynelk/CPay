-- MySQL dump 10.13  Distrib 5.7.29, for osx10.14 (x86_64)
--
-- Host: localhost    Database: cpayadmin
-- ------------------------------------------------------
-- Server version	5.7.29

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `SPRING_SESSION`
--

DROP TABLE IF EXISTS `SPRING_SESSION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SPRING_SESSION` (
  `PRIMARY_ID` char(36) NOT NULL,
  `SESSION_ID` char(36) NOT NULL,
  `CREATION_TIME` bigint(20) NOT NULL,
  `LAST_ACCESS_TIME` bigint(20) NOT NULL,
  `MAX_INACTIVE_INTERVAL` int(11) NOT NULL,
  `EXPIRY_TIME` bigint(20) NOT NULL,
  `PRINCIPAL_NAME` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`PRIMARY_ID`),
  UNIQUE KEY `SPRING_SESSION_IX1` (`SESSION_ID`),
  KEY `SPRING_SESSION_IX2` (`EXPIRY_TIME`),
  KEY `SPRING_SESSION_IX3` (`PRINCIPAL_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `SPRING_SESSION_ATTRIBUTES`
--

DROP TABLE IF EXISTS `SPRING_SESSION_ATTRIBUTES`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SPRING_SESSION_ATTRIBUTES` (
  `SESSION_PRIMARY_ID` char(36) NOT NULL,
  `ATTRIBUTE_NAME` varchar(200) NOT NULL,
  `ATTRIBUTE_BYTES` blob NOT NULL,
  PRIMARY KEY (`SESSION_PRIMARY_ID`,`ATTRIBUTE_NAME`),
  CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK` FOREIGN KEY (`SESSION_PRIMARY_ID`) REFERENCES `SPRING_SESSION` (`PRIMARY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admin_privileges`
--

DROP TABLE IF EXISTS `admin_privileges`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `admin_privileges` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` bigint(20) unsigned DEFAULT NULL,
  `privilege` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_admin_priv` (`admin_id`,`privilege`),
  CONSTRAINT `admin_privileges_ibfk_1` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=171 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admins`
--

DROP TABLE IF EXISTS `admins`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `admins` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
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
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `audit_trail`
--

DROP TABLE IF EXISTS `audit_trail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `audit_trail` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `user_name` varchar(255) NOT NULL DEFAULT '',
  `user_id` varchar(255) NOT NULL DEFAULT '',
  `action` text,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=115 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `beneficiaries`
--

DROP TABLE IF EXISTS `beneficiaries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `beneficiaries` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `batch_id` bigint(20) unsigned DEFAULT NULL,
  `name` varchar(255) NOT NULL DEFAULT '',
  `account` varchar(255) NOT NULL DEFAULT '',
  `status` varchar(255) NOT NULL DEFAULT '',
  `amount` double NOT NULL DEFAULT '0',
  `account_type` varchar(255) NOT NULL DEFAULT '',
  `reason` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UNIQUE_PAYMENT_ACCOUNT` (`batch_id`,`account`),
  CONSTRAINT `beneficiaries_ibfk_1` FOREIGN KEY (`batch_id`) REFERENCES `merchant_batch_transactions_log` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=123 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `charging_details`
--

DROP TABLE IF EXISTS `charging_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `charging_details` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gateway_id` varchar(255) NOT NULL DEFAULT '',
  `service` enum('PAYIN','PAYOUT') NOT NULL DEFAULT 'PAYIN',
  `amount` double NOT NULL DEFAULT '0',
  `charging_method` enum('PERCENTAGE','FLAT_FEE','TIER') NOT NULL DEFAULT 'PERCENTAGE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_charge` (`gateway_id`,`service`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `db_changes`
--

DROP TABLE IF EXISTS `db_changes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `db_changes` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `query_id` varchar(255) NOT NULL DEFAULT '',
  `sql_text` text,
  `roll_back` text,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `query_id` (`query_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchant_admin_privileges`
--

DROP TABLE IF EXISTS `merchant_admin_privileges`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `merchant_admin_privileges` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` bigint(20) unsigned DEFAULT NULL,
  `privilege` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_admin_priv` (`admin_id`,`privilege`),
  CONSTRAINT `merchant_admin_privileges_ibfk_1` FOREIGN KEY (`admin_id`) REFERENCES `merchant_admins` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=324 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchant_admins`
--

DROP TABLE IF EXISTS `merchant_admins`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `merchant_admins` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint(20) unsigned DEFAULT NULL,
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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchant_batch_transactions_log`
--

DROP TABLE IF EXISTS `merchant_batch_transactions_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `merchant_batch_transactions_log` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint(20) unsigned DEFAULT NULL,
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
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchant_settings`
--

DROP TABLE IF EXISTS `merchant_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `merchant_settings` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint(20) unsigned DEFAULT NULL,
  `label` varchar(255) NOT NULL DEFAULT '',
  `name` varchar(255) NOT NULL DEFAULT '',
  `setting_value` text,
  `description` varchar(255) NOT NULL DEFAULT '',
  `setting_group` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_merchant_setting_name` (`merchant_id`,`name`),
  CONSTRAINT `merchant_settings_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchant_sms`
--

DROP TABLE IF EXISTS `merchant_sms`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `merchant_sms` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint(20) unsigned DEFAULT NULL,
  `charge` double NOT NULL DEFAULT '0',
  `cost` double NOT NULL DEFAULT '0',
  `total_recipients` int(11) DEFAULT '0',
  `status` varchar(255) NOT NULL DEFAULT '',
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `trace` mediumblob,
  `content` text,
  `recipients` mediumblob,
  `gw_response` mediumblob,
  `smsgw` varchar(255) NOT NULL DEFAULT '',
  `created_by` varchar(255) NOT NULL DEFAULT '',
  `send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `total_amount` double NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `merchant_id` (`merchant_id`),
  CONSTRAINT `merchant_sms_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=10196 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchant_statement`
--

DROP TABLE IF EXISTS `merchant_statement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `merchant_statement` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint(20) unsigned DEFAULT NULL,
  `transactions_log_id` bigint(20) unsigned DEFAULT NULL,
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
  PRIMARY KEY (`id`),
  KEY `transactions_log_id` (`transactions_log_id`),
  CONSTRAINT `merchant_statement_ibfk_1` FOREIGN KEY (`transactions_log_id`) REFERENCES `merchant_transactions_log` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=11451 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchant_transactions_log`
--

DROP TABLE IF EXISTS `merchant_transactions_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `merchant_transactions_log` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint(20) unsigned DEFAULT NULL,
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
  `merchant_batch_transactions_log_id` bigint(20) unsigned DEFAULT NULL,
  `tx_cost` double NOT NULL DEFAULT '0',
  `callback_url` varchar(255) NOT NULL DEFAULT '',
  `callback_trace` text,
  `name` varchar(255) NOT NULL DEFAULT '',
  `account_type` varchar(255) NOT NULL DEFAULT 'phone',
  `beneficiary_id` bigint(20) unsigned DEFAULT NULL,
  `resolved_by` varchar(255) NOT NULL DEFAULT '',
  `originate_ip` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `tx_unique_id` (`tx_unique_id`),
  UNIQUE KEY `unique_merchant_id` (`merchant_id`,`tx_merchant_ref`),
  UNIQUE KEY `unique_merchant_tx` (`merchant_id`,`tx_merchant_ref`),
  KEY `merchant_batch_transactions_log_id` (`merchant_batch_transactions_log_id`),
  KEY `tx_merchant_ref` (`tx_merchant_ref`),
  KEY `beneficiary_id` (`beneficiary_id`),
  CONSTRAINT `merchant_transactions_log_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE SET NULL,
  CONSTRAINT `merchant_transactions_log_ibfk_2` FOREIGN KEY (`merchant_batch_transactions_log_id`) REFERENCES `merchant_batch_transactions_log` (`id`) ON DELETE SET NULL,
  CONSTRAINT `merchant_transactions_log_ibfk_3` FOREIGN KEY (`beneficiary_id`) REFERENCES `beneficiaries` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=235 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchants`
--

DROP TABLE IF EXISTS `merchants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `merchants` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL DEFAULT '',
  `status` enum('ACTIVE','SUSPENDED','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `account_number` varchar(255) NOT NULL DEFAULT '',
  `created_on` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` varchar(255) NOT NULL DEFAULT '',
  `account_type` enum('business','personal') NOT NULL DEFAULT 'personal',
  `public_key` blob,
  `private_key` blob,
  `allowed_apis` text,
  `short_name` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_id` (`account_number`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchants_audit_trail`
--

DROP TABLE IF EXISTS `merchants_audit_trail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `merchants_audit_trail` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint(20) unsigned DEFAULT NULL,
  `user_name` varchar(255) NOT NULL DEFAULT '',
  `user_id` varchar(255) NOT NULL DEFAULT '',
  `action` text,
  `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `merchant_id` (`merchant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `settings`
--

DROP TABLE IF EXISTS `settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `settings` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `label` varchar(255) NOT NULL DEFAULT '',
  `name` varchar(255) NOT NULL DEFAULT '',
  `setting_value` text,
  `description` varchar(255) NOT NULL DEFAULT '',
  `setting_group` varchar(255) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2020-02-26 17:15:55
