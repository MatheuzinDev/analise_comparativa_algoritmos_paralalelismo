import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BenchmarkRunner {
    private static final Path DEFAULT_SAMPLES_DIR = Path.of("Amostras");
    private static final int DEFAULT_REPETITIONS = 3;
    private static final int[] DEFAULT_THREAD_COUNTS = { 2, 4, 8 };

    private final Path samplesDir;
    private final int repetitions;
    private final int[] threadCounts;

    public BenchmarkRunner() {
        this(DEFAULT_SAMPLES_DIR, DEFAULT_REPETITIONS, DEFAULT_THREAD_COUNTS);
    }

    public BenchmarkRunner(Path samplesDir, int repetitions, int[] threadCounts) {
        if (repetitions < 1) {
            throw new IllegalArgumentException("A quantidade de repeticoes deve ser maior que zero.");
        }

        if (threadCounts == null || threadCounts.length == 0) {
            throw new IllegalArgumentException("Informe pelo menos uma configuracao de threads.");
        }

        this.samplesDir = samplesDir;
        this.repetitions = repetitions;
        this.threadCounts = Arrays.copyOf(threadCounts, threadCounts.length);
    }

    public List<BenchmarkResult> runDefaultBenchmark() throws Exception {
        Map<Path, String> filesAndWords = new LinkedHashMap<>();
        filesAndWords.put(samplesDir.resolve("DonQuixote-388208.txt"), "que");
        filesAndWords.put(samplesDir.resolve("Dracula-165307.txt"), "the");
        filesAndWords.put(samplesDir.resolve("MobyDick-217452.txt"), "the");

        return runBenchmark(filesAndWords);
    }

    public List<BenchmarkResult> runBenchmarkWithSingleWord(String word) throws Exception {
        Map<Path, String> filesAndWords = new LinkedHashMap<>();
        filesAndWords.put(samplesDir.resolve("DonQuixote-388208.txt"), word);
        filesAndWords.put(samplesDir.resolve("Dracula-165307.txt"), word);
        filesAndWords.put(samplesDir.resolve("MobyDick-217452.txt"), word);

        return runBenchmark(filesAndWords);
    }

    public List<BenchmarkResult> runBenchmark(Map<Path, String> filesAndWords) throws Exception {
        if (filesAndWords == null || filesAndWords.isEmpty()) {
            throw new IllegalArgumentException("Informe pelo menos um arquivo para executar o benchmark.");
        }

        List<BenchmarkResult> results = new ArrayList<>();

        for (Map.Entry<Path, String> entry : filesAndWords.entrySet()) {
            results.addAll(runFileBenchmark(entry.getKey(), entry.getValue()));
        }

        return results;
    }

    public List<BenchmarkResult> runManualComparison(Path file, String word, int parallelCpuThreads) throws Exception {
        if (parallelCpuThreads < 1) {
            throw new IllegalArgumentException("A quantidade de threads deve ser maior que zero.");
        }

        PreparedInput input = prepareInput(file, word);
        List<BenchmarkResult> results = new ArrayList<>();

        results.add(measure(input, new SerialCPUCounter(), 1, 1));
        results.add(measure(input, new ParallelCPUCounter(parallelCpuThreads), parallelCpuThreads, 1));
        results.add(measure(input, new ParallelGPUCounter(), 0, 1));

        validateConsistentOccurrences(results);
        return results;
    }

    private List<BenchmarkResult> runFileBenchmark(Path file, String word) throws Exception {
        PreparedInput input = prepareInput(file, word);
        List<BenchmarkResult> results = new ArrayList<>();

        for (int repetition = 1; repetition <= repetitions; repetition++) {
            results.add(measure(input, new SerialCPUCounter(), 1, repetition));
        }

        for (int threadCount : threadCounts) {
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                results.add(measure(input, new ParallelCPUCounter(threadCount), threadCount, repetition));
            }
        }

        for (int repetition = 1; repetition <= repetitions; repetition++) {
            results.add(measure(input, new ParallelGPUCounter(), 0, repetition));
        }

        validateConsistentOccurrences(results);
        return results;
    }

    private static BenchmarkResult measure(
            PreparedInput input,
            WordCounter counter,
            int threads,
            int repetition) throws Exception {
        long start = System.nanoTime();
        long occurrences = counter.count(input.normalizedText, input.normalizedWordBytes);
        long elapsedNs = System.nanoTime() - start;

        return new BenchmarkResult(
                input.fileName,
                input.normalizedWord,
                counter.name(),
                threads,
                repetition,
                occurrences,
                elapsedNs / 1_000_000.0);
    }

    private static PreparedInput prepareInput(Path file, String word) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("O arquivo de entrada nao pode ser nulo.");
        }

        if (!Files.exists(file)) {
            throw new IOException("Arquivo nao encontrado: " + file);
        }

        String text = Files.readString(file, StandardCharsets.UTF_8);
        String normalizedText = TextNormalizer.normalizeText(text);
        String normalizedWord = TextNormalizer.normalizeWord(word);

        return new PreparedInput(
                file.getFileName().toString(),
                normalizedText.getBytes(StandardCharsets.US_ASCII),
                normalizedWord,
                normalizedWord.getBytes(StandardCharsets.US_ASCII));
    }

    private static void validateConsistentOccurrences(List<BenchmarkResult> results) {
        if (results.isEmpty()) {
            return;
        }

        long expected = results.get(0).occurrences();
        for (BenchmarkResult result : results) {
            if (result.occurrences() != expected) {
                throw new IllegalStateException(
                        "Contagem inconsistente no arquivo " + result.fileName()
                                + ": esperado " + expected
                                + ", mas " + result.method()
                                + " encontrou " + result.occurrences());
            }
        }
    }

    private static final class PreparedInput {
        private final String fileName;
        private final byte[] normalizedText;
        private final String normalizedWord;
        private final byte[] normalizedWordBytes;

        private PreparedInput(String fileName, byte[] normalizedText, String normalizedWord, byte[] normalizedWordBytes) {
            this.fileName = fileName;
            this.normalizedText = normalizedText;
            this.normalizedWord = normalizedWord;
            this.normalizedWordBytes = normalizedWordBytes;
        }
    }
}
