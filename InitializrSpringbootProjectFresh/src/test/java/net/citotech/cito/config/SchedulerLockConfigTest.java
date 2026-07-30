package net.citotech.cito.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import net.citotech.cito.TransactionsLogController;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Covers audit G1/G6: TransactionsLogController.testCheckstatusCron()/paymentsPayCron() are
 * scheduler-only money jobs protected by ShedLock, not direct HTTP-triggered crons guarded only
 * by a local-filesystem lock. This proves the real distributed-lock
 * mechanics against a real (if in-memory, since no MySQL is available in this test environment)
 * SQL engine: the same lock name can't be held twice concurrently, and releases correctly.
 */
class SchedulerLockConfigTest {

    private DataSource freshDatabase() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new JdbcTemplate(dataSource).execute(
            "CREATE TABLE shedlock ("
                + "name VARCHAR(64) NOT NULL, "
                + "lock_until TIMESTAMP(3) NOT NULL, "
                + "locked_at TIMESTAMP(3) NOT NULL, "
                + "locked_by VARCHAR(255) NOT NULL, "
                + "PRIMARY KEY (name))");
        return dataSource;
    }

    @Test
    void lockProviderBeanWiresUpAgainstTheConfiguredDataSource() {
        LockProvider lockProvider = new SchedulerLockConfig().lockProvider(freshDatabase());

        assertThat(lockProvider).isNotNull();
    }

    @Test
    void theSameLockNameCannotBeHeldByTwoCallersAtOnce() {
        LockProvider lockProvider = new SchedulerLockConfig().lockProvider(freshDatabase());
        LockConfiguration config = new LockConfiguration(
            Instant.now(), "paymentsPayCron", Duration.ofMinutes(20), Duration.ofSeconds(30));

        Optional<SimpleLock> firstHolder = lockProvider.lock(config);
        Optional<SimpleLock> secondHolder = lockProvider.lock(config);

        assertThat(firstHolder).isPresent();
        assertThat(secondHolder).isEmpty();

        firstHolder.get().unlock();
    }

    @Test
    void releasingTheLockAllowsItToBeAcquiredAgain() {
        LockProvider lockProvider = new SchedulerLockConfig().lockProvider(freshDatabase());
        LockConfiguration config = new LockConfiguration(
            Instant.now(), "testCheckstatusCron", Duration.ofMinutes(15), Duration.ofSeconds(0));

        Optional<SimpleLock> firstHolder = lockProvider.lock(config);
        assertThat(firstHolder).isPresent();
        firstHolder.get().unlock();

        Optional<SimpleLock> secondHolder = lockProvider.lock(
            new LockConfiguration(Instant.now(), "testCheckstatusCron", Duration.ofMinutes(15), Duration.ofSeconds(0)));

        assertThat(secondHolder).isPresent();
        secondHolder.get().unlock();
    }

    @Test
    void differentLockNamesDoNotContendWithEachOther() {
        LockProvider lockProvider = new SchedulerLockConfig().lockProvider(freshDatabase());

        Optional<SimpleLock> payoutLock = lockProvider.lock(new LockConfiguration(
            Instant.now(), "paymentsPayCron", Duration.ofMinutes(20), Duration.ofSeconds(30)));
        Optional<SimpleLock> statusCheckLock = lockProvider.lock(new LockConfiguration(
            Instant.now(), "testCheckstatusCron", Duration.ofMinutes(15), Duration.ofSeconds(0)));

        assertThat(payoutLock).isPresent();
        assertThat(statusCheckLock).isPresent();

        payoutLock.get().unlock();
        statusCheckLock.get().unlock();
    }

    @Test
    void configEnablesSchedulerLockWithASensibleDefaultCeiling() {
        EnableSchedulerLock annotation = SchedulerLockConfig.class.getAnnotation(EnableSchedulerLock.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.defaultLockAtMostFor()).isEqualTo("PT10M");
    }

    @Test
    void bothMoneyMovementCronsAreScheduledLockedAndNotHttpMapped() throws NoSuchMethodException {
        Method statusCheckCron = TransactionsLogController.class.getDeclaredMethod("testCheckstatusCron");
        Method payoutCron = TransactionsLogController.class.getDeclaredMethod("paymentsPayCron");

        SchedulerLock statusCheckLock = statusCheckCron.getAnnotation(SchedulerLock.class);
        SchedulerLock payoutLock = payoutCron.getAnnotation(SchedulerLock.class);
        Scheduled statusCheckSchedule = statusCheckCron.getAnnotation(Scheduled.class);
        Scheduled payoutSchedule = payoutCron.getAnnotation(Scheduled.class);

        assertThat(statusCheckSchedule).isNotNull();
        assertThat(statusCheckLock).isNotNull();
        assertThat(statusCheckLock.name()).isEqualTo("testCheckstatusCron");
        assertThat(payoutSchedule).isNotNull();
        assertThat(payoutLock).isNotNull();
        assertThat(payoutLock.name()).isEqualTo("paymentsPayCron");
        assertThat(statusCheckCron.getAnnotation(PostMapping.class)).isNull();
        assertThat(payoutCron.getAnnotation(PostMapping.class)).isNull();
        // Both must exceed their own @Scheduled fixedDelay (60s / 30s respectively) so a crashed
        // instance's lock reliably expires before starving the queue, and lockAtLeastFor must be
        // >= fixedDelay so a fast run's lock can't be immediately re-acquired before the next
        // legitimate tick.
        assertThat(payoutLock.lockAtMostFor()).isEqualTo("PT20M");
        assertThat(payoutLock.lockAtLeastFor()).isEqualTo("PT30S");
        assertThat(statusCheckLock.lockAtMostFor()).isEqualTo("PT15M");
        assertThat(statusCheckLock.lockAtLeastFor()).isEqualTo("PT1M");
    }
}
