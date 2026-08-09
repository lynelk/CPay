package net.citotech.cito.communication.sms;

import java.math.BigInteger;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.GeneralException;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.MerchantSms;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.Model.SmsGateway;
import net.citotech.cito.Model.Statement;
import net.citotech.cito.Model.Transaction;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Extracted SMS pending-send delivery worker (ISO domain mapping: communication/sms). The bill →
 * send → reverse → update loop below is a faithful port of {@code
 * TransactionsLogController.testSendPendingSmsCron()}: {@code deliverDue} pulls PENDING rows,
 * debits the merchant (DR {@code TX_TYPE_SMS_CUSTOMER_CHARGE}), hands the content/recipients to the
 * active {@link SmsGatewayAdapter}, and on a refundable outcome (REJECTED/FAILED — audit P5)
 * credits the merchant back (CR {@code TX_TYPE_SMS_CUSTOMER_CHARGE_REVERSAL}) before recording the
 * terminal status on the merchant_sms row.
 *
 * <p>Provider selection, billing and the ledger statement path are unchanged from the legacy
 * behavior; the only behavioral difference is that the whole call is ShedLock-wrapped by the
 * scheduler instead of relying on a single-host file lock (B0).
 */
@Service
public class SmsDeliveryService {

    private static final Logger logger = Logger.getLogger(SmsDeliveryService.class.getName());

    private static final String SMS_BALANCE_TYPE = SmsGateway.BALANCE_TYPE;
    private static final String GATEWAY_ID = SmsGateway.getGatewayId();
    private static final int DEFAULT_BATCH_LIMIT = 1000;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final SmsGatewayAdapter gatewayAdapter;

