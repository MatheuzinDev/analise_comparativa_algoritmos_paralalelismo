import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ParallelCPUCounter implements WordCounter {
    private final int threadCount;

    public ParallelCPUCounter(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("A quantidade de threads deve ser maior que zero.");
        }

        this.threadCount = threadCount;
    }

    @Override
    public String name() {
        return "ParallelCPU";
    }

    public int threadCount() {
        return threadCount;
    }

    @Override
    public long count(byte[] normalizedText, byte[] normalizedWord) throws Exception {
        validateInput(normalizedText, normalizedWord);

        if (normalizedText.length == 0 || normalizedWord.length > normalizedText.length) {
            return 0;
        }

        int taskCount = Math.min(threadCount, normalizedText.length);
        int chunkSize = (normalizedText.length + taskCount - 1) / taskCount;
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);

        try {
            List<Future<Long>> futures = new ArrayList<>();

            for (int start = 0; start < normalizedText.length; start += chunkSize) {
                int rangeStart = start;
                int rangeEnd = Math.min(start + chunkSize, normalizedText.length);

                futures.add(executor.submit(() -> countRange(normalizedText, normalizedWord, rangeStart, rangeEnd)));
            }

            long total = 0;
            for (Future<Long> future : futures) {
                total += future.get();
            }

            return total;
        } finally {
            executor.shutdown();
        }
    }

    private static long countRange(byte[] text, byte[] word, int startInclusive, int endExclusive) {
        long occurrences = 0;
        int index = startInclusive;

        if (index > 0 && text[index - 1] != ' ') {
            while (index < text.length && text[index] != ' ') {
                index++;
            }

            if (index < text.length) {
                index++;
            }
        }

        while (index < text.length && index < endExclusive) {
            while (index < text.length && text[index] == ' ') {
                index++;
            }

            if (index >= text.length || index >= endExclusive) {
                break;
            }

            int wordStart = index;
            while (index < text.length && text[index] != ' ') {
                index++;
            }

            int wordLength = index - wordStart;
            if (wordStart < endExclusive && wordLength == word.length && matches(text, word, wordStart)) {
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
