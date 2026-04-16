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
-- Dumping data for table `db_changes`
--

LOCK TABLES `db_changes` WRITE;
/*!40000 ALTER TABLE `db_changes` DISABLE KEYS */;
INSERT INTO `db_changes` VALUES (4,'2020-02-19-00','ALTER TABLE merchant_transactions_log ADD COLUMN `originate_ip` VARCHAR(255) NOT NULL DEFAULT \'\'','ALTER TABLE merchant_transactions_log DROP COLUMN `originate_ip`','2020-02-20 07:42:59'),(5,'2020-02-20-01','CREATE TABLE merchant_sms (\n                `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,\n                `merchant_id` BIGINT UNSIGNED,\n                `charge` DOUBLE NOT NULL DEFAULT 0,\n                `cost` DOUBLE NOT NULL DEFAULT 0,\n                `total_recipients` INT DEFAULT 0,\n                `status` VARCHAR(255) NOT NULL DEFAULT \'\',\n                `created_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n                `trace` MEDIUMBLOB,\n                `content` TEXT, \n                `recipients` MEDIUMBLOB,\n                `gw_response` MEDIUMBLOB,\n                `smsgw` VARCHAR(255) NOT NULL DEFAULT \'\',\n                FOREIGN KEY (`merchant_id`) REFERENCES `merchants` (`id`) ON DELETE SET NULL\n            ) ENGINE=InnoDB;','DROP TABLE IF EXISTS merchant_sms;','2020-02-20 07:42:59'),(6,'2020-02-20-02','ALTER TABLE merchant_sms ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT \'\'','ALTER TABLE merchant_sms DROP COLUMN created_by','2020-02-21 10:45:14'),(7,'2020-02-21-01','ALTER TABLE merchant_statement ADD COLUMN sms_balance DOUBLE NOT NULL DEFAULT 0','ALTER TABLE merchant_statement DROP COLUMN sms_balance','2020-02-21 10:45:14'),(8,'2020-02-21-02','ALTER TABLE merchant_sms ADD COLUMN send_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP','ALTER TABLE merchant_sms DROP COLUMN send_time','2020-02-21 10:45:14'),(9,'2020-02-21-03','ALTER TABLE merchant_sms ADD COLUMN total_amount DOUBLE NOT NULL DEFAULT 0','ALTER TABLE merchant_sms DROP COLUMN total_amount','2020-02-21 10:45:14'),(10,'2020-02-24-01','CREATE TABLE `merchant_settings` (\n                `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,\n                `merchant_id` bigint unsigned,\n                `label` varchar(255) NOT NULL DEFAULT \'\',\n                `name` varchar(255) NOT NULL DEFAULT \'\',\n                `setting_value` text,\n                `description` varchar(255) NOT NULL DEFAULT \'\',\n                `setting_group` varchar(255) NOT NULL DEFAULT \'\',\n                PRIMARY KEY (`id`),\n                UNIQUE KEY `unique_merchant_setting_name` (`merchant_id`,`name`),\n                FOREIGN KEY (`merchant_id`) REFERENCES `merchants`(`id`) ON DELETE CASCADE  \n            ) ENGINE=InnoDB','DROP TABLE IF EXISTS merchant_settings','2020-02-24 19:17:30');
/*!40000 ALTER TABLE `db_changes` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2020-02-26 17:16:33