    public SmsDeliveryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            SmsGatewayAdapter gatewayAdapter) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.gatewayAdapter = gatewayAdapter;
    }

    /**
     * Processes up to {@code limit} PENDING merchant_sms rows. Returns the number of rows processed
     * so callers (scheduler/controller) can report it. Per-row failures never roll back the batch:
     * a failing row is logged and skipped, exactly like the legacy loop.
     */
    public int deliverDue(int limit) {
        int cap = limit <= 0 ? DEFAULT_BATCH_LIMIT : Math.min(limit, DEFAULT_BATCH_LIMIT);
        List<MerchantSms> pending = pendingRows(cap);
        int processed = 0;
        for (MerchantSms sms : pending) {
            try {
                process(sms);
                processed++;
            } catch (Exception ex) {
                logger.log(
                        Level.WARNING,
                        "SMS delivery failed for id " + sms.getId() + ": " + ex.getMessage(),
                        ex);
            }
        }
        return processed;
    }

    private void process(MerchantSms sms) {
        Merchant merchant = Common.getMerchantById(sms.getMerchant_id() + "", jdbcTemplate);
        if (merchant == null) {
            logger.log(
                    Level.WARNING,
                    "Skipping SMS id " + sms.getId() + ": no merchant " + sms.getMerchant_id());
            return;
        }

        // Bill the merchant (DR) for this SMS before sending, matching the legacy loop.
        String billResult =
                executeCharge(sms, Transaction.TX_TYPE_SMS_CUSTOMER_CHARGE, "SMS Charge: ", "DR");
        if (!"success".equals(billResult)) {
            logger.log(
                    Level.WARNING,
                    "SMS id "
                            + sms.getId()
                            + " charge failed ("
                            + billResult
                            + "); leaving row pending for retry");
            return;
        }

        // Send through the active adapter.
        SmsSendResult sendResult =
                gatewayAdapter.send(
                        new SmsSendRequest(
                                sms.getId().longValue(),
                                sms.getMerchant_id().longValue(),
                                sms.getContent(),
                                sms.getRecipients(),
                                sms.getSmsgw()));

        if (sendResult.status().isRefundable()) {
            // Audit P5: a transport failure or provider rejection must refund the charge.
            String reversalResult =
                    executeCharge(
                            sms,
                            Transaction.TX_TYPE_SMS_CUSTOMER_CHARGE_REVERSAL,
                            "SMS Charge: ",
                            "CR");
            if (!"success".equals(reversalResult)) {
                logger.log(
                        Level.WARNING,
                        "SMS id "
                                + sms.getId()
                                + " reversal failed ("
                                + reversalResult
                                + "); recording status anyway");
            }
        }

        updateRowStatus(sms, sendResult);
    }

    private String executeCharge(
            MerchantSms sms, String narrative, String description, String txType) {
        Statement statement = new Statement();
        statement.setAmount(sms.getTotal_amount());
        statement.setGateway_id(GATEWAY_ID);
        statement.setNarritive(narrative);
        statement.setMerchant_id(getMerchantLongId(sms));
        statement.setDescription(description);
        statement.setRecorded_by("SYSTEM");
        statement.setTx_type(txType);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(
                new TransactionCallback<String>() {
                    @Override
                    public String doInTransaction(TransactionStatus status) {
                        try {
                            String result =
                                    Common.recordStatementTxWithoutTransaction(
                                            statement,
                                            SMS_BALANCE_TYPE,
                                            jdbcTemplate,
                                            transactionManager,
                                            status);
                            if (!"success".equals(result)) {
                                logger.log(
                                        Level.WARNING,
                                        "SMS statement " + narrative + " rejected: " + result);
                                return result;
                            }
                            return "success";
                        } catch (Exception ex) {
                            status.setRollbackOnly();
                            logger.log(Level.SEVERE, ex.getMessage(), ex);
                            return GeneralException.getError("102", GeneralException.ERRORS_102);
                        }
                    }
                });
    }

    private void updateRowStatus(MerchantSms sms, SmsSendResult result) {
        String gatewayName = sms.getSmsgw();
        if (gatewayName == null || gatewayName.isBlank()) {
            gatewayName = smsgwName();
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", sms.getId());
        parameters.addValue("status", result.status().name());
        parameters.addValue("trace", result.trace());
        parameters.addValue("gw_response", result.gwResponse());
        parameters.addValue("smsgw", gatewayName);
        jdbcTemplate.update(
                "UPDATE "
                        + Common.DB_TABLE_MERCHANT_SMS
                        + " SET status=:status, gw_response=:gw_response, smsgw=:smsgw, trace=:trace"
                        + " WHERE id=:id",
                parameters);
    }

    private List<MerchantSms> pendingRows(int limit) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("limit", limit);
        return jdbcTemplate.query(
                "SELECT * FROM "
                        + Common.DB_TABLE_MERCHANT_SMS
                        + " WHERE status IN ('PENDING') "
                        + " ORDER BY id ASC LIMIT :limit",
                parameters,
                (rs, rowNum) -> {
                    MerchantSms sms = new MerchantSms();
                    sms.setId(BigInteger.valueOf(rs.getLong("id")));
                    sms.setCharge(rs.getDouble("charge"));
                    sms.setCost(rs.getDouble("cost"));
                    sms.setContent(rs.getString("content"));
                    sms.setCreated_on(rs.getString("created_on"));
                    sms.setSend_time(rs.getString("send_time"));
                    sms.setStatus(rs.getString("status"));
                    sms.setMerchant_id(BigInteger.valueOf(rs.getLong("merchant_id")));
                    sms.setRecipients(rs.getString("recipients"));
                    sms.setSmsgw(rs.getString("smsgw"));
                    sms.setTotal_amount(rs.getDouble("total_amount"));
                    sms.setTrace(rs.getString("trace"));
                    sms.setTotal_recipients(rs.getInt("total_recipients"));
                    sms.setGw_response(rs.getString("gw_response"));
                    sms.setCreated_by(rs.getString("created_by"));
                    return sms;
                });
    }

    private long getMerchantLongId(MerchantSms sms) {
        return sms.getMerchant_id() == null ? 0L : sms.getMerchant_id().longValue();
    }

    private String smsgwName() {
        try {
            Setting setting = Common.getSettings("sms_gateway_name", jdbcTemplate);
            return setting == null ? "" : setting.getSetting_value();
        } catch (Exception ex) {
            return "";
        }
    }
}
