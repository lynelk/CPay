package net.citotech.cito.merchant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.api.v2.MerchantStatementExportService;
import net.citotech.cito.api.v2.dto.StatementExportResponse;
import net.citotech.cito.api.v2.dto.StatementExportResponse.StatementRow;
import net.citotech.cito.security.SimpleRateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Covers audit N3: a merchant portal user with only a session (no v2 signing keys) must be able to
 * export their own statement, without the allowed_apis check the signed v2 API enforces.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class MerchantSelfServiceControllerStatementsTest {

    @Test
    void rejectsTheRequestWhenThereIsNoMerchantSession() throws Exception {
        MockMvc mockMvc = mockMvc(mock(NamedParameterJdbcTemplate.class), mock(MerchantStatementExportService.class));

        mockMvc.perform(get("/api/v2/merchant-self-service/statements")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MERCHANT_SESSION_REQUIRED"));
    }

    @Test
    void returnsTheStatementForTheLoggedInMerchant() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(contains("merchants"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                when(rs.getString("name")).thenReturn("Test Merchant");
                when(rs.getString("short_name")).thenReturn("Test");
                when(rs.getString("account_number")).thenReturn("1000003");
                when(rs.getString("status")).thenReturn("ACTIVE");
                when(rs.getLong("id")).thenReturn(7L);
                when(rs.getString("created_on")).thenReturn("");
                when(rs.getString("created_by")).thenReturn("");
                when(rs.getString("account_type")).thenReturn("business");
                when(rs.getString("public_key")).thenReturn("");
                when(rs.getString("private_key")).thenReturn("");
                when(rs.getString("hmac_secret")).thenReturn("");
                when(rs.getString("allowed_apis")).thenReturn("");
                return List.of(mapper.mapRow(rs, 1));
            });
        when(jdbcTemplate.query(contains("merchant_admins"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        MerchantStatementExportService statementExportService = mock(MerchantStatementExportService.class);
        StatementExportResponse export = new StatementExportResponse();
        export.setMerchantNumber("1000003");
        export.setCount(1);
        export.setRows(List.of(new StatementRow()));
        when(statementExportService.exportForPortal(any(), org.mockito.ArgumentMatchers.eq("2026-01-01"),
                org.mockito.ArgumentMatchers.eq("2026-01-31"), any(), any()))
            .thenReturn(export);

        MockMvc mockMvc = mockMvc(jdbcTemplate, statementExportService);

        MerchantUser user = new MerchantUser();
        user.setMerchant_id(7L);

        mockMvc.perform(get("/api/v2/merchant-self-service/statements")
                .param("startDate", "2026-01-01")
                .param("endDate", "2026-01-31")
                .with(sessionWithMerchantUser(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.merchantNumber").value("1000003"))
            .andExpect(jsonPath("$.count").value(1));
    }

    private RequestPostProcessor sessionWithMerchantUser(MerchantUser user) {
        return request -> {
            HttpSession session = request.getSession(true);
            session.setAttribute("merchantUser", user);
            return request;
        };
    }

    private MockMvc mockMvc(NamedParameterJdbcTemplate jdbcTemplate, MerchantStatementExportService statementExportService) {
        MerchantSelfServiceController controller = new MerchantSelfServiceController(
            mock(MerchantSelfServiceSignupService.class),
            mock(MerchantChannelCredentialService.class),
            mock(MerchantEnvironmentService.class),
            mock(net.citotech.cito.reconciliation.MerchantSettlementPreferenceService.class),
            mock(SimpleRateLimitService.class),
            statementExportService,
            jdbcTemplate,
            mock(net.citotech.cito.merchant.MerchantNotificationPreferenceService.class),
            mock(MerchantEmailVerificationService.class)
        );
        return MockMvcBuilders.standaloneSetup(controller).build();
    }
}
