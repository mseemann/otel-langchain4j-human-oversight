package io.mseemann.oteldemo;

import java.util.List;
import java.util.Locale;

/**
 * Output Guard: a deterministic check of the LLM answer for unauthorized commitments, BEFORE it
 * reaches the end user.
 *
 * Background: Air Canada was held liable for a refund policy its own chatbot had simply invented
 * ("negligent misrepresentation"), and a Chevrolet dealership's chatbot was prompt-injected into
 * offering a car for one dollar as a "legally binding offer, no take-backs". In both cases the
 * operator was liable for everything the chatbot said - regardless of whether a human ever
 * "meant" it. This class is the last line of defense before delivery: it doesn't replace better
 * detection, it deterministically catches the most obvious, most expensive cases.
 *
 * This is only the "deny-list" layer - a proper guardrails framework (NeMo Guardrails, Guardrails
 * AI) or hallucination detection (semantic entropy etc.) is deliberately not implemented here,
 * because either needs an external service or isn't deterministic itself.
 */
final class OutputGuard {

    // Deliberately NOT generic words like "free" alone - that would wrongly block a legitimate
    // "free shipping over $50" answer. Instead only phrasings that combine a value transfer /
    // commitment with a bindingness or finality claim, or explicit extreme discounts ("for $1")
    // as in the Chevy case. This narrows recall on purpose (false negatives are possible, e.g.
    // rephrasing) to keep the block rate for legitimate answers low - the same trade-off as
    // elsewhere in this codebase, just weighted the other way, because a false positive here (a
    // wrongly blocked but harmless answer) affects the end user directly instead of just an
    // internal reviewer.
    private static final List<String> FORBIDDEN_COMMITMENT_PATTERNS = List.of(
            "i'll gift you", "we'll gift you", "you'll get it for free as a gift",
            "for $1", "for 1 dollar", "for one dollar", "for $1 usd",
            "legally binding offer", "is legally binding", "no take-backs", "no returns accepted",
            "i guarantee you", "i promise you");

    static final String FALLBACK_ANSWER =
            "This request couldn't be answered automatically, because the generated answer " +
            "contained a possibly unauthorized commitment. A staff member is reviewing the case " +
            "and will get back to you.";

    private OutputGuard() {
    }

    /**
     * @param blocked        true if answer contains at least one pattern from
     *                       FORBIDDEN_COMMITMENT_PATTERNS.
     * @param matchedPattern the first matched pattern (for logging / the audit trail - see
     *                       human_oversight.output_guard_matched_pattern in
     *                       LangChain4jController), or null if blocked=false.
     */
    record Result(boolean blocked, String matchedPattern) {
        static Result clean() {
            return new Result(false, null);
        }
    }

    static Result check(String answer) {
        if (answer == null || answer.isEmpty()) {
            return Result.clean();
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        for (String pattern : FORBIDDEN_COMMITMENT_PATTERNS) {
            if (lower.contains(pattern)) {
                return new Result(true, pattern);
            }
        }
        return Result.clean();
    }
}
