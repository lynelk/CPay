package net.citotech.cito.api.v2;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MerchantReadAuditService {
    private static final Logger logger = Logger.getLogger(MerchantReadAuditService.class.getName());
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MerchantReadAuditService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(Merchant merchant, String action, String resourceReference) {
        if (merchant == null) {
            return;
        }
        try {
            String sql = "INSERT INTO " + Common.DB_TABLE_AUDIT_TRAIL_MERCHANT
                    + " (merchant_id, user_name, user_id, action) "
                    + "VALUES (:merchant_id, :user_name, :user_id, :action)";
            MapSqlParameterSource p = new MapSqlParameterSource();
            p.addValue("merchant_id", merchant.getId());
            p.addValue("user_name", merchant.getAccount_number());
            p.addValue("user_id", String.valueOf(merchant.getId()));
            p.addValue("action", action + ": " + resourceReference);
            jdbcTemplate.update(sql, p);
        } catch (DataAccessException ex) {
            logger.log(Level.WARNING, "Merchant read audit could not be recorded", ex);
        }
    }
}
