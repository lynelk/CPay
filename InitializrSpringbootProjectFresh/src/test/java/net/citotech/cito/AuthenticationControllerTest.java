package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthenticationControllerTest {

    @Test
    void merchantSessionCheckSupportsFrontendRouteAlias() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController()).build();

        MvcResult result = mockMvc.perform(post("/auth/isMerchantUserLoggedIn")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

        JSONObject json = new JSONObject(result.getResponse().getContentAsString());
        assertThat(json.getString("code")).isEqualTo("000");
        assertThat(json.getString("message")).isEqualTo("false");
    }

    @Test
    void merchantLogoutSupportsFrontendRouteAlias() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController()).build();

        MvcResult result = mockMvc.perform(post("/auth/logoutMerchantUser")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk())
            .andReturn();

        JSONObject json = new JSONObject(result.getResponse().getContentAsString());
        assertThat(json.getString("code")).isEqualTo("000");
        assertThat(json.getString("message")).isEqualTo("SUCCESS");
    }
}
