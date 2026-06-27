package io.mseemann.oteldemo;

/**
 * Purely deterministic status lookup - a plain function, not a model-driven tool. No reason to
 * call the model again for fixed demo data.
 */
final class OrderStatusLookup {

    private OrderStatusLookup() {
    }

    record Result(String trackingId, String status, String location, String estimatedDelivery) {
    }

    static Result lookup(String orderNumber) {
        String digits = orderNumber == null ? "" : orderNumber.replaceAll("[^0-9]", "");
        String trackingId = "TRK-" + digits;
        // Fixed demo values; a real integration would call a carrier API here - still always
        // server-side, never model-formulated.
        return new Result(trackingId, "in_transit", "Hamburg distribution center", "2026-06-21");
    }
}
