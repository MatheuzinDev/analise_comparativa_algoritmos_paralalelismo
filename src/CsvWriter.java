import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class CsvWriter {
    private CsvWriter() {
    }

    public static void write(Path outputFile, List<BenchmarkResult> results) throws IOException {
        if (outputFile == null) {
            throw new IllegalArgumentException("O caminho do CSV nao pode ser nulo.");
        }

        if (results == null) {
            throw new IllegalArgumentException("A lista de resultados nao pode ser nula.");
        }

        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write("arquivo,palavra,metodo,threads,repeticao,ocorrencias,tempo_ms");
            writer.newLine();

            for (BenchmarkResult result : results) {
                writer.write(toCsvLine(result));
                writer.newLine();
            }
        }
    }

    private static String toCsvLine(BenchmarkResult result) {
        return csv(result.fileName()) + ","
                + csv(result.word()) + ","
                + csv(result.method()) + ","
                + result.threads() + ","
                + result.repetition() + ","
                + result.occurrences() + ","
                + String.format(Locale.ROOT, "%.3f", result.elapsedMs());
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }

        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;

        if (!needsQuotes) {
            return value;
        }

        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
