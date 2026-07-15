package net.citotech.cito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiV1ControllerTest {

    @Test
    void v1SmsEndpointDelegatesToLegacyApi() throws Exception {
        Api api = mock(Api.class);
        when(api.doSendSms(eq("{\"merchant_number\":\"1000000\"}"), any(HttpServletRequest.class), any(HttpServletResponse.class)))
            .thenReturn("{\"code\":\"000\"}");

        ApiV1Controller controller = new ApiV1Controller();
        ReflectionTestUtils.setField(controller, "api", api);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/v1/doSendSms")
                .contentType("application/json")
                .content("{\"merchant_number\":\"1000000\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"code\":\"000\"}"));

        verify(api).doSendSms(eq("{\"merchant_number\":\"1000000\"}"), any(HttpServletRequest.class), any(HttpServletResponse.class));
    }
}
