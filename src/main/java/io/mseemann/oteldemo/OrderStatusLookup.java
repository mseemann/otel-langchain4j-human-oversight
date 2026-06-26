package io.mseemann.oteldemo;

/**
 * Purely deterministic status lookup. A standalone, pure function (String -> Result) rather
 * than a model-driven tool: for the "nothing unusual" case there's no reason to call the model a
 * second time for a plain demo lookup - the values here are fixed demo data anyway, not a real
 * carrier API call.
 */
final class OrderStatusLookup {

    private OrderStatusLookup() {
    }

    record Result(String trackingId, String status, String location, String estimatedDelivery) {
    }

    static Result lookup(String orderNumber) {
        String digits = orderNumber == null ? "" : orderNumber.replaceAll("[^0-9]", "");
        String trackingId = "TRK-" + digits;
        // Fixed demo values - a real integration would call a carrier API here, but that doesn't
        // change the architectural point: the result is always determined server-side, never
        // formulated by the model.
        return new Result(trackingId, "in_transit", "Hamburg distribution center", "2026-06-21");
    }
}
