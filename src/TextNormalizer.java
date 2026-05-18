import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() {
    }

    public static String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder normalized = new StringBuilder(decomposed.length());
        boolean lastWasSpace = true;

        for (int i = 0; i < decomposed.length(); i++) {
            char current = decomposed.charAt(i);

            if (isDiacritic(current)) {
                continue;
            }

            if (isAsciiLetterOrDigit(current)) {
                normalized.append(current);
                lastWasSpace = false;
            } else if (!lastWasSpace) {
                normalized.append(' ');
                lastWasSpace = true;
            }
        }

        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }

        return normalized.toString();
    }

    public static String normalizeWord(String word) {
        String normalized = normalizeText(word);

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A palavra de busca nao pode ser vazia.");
        }

        if (normalized.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Informe apenas uma palavra para a busca: " + word);
        }

        return normalized;
    }

    public static byte[] normalizeTextToBytes(String text) {
        return normalizeText(text).getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] normalizeWordToBytes(String word) {
        return normalizeWord(word).getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9');
    }

    private static boolean isDiacritic(char value) {
        int type = Character.getType(value);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}
