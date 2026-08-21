package net.citotech.cito.communication.provider;

/**
 * Capability matrix of a communication provider adapter (ISO domain mapping:
 * communication/provider). The registry and future capability table read these flags to decide
 * whether an adapter genuinely supports send, delivery receipts, inbound, or status query before
 * routing traffic to it.
 */
public record ProviderCapabilities(
        boolean send,
        boolean templates,
        boolean deliveryReceipts,
        boolean inbound,
        boolean statusQuery) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean send;
        private boolean templates;
        private boolean deliveryReceipts;
        private boolean inbound;
        private boolean statusQuery;

        public Builder send(boolean value) {
            this.send = value;
            return this;
        }

        public Builder templates(boolean value) {
            this.templates = value;
            return this;
        }

        public Builder deliveryReceipts(boolean value) {
            this.deliveryReceipts = value;
            return this;
        }

        public Builder inbound(boolean value) {
            this.inbound = value;
            return this;
        }

        public Builder statusQuery(boolean value) {
            this.statusQuery = value;
            return this;
        }

        public ProviderCapabilities build() {
            return new ProviderCapabilities(send, templates, deliveryReceipts, inbound, statusQuery);
        }
    }
}
