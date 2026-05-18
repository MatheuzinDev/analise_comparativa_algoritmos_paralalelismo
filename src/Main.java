import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class Main {
    private static final Path CSV_OUTPUT = Path.of("resultados", "resultados.csv");
    private static final Path CHARTS_OUTPUT_DIR = Path.of("resultados", "graficos");

    public static void main(String[] args) {
        try {
            if (args.length == 0 || "ajuda".equalsIgnoreCase(args[0]) || "help".equalsIgnoreCase(args[0])) {
                printUsage();
                return;
            }

            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "contar" -> runManualCount(args);
                case "benchmark" -> runBenchmark(args);
                default -> {
                    System.err.println("Comando desconhecido: " + args[0]);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (UnsatisfiedLinkError error) {
            System.err.println("Erro ao carregar biblioteca nativa OpenCL: " + error.getMessage());
            System.err.println("Verifique se o driver OpenCL esta instalado e se libOpenCL.so esta disponivel.");
            System.err.println("No Ubuntu, normalmente o pacote ocl-icd-opencl-dev fornece esse link.");
            System.exit(1);
        } catch (Exception exception) {
            System.err.println("Erro: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void runManualCount(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Uso invalido do comando contar.");
            printUsage();
            System.exit(1);
        }

        Path file = Path.of(args[1]);
        String word = args[2];
        int threads = args.length == 4 ? parseThreadCount(args[3]) : Runtime.getRuntime().availableProcessors();

        BenchmarkRunner runner = new BenchmarkRunner();
        List<BenchmarkResult> results = runner.runManualComparison(file, word, threads);

        System.out.println("Arquivo: " + file);
        System.out.println("Palavra: " + TextNormalizer.normalizeWord(word));
        System.out.println("Threads ParallelCPU: " + threads);
        System.out.println();
        printResultsTable(results);
    }

    private static void runBenchmark(String[] args) throws Exception {
        if (args.length > 2) {
            System.err.println("Uso invalido do comando benchmark.");
            printUsage();
            System.exit(1);
        }

        BenchmarkRunner runner = new BenchmarkRunner();
        List<BenchmarkResult> results;

        if (args.length == 2) {
            results = runner.runBenchmarkWithSingleWord(args[1]);
        } else {
            results = runner.runDefaultBenchmark();
        }

        CsvWriter.write(CSV_OUTPUT, results);
        ChartGenerator.generateAll(results, CHARTS_OUTPUT_DIR);

        System.out.println("Benchmark concluido.");
        System.out.println("CSV gerado em: " + CSV_OUTPUT);
        System.out.println("Graficos gerados em: " + CHARTS_OUTPUT_DIR);
        System.out.println();
        printSearchedWords(results);
        System.out.println();
        printSummary(results);
    }

    private static int parseThreadCount(String value) {
        try {
            int threads = Integer.parseInt(value);
            if (threads < 1) {
                throw new IllegalArgumentException("A quantidade de threads deve ser maior que zero.");
            }

            return threads;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Quantidade de threads invalida: " + value);
        }
    }

    private static void printResultsTable(List<BenchmarkResult> results) {
        System.out.printf("%-18s %8s %10s %14s%n", "Metodo", "Threads", "Ocorr.", "Tempo (ms)");
        System.out.println("--------------------------------------------------------");

        for (BenchmarkResult result : results) {
            System.out.printf(
                    Locale.ROOT,
                    "%-18s %8d %10d %14.3f%n",
                    result.method(),
                    result.threads(),
                    result.occurrences(),
                    result.elapsedMs());
        }
    }

    private static void printSummary(List<BenchmarkResult> results) {
        Map<String, AverageAccumulator> averages = new TreeMap<>();

        for (BenchmarkResult result : results) {
            String key = result.fileName() + " | " + result.word() + " | " + methodLabel(result);
            averages.computeIfAbsent(key, ignored -> new AverageAccumulator())
                    .add(result.elapsedMs(), result.occurrences());
        }

        System.out.printf("%-60s %12s %14s%n", "Arquivo | Palavra | Metodo", "Ocorrencias", "Media (ms)");
        System.out.println("----------------------------------------------------------------------------------------");

        averages.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> System.out.printf(
                        Locale.ROOT,
                        "%-60s %12d %14.3f%n",
                        entry.getKey(),
                        entry.getValue().occurrences(),
                        entry.getValue().averageMs()));
    }

    private static void printSearchedWords(List<BenchmarkResult> results) {
        Map<String, String> wordsByFile = new TreeMap<>();

        for (BenchmarkResult result : results) {
            wordsByFile.put(result.fileName(), result.word());
        }

        System.out.println("Palavras buscadas:");
        for (Map.Entry<String, String> entry : wordsByFile.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private static String methodLabel(BenchmarkResult result) {
        if ("ParallelCPU".equals(result.method())) {
            return result.method() + " " + result.threads() + " threads";
        }

        return result.method();
    }

    private static void printUsage() {
        System.out.println("Uso:");
        System.out.println("  java -cp build:jocl-2.0.4.jar Main contar <arquivo> <palavra> [threads]");
        System.out.println("  java -cp build:jocl-2.0.4.jar Main benchmark");
        System.out.println("  java -cp build:jocl-2.0.4.jar Main benchmark <palavra-unica>");
        System.out.println();
        System.out.println("Exemplos:");
        System.out.println("  java -cp build:jocl-2.0.4.jar Main contar Amostras/Dracula-165307.txt the");
        System.out.println("  java -cp build:jocl-2.0.4.jar Main contar Amostras/Dracula-165307.txt vampire 4");
        System.out.println("  java -cp build:jocl-2.0.4.jar Main benchmark");
    }

    private static final class AverageAccumulator {
        private double totalMs;
        private int count;
        private long occurrences;

        private void add(double elapsedMs, long occurrences) {
            totalMs += elapsedMs;
            count++;
            this.occurrences = occurrences;
        }

        private double averageMs() {
            return count == 0 ? 0.0 : totalMs / count;
        }

        private long occurrences() {
            return occurrences;
        }
    }
}
