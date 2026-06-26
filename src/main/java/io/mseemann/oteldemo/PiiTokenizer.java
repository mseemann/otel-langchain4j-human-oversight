package io.mseemann.oteldemo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PII tokenization BEFORE the cloud LLM call.
 *
 * Different from the PII masking that also happens in the OTel Collector (transform/mask-pii in
 * otel-collector-config.yml): the Collector's masking acts AFTER THE FACT on whatever gets
 * exported/telemetered - the actual prompt sent to the cloud LLM provider (Anthropic) bypasses it
 * entirely, because the API call happens before any span is exported. This class intervenes
 * BEFORE the call: detected PII is replaced with a placeholder token, the mapping table stays
 * exclusively in this process (NEVER goes to the LLM provider), and the answer is mapped back
 * before being delivered to the user.
 *
 * Deliberately the same four categories as transform/mask-pii in the Collector (email / phone /
 * IBAN / card number) - consistent with the existing detection, not a second detection mechanism
 * with its own blind spots. Important, deliberately not hidden limitation: regex only catches
 * what LOOKS LIKE one of these four categories. Names, addresses, or other free-form context PII
 * are NOT detected - that would need NER (e.g. Presidio). This class is an additional line of
 * defense before the cloud call, not a replacement for better detection.
 *
 * Note: IBAN/phone patterns below are intentionally still German-format (DE.../+49...) - this
 * demo's "customer" data is German-market data, and the patterns are a direct, literal port of
 * the source project's detection. A real deployment would parametrize country/format.
 */
final class PiiTokenizer {

    private static final Pattern EMAIL = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    // Whitespace allowed BEFORE each digit (covers "DE89 3704 0044 0532 0130 00"), deliberately
    // NOT after: an optional "\s?" as the last element before the word boundary would wrongly
    // consume a real space AFTER the IBAN, because \b is still satisfied right after a consumed
    // space (space = non-word, next char is usually a word char) - the match would then wrongly
    // include a trailing space. With \s? before each digit, the match always ends on a digit.
    private static final Pattern IBAN = Pattern.compile("\\bDE\\d{2}(?:\\s?\\d){18}\\b");
    private static final Pattern CARD = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b(?:\\+49|0049|0)[\\s/-]?\\d[\\d\\s/-]{6,14}\\d\\b");

    // Order matters here, it's not arbitrary: IBAN and CARD are long, unbroken digit sequences
    // that the PHONE pattern (variable length, starts with "0"/"+49") could otherwise wrongly
    // grab as a phone number. By replacing IBAN/CARD with tokens first, PHONE never sees those
    // spots in the raw text anymore. EMAIL goes first because "@..." doesn't collide with any of
    // the other three patterns.
    private static final List<CategoryPattern> CATEGORIES = List.of(
            new CategoryPattern("EMAIL", EMAIL),
            new CategoryPattern("IBAN", IBAN),
            new CategoryPattern("CARD", CARD),
            new CategoryPattern("PHONE", PHONE));

    private record CategoryPattern(String label, Pattern pattern) {
    }

    /**
     * @param text            input text with placeholders like "[EMAIL_1]" instead of the real
     *                        values.
     * @param tokenToOriginal mapping token -> original value. Stays local, is NEVER sent to the
     *                        LLM provider.
     */
    record Tokenized(String text, Map<String, String> tokenToOriginal) {
    }

    private PiiTokenizer() {
    }

    static Tokenized tokenize(String input) {
        if (input == null || input.isEmpty()) {
            return new Tokenized(input, Map.of());
        }
        Map<String, String> tokenToOriginal = new LinkedHashMap<>();
        String result = input;
        for (CategoryPattern category : CATEGORIES) {
            result = replaceWithTokens(result, category, tokenToOriginal);
        }
        return new Tokenized(result, tokenToOriginal);
    }

    private static String replaceWithTokens(
            String text, CategoryPattern category, Map<String, String> tokenToOriginal) {
        Matcher matcher = category.pattern().matcher(text);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        int last = 0;
        while (matcher.find()) {
            count++;
            String token = "[" + category.label() + "_" + count + "]";
            tokenToOriginal.put(token, matcher.group());
            sb.append(text, last, matcher.start()).append(token);
            last = matcher.end();
        }
        sb.append(text.substring(last));
        return sb.toString();
    }

    /**
     * Reverses the tokenization - applied to the LLM answer BEFORE it's delivered to the end
     * user (in case the model repeats a token from the prompt in its answer, e.g. "I've noted the
     * callback number [PHONE_1]").
     */
    static String detokenize(String textWithTokens, Map<String, String> tokenToOriginal) {
        if (textWithTokens == null || tokenToOriginal.isEmpty()) {
            return textWithTokens;
        }
        String result = textWithTokens;
        for (Map.Entry<String, String> entry : tokenToOriginal.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
