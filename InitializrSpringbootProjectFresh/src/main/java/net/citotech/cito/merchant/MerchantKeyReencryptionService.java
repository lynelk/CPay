package net.citotech.cito.merchant;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Audit E6: upgrades existing merchant RSA private-key rows to the dedicated {@code
 * cpay.key.encryption.key} envelope. AES-GCM cannot be applied in a Flyway migration, so the {@code
 * V31__merchant_key_encryption.sql} column tracks state and this service does the actual
 * re-encryption in code:
 *
 * <ul>
 *   <li>version 2 = already under the dedicated key - skipped.
 *   <li>version 0 / NULL = legacy: raw PEM plaintext, or a blob encrypted under the pre-dedicated
 *       channel key. Raw PEM is encrypted directly; channel-key blobs are decrypted via the legacy
 *       service and re-encrypted.
 *   <li>version 1 = explicitly tracked channel-key blob - decrypted and re-encrypted.
 * </ul>
 *
 * <p>Runs a short-delay {@code @Scheduled} sweep (ShedLock-protected for multi-instance safety) so
 * existing rows are upgraded shortly after boot and any late/unusual rows get caught on subsequent
 * hourly passes. {@link #upgradeMerchant(long)} is the on-demand entry point.
 */
@Service
public class MerchantKeyReencryptionService {
    private static final Logger logger =
            Logger.getLogger(MerchantKeyReencryptionService.class.getName());
    private static final String PEM_PREFIX = "-----BEGIN";
    private static final int VERSION_DEDICATED = 2;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MerchantKeyEncryptionService keyEncryptionService;
    private final MerchantChannelCryptoService channelCryptoService;

    @Value("${cpay.key-encryption.reencrypt.enabled:true}")
    private boolean reencryptEnabled;

    public MerchantKeyReencryptionService(
            NamedParameterJdbcTemplate jdbcTemplate,
            MerchantKeyEncryptionService keyEncryptionService,
            MerchantChannelCryptoService channelCryptoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.keyEncryptionService = keyEncryptionService;
        this.channelCryptoService = channelCryptoService;
    }

    @Scheduled(
            initialDelayString = "${cpay.key-encryption.reencrypt.initial-delay-ms:15000}",
            fixedDelayString = "${cpay.key-encryption.reencrypt.fixed-delay-ms:3600000}")
    @SchedulerLock(
            name = "merchantKeyReencryption",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT1M")
    public void scheduledUpgradeAll() {
        if (!reencryptEnabled) {
            return;
        }
        UpgradeSummary summary = upgradeRows();
        if (summary.upgraded() > 0 || summary.failed() > 0) {
            logger.log(
                    Level.INFO,
                    "Merchant key re-encryption sweep: upgraded={0}, skipped={1}, failed={2}",
                    new Object[] {summary.upgraded(), summary.skipped(), summary.failed()});
        }
    }

    /** On-demand re-encryption of a single merchant's private key (admin-triggerable). */
    public boolean upgradeMerchant(long merchantId) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", merchantId);
        List<Row> rows =
                jdbcTemplate.query(
                        "SELECT id, private_key, key_encryption_version FROM merchants WHERE id=:id AND private_key IS NOT NULL AND private_key <> ''",
                        params,
                        (rs, rowNum) ->
                                new Row(
                                        rs.getLong("id"),
                                        rs.getString("private_key"),
                                        rs.getInt("key_encryption_version")));
        int upgraded = 0;
        int failed = 0;
        for (Row row : rows) {
            Outcome outcome = upgradeRow(row);
            if (outcome == Outcome.FAILED) {
                failed++;
            } else if (outcome == Outcome.UPGRADED) {
                upgraded++;
            }
        }
        return failed == 0 && upgraded > 0;
    }

    private UpgradeSummary upgradeRows() {
        List<Row> rows =
                jdbcTemplate.query(
                        "SELECT id, private_key, key_encryption_version FROM merchants WHERE private_key IS NOT NULL AND private_key <> ''",
                        new MapSqlParameterSource(),
                        (rs, rowNum) ->
                                new Row(
                                        rs.getLong("id"),
                                        rs.getString("private_key"),
                                        rs.getInt("key_encryption_version")));
        int upgraded = 0;
        int skipped = 0;
        int failed = 0;
        for (Row row : rows) {
            Outcome outcome = upgradeRow(row);
            if (outcome == Outcome.UPGRADED) {
                upgraded++;
            } else if (outcome == Outcome.SKIPPED) {
                skipped++;
            } else {
                failed++;
            }
        }
        return new UpgradeSummary(upgraded, skipped, failed);
    }

    private Outcome upgradeRow(Row row) {
        try {
            String stored = row.privateKey();
            if (row.version() == VERSION_DEDICATED || isBlank(stored)) {
                return Outcome.SKIPPED;
            }
            boolean isPlaintextPem = stored.startsWith(PEM_PREFIX);
            String plaintext = isPlaintextPem ? stored : decryptLegacy(stored);
            if (plaintext == null) {
                logger.log(
                        Level.WARNING,
                        "Merchant {0}: could not decrypt private key under legacy key",
                        row.id());
                return Outcome.FAILED;
            }
            String reencrypted = keyEncryptionService.encrypt(plaintext);
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("id", row.id());
            params.addValue("private_key", reencrypted);
            params.addValue("version", VERSION_DEDICATED);
            int updated =
                    jdbcTemplate.update(
                            "UPDATE merchants SET private_key=:private_key, key_encryption_version=:version WHERE id=:id",
                            params);
            if (updated == 0) {
                logger.log(
                        Level.WARNING,
                        "Merchant {0}: re-encryption update affected no rows",
                        row.id());
                return Outcome.FAILED;
            }
            logger.log(
                    Level.INFO,
                    "Merchant {0}: private key re-encrypted under dedicated E6 key",
                    row.id());
            return Outcome.UPGRADED;
        } catch (Exception ex) {
            logger.log(
                    Level.WARNING,
                    "Merchant {0}: re-encryption failed: {1}",
                    new Object[] {row.id(), ex.getMessage()});
            return Outcome.FAILED;
        }
    }

    private String decryptLegacy(String stored) {
        if (channelCryptoService == null) {
            return null;
        }
        try {
            return channelCryptoService.decrypt(stored);
        } catch (Exception first) {
            try {
                return keyEncryptionService.decrypt(stored);
            } catch (Exception second) {
                return null;
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record Row(long id, String privateKey, int version) {}

    public record UpgradeSummary(int upgraded, int skipped, int failed) {}

    private enum Outcome {
        UPGRADED,
        SKIPPED,
        FAILED
    }
}
