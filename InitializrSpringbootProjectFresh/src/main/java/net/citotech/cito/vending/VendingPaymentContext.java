package net.citotech.cito.vending;

/**
 * Correlation context attached to every vending financial movement.
 *
 * <p>Carries the vendor code and optional connector/rental/device/OEM references that are written
 * into payment metadata ({@code cpayDomain=VENDING}, {@code vendingVendor}, {@code
 * vendingOperation}, ...) so ledger entries can always be traced back to the physical vending
 * operation that produced them. Connector, rental, device and OEM references are optional at
 * collection time because a deposit is collected before the OEM reference exists.
 */
public record VendingPaymentContext(
        String vendorCode,
        String connectorId,
        String rentalReference,
        String deviceId,
        String oemReference) {

    public static VendingPaymentContext of(String vendorCode) {
        return new VendingPaymentContext(vendorCode, null, null, null, null);
    }

    public VendingPaymentContext withConnectorId(String value) {
        return new VendingPaymentContext(
                vendorCode, value, rentalReference, deviceId, oemReference);
    }

    public VendingPaymentContext withRentalReference(String value) {
        return new VendingPaymentContext(vendorCode, connectorId, value, deviceId, oemReference);
    }

    public VendingPaymentContext withDeviceId(String value) {
        return new VendingPaymentContext(
                vendorCode, connectorId, rentalReference, value, oemReference);
    }

    public VendingPaymentContext withOemReference(String value) {
        return new VendingPaymentContext(vendorCode, connectorId, rentalReference, deviceId, value);
    }
}
