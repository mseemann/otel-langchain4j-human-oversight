package io.mseemann.oteldemo;

/**
 * Deterministic policy for the {@code propose_goodwill_gesture} tool (see LangChain4jController).
 * The committed value (a voucher amount) never comes from the model - the tool takes no amount
 * parameter, so there's no argument for the model to fill with an arbitrary value. evaluate()
 * executes nothing itself; a human decides via POST /lc4j/human-intervention whether the
 * proposal is acted on (see HumanOversightController).
 */
final class GoodwillGesturePolicy {

    // Fixed, not a range (see class comment) - a range would just reintroduce a degree of
    // freedom someone would have to fill.
    static final int VOUCHER_CENTS = 1500;

    // Matches the router's default order-number format ("ORD-4711"); anything else can't be
    // tied to a real order.
    private static final String ORDER_NUMBER_PATTERN = "ORD-\\d+";

    private GoodwillGesturePolicy() {
    }

    /**
     * @param voucherCents    always VOUCHER_CENTS - never set from a model value.
     * @param rejectionReason set only if accepted=false.
     */
    record Proposal(boolean accepted, String orderNumber, int voucherCents, String rejectionReason) {
        static Proposal accepted(String orderNumber) {
            return new Proposal(true, orderNumber, VOUCHER_CENTS, null);
        }

        static Proposal rejected(String rejectionReason) {
            return new Proposal(false, null, 0, rejectionReason);
        }
    }

    static Proposal evaluate(String orderNumber) {
        if (orderNumber == null || !orderNumber.matches(ORDER_NUMBER_PATTERN)) {
            return Proposal.rejected("invalid_order_number");
        }
        return Proposal.accepted(orderNumber);
    }
}
