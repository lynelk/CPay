package net.citotech.cito.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentPartyRequest;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SanctionsScreeningServiceTest {

    @Test
    void returnsBlockDecisionAndRecordsHitWhenWatchlistMatches() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper<?> mapper = invocation.getArgument(2);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("id")).thenReturn(7L);
                when(rs.getString("list_name")).thenReturn("TEST_SANCTIONS");
                when(rs.getString("risk_rating")).thenReturn("HIGH");
                when(rs.getString("action")).thenReturn("BLOCK");
                when(rs.getString("summary")).thenReturn("Blocked account");
                return List.of(mapper.mapRow(rs, 0));
            });

        SanctionsScreeningService service = new SanctionsScreeningService(jdbcTemplate);

        RiskDecision decision = service.screenPayment(merchant(), request(), "PAYOUT");

        assertThat(decision.getDecision()).isEqualTo("BLOCK");
        assertThat(decision.getReasonCode()).isEqualTo("SANCTIONS_MATCH");
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    private Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId(17L);
        merchant.setName("");
        merchant.setAccount_number("");
        return merchant;
    }

    private PaymentRequest request() {
        PaymentPartyRequest payee = new PaymentPartyRequest();
        payee.setType("ACCOUNT");
        payee.setValue("256770000000");

        PaymentRequest request = new PaymentRequest();
        request.setReference("SCREEN-1");
        request.setPayee(payee);
        return request;
    }
}
