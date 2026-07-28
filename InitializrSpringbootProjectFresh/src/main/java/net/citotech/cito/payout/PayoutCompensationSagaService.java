package net.citotech.cito.payout;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persisted saga state for the payout failure-compensation sequence (audit B3): reversing a
 * failed payout is several separate statement writes (DR suspense, CR customer, charge
 * reversals). Previously, if one of those writes failed partway through, the only trace was a log
 * line - this records step-by-step progress so a partial reversal is a queryable, alertable fact
 * instead of something only visible by reading logs after the incident is reported.
 *
 * <p>Every write here uses {@code REQUIRES_NEW}: the compensation sequence this tracks runs
 * inside its own DB transaction in {@code Common.doPayOut}, and if a step fails that whole
 * transaction rolls back. The saga's own progress record must survive that rollback - that's the
 * entire point of persisting it - so it commits independently in its own transaction rather than
 * participating in the caller's.
 */
@Service
public class PayoutCompensationSagaService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PayoutCompensationSagaService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Starts (or resumes, if one already exists for this tx) a compensation saga. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long start(long transactionsLogId, String txUniqueId, long merchantId, int totalSteps) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("transactions_log_id", transactionsLogId);
        p.addValue("tx_unique_id", txUniqueId);
        p.addValue("merchant_id", merchantId);
        p.addValue("total_steps", totalSteps);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(
                "INSERT INTO payout_compensation_sagas "
                    + "(transactions_log_id, tx_unique_id, merchant_id, total_steps, saga_status) "
                    + "VALUES (:transactions_log_id, :tx_unique_id, :merchant_id, :total_steps, 'STARTED')",
                p, keyHolder);
            return keyHolder.getKey().longValue();
        } catch (Exception alreadyExists) {
            return existingSagaId(transactionsLogId);
        }
    }

    private long existingSagaId(long transactionsLogId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM payout_compensation_sagas WHERE transactions_log_id=:transactions_log_id",
            new MapSqlParameterSource("transactions_log_id", transactionsLogId),
            Long.class);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStepComplete(long sagaId, String stepName) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", sagaId);
        p.addValue("step_name", stepName);
        jdbcTemplate.update(
            "UPDATE payout_compensation_sagas SET completed_steps=completed_steps+1, "
                + "last_step_name=:step_name, saga_status='IN_PROGRESS' WHERE id=:id",
            p);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(long sagaId) {
        jdbcTemplate.update(
            "UPDATE payout_compensation_sagas SET saga_status='COMPLETED' WHERE id=:id",
            new MapSqlParameterSource("id", sagaId));
    }

    /** Marks the saga stuck - a step failed and compensation could not finish. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStuck(long sagaId, String error) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", sagaId);
        p.addValue("last_error", trim(error));
        jdbcTemplate.update(
            "UPDATE payout_compensation_sagas SET saga_status='STUCK', last_error=:last_error WHERE id=:id",
            p);
    }

    /** Sagas that have sat in a non-terminal state longer than the given age - candidates for alerting. */
    public List<StuckSaga> findStuckOlderThan(int minutes) {
        Instant cutoff = Instant.now().minus(minutes, ChronoUnit.MINUTES);
        MapSqlParameterSource p = new MapSqlParameterSource("cutoff", Timestamp.from(cutoff));
        return jdbcTemplate.query(
            "SELECT id, transactions_log_id, tx_unique_id, merchant_id, total_steps, completed_steps, "
                + "last_step_name, saga_status, last_error "
                + "FROM payout_compensation_sagas "
                + "WHERE saga_status IN ('STARTED','IN_PROGRESS','STUCK') AND updated_at < :cutoff",
            p,
            (rs, rowNum) -> new StuckSaga(
                rs.getLong("id"),
                rs.getLong("transactions_log_id"),
                rs.getString("tx_unique_id"),
                rs.getLong("merchant_id"),
                rs.getInt("total_steps"),
                rs.getInt("completed_steps"),
                rs.getString("last_step_name"),
                rs.getString("saga_status"),
                rs.getString("last_error")));
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    public record StuckSaga(long id, long transactionsLogId, String txUniqueId, long merchantId,
            int totalSteps, int completedSteps, String lastStepName, String sagaStatus, String lastError) {
    }
}
