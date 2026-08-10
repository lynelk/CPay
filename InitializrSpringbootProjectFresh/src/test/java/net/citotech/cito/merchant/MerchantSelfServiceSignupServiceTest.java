package net.citotech.cito.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Common;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

class MerchantSelfServiceSignupServiceTest {

    @Test
    void signupCreatesExplicitOwnerWithoutMerchantPrivilegeRows() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);

        List<MapSqlParameterSource> updateParams = new ArrayList<>();
        final long[] generatedId = {100};
        doAnswer(
                        invocation -> {
                            updateParams.add(invocation.getArgument(1));
                            return 1;
                        })
                .when(jdbcTemplate)
                .update(anyString(), any(MapSqlParameterSource.class));
        doAnswer(
                        invocation -> {
                            updateParams.add(invocation.getArgument(1));
                            KeyHolder holder = invocation.getArgument(2);
                            holder.getKeyList().add(Map.of("GENERATED_KEY", generatedId[0]++));
                            return 1;
                        })
                .when(jdbcTemplate)
                .update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class));

        MerchantSelfServiceSignupService service = new MerchantSelfServiceSignupService(jdbcTemplate);
        service.signup(
                Map.of(
                        "businessName",
                        "Acme Traders",
                        "shortName",
                        "ACME",
                        "accountType",
                        "BUSINESS",
                        "contactName",
                        "Acme Admin",
                        "email",
                        "admin@acme.test",
                        "phone",
                        "256700000000",
                        "password",
                        "secret-pass"));

        MapSqlParameterSource merchantInsert = updateParams.get(0);
        assertThat((String) merchantInsert.getValue("allowed_apis"))
                .contains(
                        Common.API_MOBILE_MONEY_PAYIN,
                        Common.API_MOBILE_MONEY_PAYOUT,
                        Common.API_TRANSACTION_CHECKSTATUS,
                        Common.API_BALANCE_CHECK,
                        Common.API_SEND_SMS);
        assertThat(merchantInsert.getValue("status")).isEqualTo("PENDING_APPROVAL");
        assertThat(merchantInsert.getValue("account_type")).isEqualTo("business");

        MapSqlParameterSource adminInsert =
                updateParams.stream()
                        .filter(params -> params.hasValue("role"))
                        .findFirst()
                        .orElseThrow();
        assertThat(adminInsert.getValue("role")).isEqualTo(MerchantRole.OWNER.name());
        assertThat(updateParams.stream().anyMatch(params -> params.hasValue("privilege"))).isFalse();
    }
}
