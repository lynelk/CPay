package net.citotech.cito.api.v2.dto;

public class HostedCheckoutPayRequest {
    private String payerAccount;
    private String channel;

    public String getPayerAccount() { return payerAccount; }
    public void setPayerAccount(String payerAccount) { this.payerAccount = payerAccount; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
}
