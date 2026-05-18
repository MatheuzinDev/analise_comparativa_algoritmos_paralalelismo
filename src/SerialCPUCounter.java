public class SerialCPUCounter implements WordCounter {
    @Override
    public String name() {
        return "SerialCPU";
    }

    @Override
    public long count(byte[] normalizedText, byte[] normalizedWord) {
        validateInput(normalizedText, normalizedWord);

        if (normalizedText.length == 0 || normalizedWord.length > normalizedText.length) {
            return 0;
        }

        long occurrences = 0;
        int index = 0;

        while (index < normalizedText.length) {
            int wordStart = index;

            while (index < normalizedText.length && normalizedText[index] != ' ') {
                index++;
            }

            int wordLength = index - wordStart;
            if (wordLength == normalizedWord.length && matches(normalizedText, normalizedWord, wordStart)) {
                occurrences++;
            }

            index++;
        }

        return occurrences;
    }

    private static boolean matches(byte[] text, byte[] word, int start) {
        for (int i = 0; i < word.length; i++) {
            if (text[start + i] != word[i]) {
                return false;
            }
        }

        return true;
    }

    private static void validateInput(byte[] normalizedText, byte[] normalizedWord) {
        if (normalizedText == null) {
            throw new IllegalArgumentException("O texto normalizado nao pode ser nulo.");
        }

        if (normalizedWord == null || normalizedWord.length == 0) {
            throw new IllegalArgumentException("A palavra normalizada nao pode ser vazia.");
        }
    }
}
