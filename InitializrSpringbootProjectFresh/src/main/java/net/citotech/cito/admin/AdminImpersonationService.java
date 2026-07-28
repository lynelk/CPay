package net.citotech.cito.admin;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audited admin impersonation (audit O4): lets support staff view CPay as a specific merchant
 * (e.g. to debug a support ticket) without knowing the merchant's password.
 *
 * <p>Sessions are time-boxed (see {@link #DEFAULT_TTL}) and deliberately read-mostly: starting one
 * requires the {@link #PERMISSION_CODE} admin permission (checked the same way every other admin
 * op in this package is gated - see {@link AdminPermissionService#require}) and a non-blank
 * {@code reason}, and every start/end is written to the existing {@code admin_audit_events} audit
 * mechanism via {@link AdminAuditService} rather than a new one.
 *
 * <p>Critically, this service never authorizes a mutating/money-moving action by itself. Read
 * paths that want to resolve "what merchant is this admin currently impersonating" should call
 * {@link #resolveImpersonatedMerchantId(long)}; mutating paths (payouts, refunds, channel
 * credential changes, ...) must call {@link #requireNotImpersonating(long, String)} first, which
 * throws if the calling admin currently has an active impersonation session - impersonation is
 * read-mostly, so it can never stand in for the merchant's own real session or v2 request signing.
 */
@Service
public class AdminImpersonationService {
    /** Permission code gating who may start an impersonation session (seeded in V25). */
    public static final String PERMISSION_CODE = "ADMIN_IMPERSONATE_MERCHANT";

    /** Impersonation sessions are short-lived by design - not indefinite. */
    static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AdminPermissionService permissions;
    private final AdminAuditService auditService;

    public AdminImpersonationService(NamedParameterJdbcTemplate jdbcTemplate,
            AdminPermissionService permissions, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissions = permissions;
        this.auditService = auditService;
    }

    /**
     * Starts a time-boxed impersonation session. Requires the {@link #PERMISSION_CODE} permission
     * (an {@link AccessDeniedException} is thrown otherwise, exactly like every other admin op
     * gated by {@link AdminPermissionService#require}) and a non-blank {@code reason} explaining
     * why - both are checked before anything is written.
     *
     * @return the new session id
     */
    @Transactional
    public long start(long adminUserId, long merchantId, String reason) {
        permissions.require(PERMISSION_CODE, "impersonation-start", "merchant:" + merchantId);
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new PaymentGatewayException("reason is required to start an impersonation session");
        }

        Instant startedAt = Instant.now();
        Instant expiresAt = startedAt.plus(DEFAULT_TTL);

        MapSqlParameterSource insertParams = new MapSqlParameterSource();
        insertParams.addValue("admin_user_id", adminUserId);
        insertParams.addValue("merchant_id", merchantId);
        insertParams.addValue("reason", trimmedReason);
        insertParams.addValue("started_at", Timestamp.from(startedAt));
        insertParams.addValue("expires_at", Timestamp.from(expiresAt));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO admin_impersonation_sessions "
                + "(admin_user_id, merchant_id, reason, started_at, expires_at) "
                + "VALUES (:admin_user_id, :merchant_id, :reason, :started_at, :expires_at)",
            insertParams, keyHolder, new String[] {"id"});
        long sessionId = keyHolder.getKey().longValue();

        auditService.record(PERMISSION_CODE, "IMPERSONATION_START", "merchant:" + merchantId,
            "admin_user_id=" + adminUserId + "; merchant_id=" + merchantId
                + "; reason=" + trimmedReason
                + "; started_at=" + startedAt + "; expires_at=" + expiresAt);

        return sessionId;
    }

    /**
     * Ends an impersonation session early (before its TTL expires). Only affects a session that
     * belongs to the given admin and is not already ended; a no-op (no audit entry, no exception)
     * if the session id doesn't match an active session for that admin, so repeated/racing end
     * calls are harmless.
     */
    @Transactional
    public void end(long sessionId, long adminUserId, String endedReason) {
        String reasonCode = (endedReason == null || endedReason.trim().isEmpty()) ? "MANUAL_END" : endedReason.trim();

        MapSqlParameterSource updateParams = new MapSqlParameterSource();
        updateParams.addValue("id", sessionId);
        updateParams.addValue("admin_user_id", adminUserId);
        updateParams.addValue("ended_reason", reasonCode);
        int updated = jdbcTemplate.update(
            "UPDATE admin_impersonation_sessions SET ended_at = CURRENT_TIMESTAMP, ended_reason = :ended_reason "
                + "WHERE id = :id AND admin_user_id = :admin_user_id AND ended_at IS NULL",
            updateParams);

        if (updated > 0) {
            auditService.record(PERMISSION_CODE, "IMPERSONATION_END", "session:" + sessionId,
                "admin_user_id=" + adminUserId + "; session_id=" + sessionId + "; ended_reason=" + reasonCode);
        }
    }

    /**
     * The admin's currently active impersonation session, if any. A session past its
     * {@code expires_at} is treated as inactive here (and is lazily marked {@code EXPIRED} in the
     * same call) even if nobody ever called {@link #end}, so a stale session can never resolve as
     * live just because no one cleaned it up.
     */
    public Optional<ImpersonationSession> findActive(long adminUserId) {
        MapSqlParameterSource p = new MapSqlParameterSource("admin_user_id", adminUserId);
        List<ImpersonationSession> rows = jdbcTemplate.query(
            "SELECT id, admin_user_id, merchant_id, reason, started_at, expires_at "
                + "FROM admin_impersonation_sessions "
                + "WHERE admin_user_id = :admin_user_id AND ended_at IS NULL "
                + "ORDER BY started_at DESC LIMIT 1",
            p,
            (rs, rowNum) -> new ImpersonationSession(
                rs.getLong("id"),
                rs.getLong("admin_user_id"),
                rs.getLong("merchant_id"),
                rs.getString("reason"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()));

        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ImpersonationSession session = rows.get(0);
        if (session.expiresAt().isBefore(Instant.now())) {
            markExpired(session.id());
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** Read-path helper: what merchant (if any) this admin is actively, non-expired impersonating. */
    public Optional<Long> resolveImpersonatedMerchantId(long adminUserId) {
        return findActive(adminUserId).map(ImpersonationSession::merchantId);
    }

    /**
     * Guard for mutating / money-moving actions (payouts, refunds, channel credential changes,
     * ...). Impersonation is read-mostly by design: if the given admin currently has an active
     * impersonation session, refuse - regardless of which merchant is involved - forcing the
     * caller to use the merchant's own real session or v2 request signing instead.
     *
     * @throws AccessDeniedException if the admin currently has an active impersonation session
     */
    public void requireNotImpersonating(long adminUserId, String actionName) {
        if (findActive(adminUserId).isPresent()) {
            throw new AccessDeniedException(
                "Admin " + adminUserId + " is currently impersonating a merchant; " + actionName
                    + " requires the merchant's own real session or v2 request signing, not an impersonation session");
        }
    }

    private void markExpired(long sessionId) {
        jdbcTemplate.update(
            "UPDATE admin_impersonation_sessions SET ended_at = CURRENT_TIMESTAMP, ended_reason = 'EXPIRED' "
                + "WHERE id = :id AND ended_at IS NULL",
            new MapSqlParameterSource("id", sessionId));
    }

    public record ImpersonationSession(long id, long adminUserId, long merchantId, String reason,
            Instant startedAt, Instant expiresAt) {
    }
}
