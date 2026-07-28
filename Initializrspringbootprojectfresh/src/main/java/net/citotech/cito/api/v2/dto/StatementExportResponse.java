package net.citotech.cito.api.v2.dto;

import java.math.BigDecimal;
import java.util.List;

public class StatementExportResponse {
    private String merchantNumber;
    private String startDate;
    private String endDate;
    private int count;
    private List<StatementRow> rows;

    public String getMerchantNumber() {
        return merchantNumber;
    }

    public void setMerchantNumber(String merchantNumber) {
        this.merchantNumber = merchantNumber;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<StatementRow> getRows() {
        return rows;
    }

    public void setRows(List<StatementRow> rows) {
        this.rows = rows;
    }

    public static class StatementRow {
        private long id;
        private String createdOn;
        private String gatewayId;
        private String transactionType;
        private String description;
        private String narrative;
        private BigDecimal amount;
        private String currency;
        private BigDecimal mtnBalance;
        private BigDecimal airtelBalance;
        private BigDecimal safaricomBalance;
        private BigDecimal smsBalance;
        private String merchantReference;
        private String transactionId;
        private String transactionStatus;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getCreatedOn() {
            return createdOn;
        }

        public void setCreatedOn(String createdOn) {
            this.createdOn = createdOn;
        }

        public String getGatewayId() {
            return gatewayId;
        }

        public void setGatewayId(String gatewayId) {
            this.gatewayId = gatewayId;
        }

        public String getTransactionType() {
            return transactionType;
        }

        public void setTransactionType(String transactionType) {
            this.transactionType = transactionType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getNarrative() {
            return narrative;
        }

        public void setNarrative(String narrative) {
            this.narrative = narrative;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public BigDecimal getMtnBalance() {
            return mtnBalance;
        }

        public void setMtnBalance(BigDecimal mtnBalance) {
            this.mtnBalance = mtnBalance;
        }

        public BigDecimal getAirtelBalance() {
            return airtelBalance;
        }

        public void setAirtelBalance(BigDecimal airtelBalance) {
            this.airtelBalance = airtelBalance;
        }

        public BigDecimal getSafaricomBalance() {
            return safaricomBalance;
        }

        public void setSafaricomBalance(BigDecimal safaricomBalance) {
            this.safaricomBalance = safaricomBalance;
        }

        public BigDecimal getSmsBalance() {
            return smsBalance;
        }

        public void setSmsBalance(BigDecimal smsBalance) {
            this.smsBalance = smsBalance;
        }

        public String getMerchantReference() {
            return merchantReference;
        }

        public void setMerchantReference(String merchantReference) {
            this.merchantReference = merchantReference;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getTransactionStatus() {
            return transactionStatus;
        }

        public void setTransactionStatus(String transactionStatus) {
            this.transactionStatus = transactionStatus;
        }
    }
}
