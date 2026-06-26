package io.mseemann.oteldemo;

/**
 * Deterministic policy for the {@code propose_goodwill_gesture} tool (see LangChain4jController).
 *
 * The point isn't "call a tool" - it's that the committed value (a voucher amount) never
 * comes from the model in any form. The tool intentionally takes no amount parameter: if it
 * did, the vulnerability would just move one layer down - the model could propose the same
 * arbitrarily high value via tool arguments instead of free text, and a code path that accepts
 * that value uncritically wouldn't be a real architectural fix.
 *
 * evaluate() doesn't execute anything itself (no payment, no credit) - it only decides whether
 * an order number is plausible enough to be worth escalating. Whether the proposal is actually
 * acted on is decided exclusively by a human via POST /lc4j/human-intervention (see
 * HumanOversightController).
 */
final class GoodwillGesturePolicy {

    // Fixed amount instead of a model parameter (see class comment above). Deliberately a single
    // fixed value, not a range - a range would just reintroduce a degree of freedom that
    // something (model or code) would have to fill.
    static final int VOUCHER_CENTS = 1500;

    // Same fixed format as the orderNumber default in the router ("ORD-4711") - no regex
    // library, no NLP, just a simple pattern match. An order number that doesn't match this
    // can't be tied to a real order anyway - there's nothing to escalate.
    private static final String ORDER_NUMBER_PATTERN = "ORD-\\d+";

    private GoodwillGesturePolicy() {
    }

    /**
     * @param accepted        true if orderNumber matches the expected format.
     * @param orderNumber     the validated order number (only set if accepted=true).
     * @param voucherCents    ALWAYS VOUCHER_CENTS, never anything else - there is no code path
     *                        that fills this field with a model-supplied value.
     * @param rejectionReason reason for rejection (only set if accepted=false), for the
     *                        tool.output / audit trail.
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
