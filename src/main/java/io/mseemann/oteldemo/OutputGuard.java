package io.mseemann.oteldemo;

import java.util.List;
import java.util.Locale;

/**
 * Output Guard: deterministic check for unauthorized commitments in the LLM answer before it
 * reaches the customer - the last line of defense, not a replacement for better detection.
 * Background: Air Canada was held liable for a refund policy its chatbot had simply invented,
 * and a Chevrolet dealer's chatbot was prompt-injected into a "legally binding" one-dollar offer
 * - in both cases the operator was liable regardless of whether a human ever approved it. This
 * is only the deny-list layer; a real guardrails framework or hallucination detection isn't
 * built here (needs an external service, or isn't deterministic itself).
 */
final class OutputGuard {

    // Avoids bare words like "free" (would block a legit "free shipping" answer) - only
    // phrasings combining a value/commitment claim with bindingness, or extreme-discount
    // phrasing as in the Chevy case. Same false-positive/negative trade-off as elsewhere, but
    // weighted the other way: a false positive here reaches the customer directly.
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
     * @param matchedPattern the first matched pattern (audit trail - see
     *                       human_oversight.output_guard_matched_pattern), or null if
     *                       blocked=false.
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
