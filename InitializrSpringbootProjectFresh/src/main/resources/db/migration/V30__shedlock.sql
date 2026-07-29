-- Audit G1: distributed locking table for ShedLock (net.javacrumbs.shedlock).
--
-- TransactionsLogController.testCheckstatusCron() and .paymentsPayCron() are both directly
-- HTTP-triggerable (@PostMapping) AND @Scheduled, and previously were only guarded by a
-- local-filesystem lock (java.nio.channels.FileLock). A local file lock only protects against
-- concurrent execution on the SAME machine/container - if CPay ever runs more than one app
-- instance (multiple pods/replicas, a normal production HA setup), each instance has its own
-- local disk and its own scheduler firing independently, so the file lock does nothing to stop
-- two instances from processing the same payout/status-check batch at the same time. This is a
-- real double-disbursement / duplicate-processing risk for money-movement crons.
--
-- This table is ShedLock's standard schema for JdbcTemplateLockProvider (unchanged across the
-- 4.x/5.x/6.x/7.x lines as of shedlock-provider-jdbc-template 7.7.0, which this app pins in
-- pom.xml): one row per lock name, held until `lock_until`, released early by the holder when its
-- task completes. See net.citotech.cito.config.SchedulerLockConfig for the LockProvider bean and
-- the @SchedulerLock annotations on the two cron methods above.
CREATE TABLE IF NOT EXISTS `shedlock` (
    `name` VARCHAR(64) NOT NULL,
    `lock_until` TIMESTAMP(3) NOT NULL,
    `locked_at` TIMESTAMP(3) NOT NULL,
    `locked_by` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
