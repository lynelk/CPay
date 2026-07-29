package net.citotech.cito.config;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Audit G1: distributed locking for crons that are both {@code @Scheduled} AND directly
 * HTTP-triggerable via {@code @PostMapping} -
 * {@link net.citotech.cito.TransactionsLogController#testCheckstatusCron()} and
 * {@link net.citotech.cito.TransactionsLogController#paymentsPayCron()} - so that a multi-instance
 * (multi-pod/replica) deployment can't run the same payout-disbursement or status-check batch on
 * two instances concurrently.
 *
 * <p>Before this, the only guard was a local-filesystem {@link java.nio.channels.FileLock}
 * (see {@code Common.CLASS_PATH_CHECK_TX_LOCK} / {@code Common.CLASS_PATH_PAYMENTS_CRON_TX_LOCK}).
 * That only ever protected a single machine/container: each app instance has its own local disk
 * and its own independently-firing {@code @Scheduled} trigger, so in a normal production HA setup
 * (>1 replica) the file lock does nothing to stop two instances from processing the same batch of
 * pending transactions/payouts at once - a real double-disbursement risk. ShedLock backs the lock
 * with a row in the {@code shedlock} table (see {@code V29__shedlock.sql}), which is visible to
 * every instance sharing the same MySQL database, so only one instance can hold a given named lock
 * at a time regardless of how many replicas are running.
 *
 * <p>The file locks in {@code TransactionsLogController} are intentionally left in place as a
 * defense-in-depth layer (see the class-level note there) rather than removed - ShedLock is now
 * the authoritative cross-instance guard.
 *
 * <p>Uses the default {@code interceptMode = PROXY_METHOD}: ShedLock wraps calls to any
 * {@code @SchedulerLock}-annotated method with an AOP advisor, so the lock applies uniformly
 * whether the method is invoked by the Spring {@code TaskScheduler} (the {@code @Scheduled} path)
 * or dispatched to by Spring MVC (the {@code @PostMapping} path) - both go through the same
 * Spring-managed proxy for this bean. {@code TransactionsLogController} implements no interfaces,
 * so Spring AOP proxies it via CGLIB subclassing automatically (no {@code proxyTargetClass=true}
 * needed). This does NOT protect against a raw {@code this.testCheckstatusCron()} /
 * {@code this.paymentsPayCron()} self-invocation from inside the same class bypassing the proxy -
 * no such call exists anywhere in the codebase today (verified by search), but if one is ever
 * added it would silently skip the lock.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    /**
     * Backs the distributed lock with the app's existing MySQL {@link DataSource} (the same
     * database the app already uses for everything else - no new infrastructure required).
     * {@code usingDbTime()} makes ShedLock compute lock expiry from the database's clock rather
     * than each app instance's local clock, so lock correctness doesn't depend on NTP/clock sync
     * being perfect across app hosts.
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
