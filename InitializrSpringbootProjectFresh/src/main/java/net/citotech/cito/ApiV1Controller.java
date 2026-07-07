package net.citotech.cito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Versioned API controller at /api/v1/
 * Delegates all work to the original Api controller methods via shared services.
 * The original /api/ endpoints remain for backward compatibility.
 */
@RestController
@RequestMapping(path = "/api/v1")
public class ApiV1Controller {

    @Autowired
    private Api api;

    @PostMapping(path = "/doMobileMoneyPayIn")
    public String doMobileMoneyPayIn(@RequestBody String requestBody,
                                      HttpServletRequest request, HttpServletResponse response) {
        return api.doMobileMoneyPayIn(requestBody, request, response);
    }

    @PostMapping(path = "/doMobileMoneyPayOut")
    public String doMobileMoneyPayOut(@RequestBody String requestBody,
                                       HttpServletRequest request, HttpServletResponse response) {
        return api.doMobileMoneyPayOut(requestBody, request, response);
    }

    @PostMapping(path = "/doTransactionCheckStatus")
    public String doTransactionCheckStatus(@RequestBody String requestBody,
                                            HttpServletRequest request, HttpServletResponse response) {
        return api.doTransactionCheckStatus(requestBody, request, response);
    }

    @PostMapping(path = "/doGetBalances")
    public String doGetBalances(@RequestBody String requestBody,
                                 HttpServletRequest request, HttpServletResponse response) {
        return api.doGetBalances(requestBody, request, response);
    }

    @PostMapping(path = "/doSendSms")
    public String doSendSms(@RequestBody String requestBody,
                            HttpServletRequest request, HttpServletResponse response) {
        return api.doSendSms(requestBody, request, response);
    }
    @PostMapping(path = "/doSafaricomPayCallback")
    public String doSafaricomPayCallback(@RequestBody String requestBody,
                                          HttpServletRequest request, HttpServletResponse response) {
        return api.doSafaricomPayCallback(requestBody, request, response);
    }

    @PostMapping(path = "/doSafaricomPayInCallbackResults")
    public String doSafaricomPayInCallbackResults(@RequestBody String requestBody,
                                                   HttpServletRequest request, HttpServletResponse response) {
        return api.doSafaricomPayInCallbackResults(requestBody, request, response);
    }

    @PostMapping(path = "/doSafaricomPayOutCallbackResults")
    public String doSafaricomPayOutCallbackResults(@RequestBody String requestBody,
                                                    HttpServletRequest request, HttpServletResponse response) {
        return api.doSafaricomPayOutCallbackResults(requestBody, request, response);
    }

    @PostMapping(path = "/doSafaricomPayOutCallback")
    public String doSafaricomPayOutCallback(@RequestBody String requestBody,
                                             HttpServletRequest request, HttpServletResponse response) {
        return api.doSafaricomPayOutCallback(requestBody, request, response);
    }
}

