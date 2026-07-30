package net.citotech.cito.reconciliation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProviderStatementValidator {
    private final ProviderStatementParserRegistry parserRegistry;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProviderStatementValidator(ProviderStatementParserRegistry parserRegistry, NamedParameterJdbcTemplate jdbcTemplate) {
        this.parserRegistry = parserRegistry;
        this.jdbcTemplate = jdbcTemplate;
    }

    public long validate(String providerCode, String fileName, byte[] content) {
        ProviderStatementParser parser = parserRegistry.get(providerCode);
        List<StatementRow> rows = parser.parse(content, fileName);
        int valid = 0;
        int invalid = 0;
        int duplicates = 0;
        Set<String> seen = new HashSet<>();
        for (StatementRow row : rows) {
            boolean ok = row.providerReference != null && !row.providerReference.isEmpty() && row.amount != null && row.currency != null && !row.currency.isEmpty();
            String key = row.providerReference + ":" + row.amount + ":" + row.currency;
            if (!ok) invalid++;
            else if (!seen.add(key)) duplicates++;
            else valid++;
        }
        return save(parser.providerCode(), parser.channelCode(), fileName, rows.size(), valid, invalid, duplicates);
    }

    private long save(String providerCode, String channelCode, String fileName, int total, int valid, int invalid, int duplicates) {
        String status = invalid == 0 && duplicates == 0 ? "PASSED" : "FAILED";
        String sql = "INSERT INTO provider_statement_validation_runs (provider_code, channel_code, file_name, validation_status, total_rows, valid_rows, invalid_rows, duplicate_rows, message) VALUES (:provider_code, :channel_code, :file_name, :status, :total, :valid, :invalid, :duplicates, :message)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", providerCode);
        p.addValue("channel_code", channelCode);
        p.addValue("file_name", fileName);
        p.addValue("status", status);
        p.addValue("total", total);
        p.addValue("valid", valid);
        p.addValue("invalid", invalid);
        p.addValue("duplicates", duplicates);
        p.addValue("message", "valid=" + valid + ", invalid=" + invalid + ", duplicates=" + duplicates);
        jdbcTemplate.update(sql, p);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
    }
}

