package io.mseemann.oteldemo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PII tokenization BEFORE the cloud LLM call - different from the OTel Collector's PII masking
 * (transform/mask-pii in otel-collector-config.yml), which only acts AFTER THE FACT on exported
 * telemetry and never sees the actual prompt sent to Anthropic. This class replaces PII with a
 * placeholder token before the call; the mapping stays local, is never sent to the LLM provider,
 * and is mapped back onto the answer afterwards. Same four categories as the Collector (email/
 * phone/IBAN/card), detected via regex only - no NER, so names/addresses aren't caught. IBAN/
 * phone patterns are German-format on purpose (this demo's data is German-market).
 */
final class PiiTokenizer {

    private static final Pattern EMAIL = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
    // \s? BEFORE each digit, not after - covers space-separated IBANs ("DE89 3704...") while
    // still always ending the match on a digit, so a trailing space is never swallowed.
    private static final Pattern IBAN = Pattern.compile("\\bDE\\d{2}(?:\\s?\\d){18}\\b");
    private static final Pattern CARD = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b(?:\\+49|0049|0)[\\s/-]?\\d[\\d\\s/-]{6,14}\\d\\b");

    // Order matters: IBAN/CARD (long digit runs) must be tokenized before PHONE, or PHONE could
    // grab them as a phone number. EMAIL is independent of the other three.
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
