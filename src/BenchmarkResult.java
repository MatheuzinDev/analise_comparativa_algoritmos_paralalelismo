public class BenchmarkResult {
    private final String fileName;
    private final String word;
    private final String method;
    private final int threads;
    private final int repetition;
    private final long occurrences;
    private final double elapsedMs;

    public BenchmarkResult(
            String fileName,
            String word,
            String method,
            int threads,
            int repetition,
            long occurrences,
            double elapsedMs) {
        this.fileName = fileName;
        this.word = word;
        this.method = method;
        this.threads = threads;
        this.repetition = repetition;
        this.occurrences = occurrences;
        this.elapsedMs = elapsedMs;
    }

    public String fileName() {
        return fileName;
    }

    public String word() {
        return word;
    }

    public String method() {
        return method;
    }

    public int threads() {
        return threads;
    }

    public int repetition() {
        return repetition;
    }

    public long occurrences() {
        return occurrences;
    }

    public double elapsedMs() {
        return elapsedMs;
    }

    @Override
    public String toString() {
        return "BenchmarkResult{" +
                "fileName='" + fileName + '\'' +
                ", word='" + word + '\'' +
                ", method='" + method + '\'' +
                ", threads=" + threads +
                ", repetition=" + repetition +
                ", occurrences=" + occurrences +
                ", elapsedMs=" + elapsedMs +
                '}';
    }
}
