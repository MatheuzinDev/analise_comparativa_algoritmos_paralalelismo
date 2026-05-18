import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class ChartGenerator {
    private static final int TOP = 60;
    private static final int LEFT = 80;
    private static final int RIGHT = 40;
    private static final int BOTTOM = 120;
    private static final int CHART_HEIGHT = 300;
    private static final String[] COLORS = {
            "#2563eb",
            "#16a34a",
            "#f97316",
            "#9333ea",
            "#dc2626",
            "#0891b2",
            "#4b5563"
    };

    private ChartGenerator() {
    }

    public static void generateAll(List<BenchmarkResult> results, Path outputDir) throws IOException {
        if (results == null) {
            throw new IllegalArgumentException("A lista de resultados nao pode ser nula.");
        }

        if (outputDir == null) {
            throw new IllegalArgumentException("O diretorio de graficos nao pode ser nulo.");
        }

        Files.createDirectories(outputDir);

        writeBarChart(
                outputDir.resolve("tempo_por_metodo.svg"),
                "Tempo medio por metodo",
                "Tempo medio (ms)",
                averageByMethod(results),
                "ms");

        writeBarChart(
                outputDir.resolve("tempo_por_arquivo.svg"),
                "Tempo medio por arquivo",
                "Tempo medio (ms)",
                averageByFile(results),
                "ms");

        writeBarChart(
                outputDir.resolve("speedup_cpu.svg"),
                "Speedup do ParallelCPU sobre SerialCPU",
                "Speedup medio",
                speedupByCpuThreadCount(results),
                "x");
    }

    private static Map<String, Double> averageByMethod(List<BenchmarkResult> results) {
        Map<String, AverageAccumulator> averages = new LinkedHashMap<>();

        for (BenchmarkResult result : results) {
            averages.computeIfAbsent(methodLabel(result), ignored -> new AverageAccumulator())
                    .add(result.elapsedMs());
        }

        return finishAverages(averages);
    }

    private static Map<String, Double> averageByFile(List<BenchmarkResult> results) {
        Map<String, AverageAccumulator> averages = new LinkedHashMap<>();

        for (BenchmarkResult result : results) {
            averages.computeIfAbsent(result.fileName(), ignored -> new AverageAccumulator())
                    .add(result.elapsedMs());
        }

        return finishAverages(averages);
    }

    private static Map<String, Double> speedupByCpuThreadCount(List<BenchmarkResult> results) {
        Map<String, AverageAccumulator> serialByFile = new LinkedHashMap<>();
        Map<Integer, Map<String, AverageAccumulator>> parallelByThreads = new TreeMap<>();

        for (BenchmarkResult result : results) {
            if ("SerialCPU".equals(result.method())) {
                serialByFile.computeIfAbsent(result.fileName(), ignored -> new AverageAccumulator())
                        .add(result.elapsedMs());
            } else if ("ParallelCPU".equals(result.method())) {
                parallelByThreads
                        .computeIfAbsent(result.threads(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(result.fileName(), ignored -> new AverageAccumulator())
                        .add(result.elapsedMs());
            }
        }

        Map<String, Double> speedups = new LinkedHashMap<>();

        for (Map.Entry<Integer, Map<String, AverageAccumulator>> threadEntry : parallelByThreads.entrySet()) {
            AverageAccumulator speedupAverage = new AverageAccumulator();

            for (Map.Entry<String, AverageAccumulator> serialEntry : serialByFile.entrySet()) {
                AverageAccumulator parallelAverage = threadEntry.getValue().get(serialEntry.getKey());
                if (parallelAverage == null || parallelAverage.average() <= 0.0) {
                    continue;
                }

                speedupAverage.add(serialEntry.getValue().average() / parallelAverage.average());
            }

            if (speedupAverage.count() > 0) {
                speedups.put(threadEntry.getKey() + " threads", speedupAverage.average());
            }
        }

        return speedups;
    }

    private static Map<String, Double> finishAverages(Map<String, AverageAccumulator> averages) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<String, AverageAccumulator> entry : averages.entrySet()) {
            values.put(entry.getKey(), entry.getValue().average());
        }

        return values;
    }

    private static String methodLabel(BenchmarkResult result) {
        if ("ParallelCPU".equals(result.method())) {
            return result.method() + " " + result.threads() + "t";
        }

        return result.method();
    }

    private static void writeBarChart(
            Path outputFile,
            String title,
            String axisLabel,
            Map<String, Double> values,
            String valueSuffix) throws IOException {
        int count = Math.max(1, values.size());
        int width = Math.max(760, LEFT + RIGHT + count * 110);
        int height = TOP + CHART_HEIGHT + BOTTOM;
        int chartWidth = width - LEFT - RIGHT;
        double maxValue = maxValue(values);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(width)
                .append("\" height=\"")
                .append(height)
                .append("\" viewBox=\"0 0 ")
                .append(width)
                .append(' ')
                .append(height)
                .append("\">\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        svg.append("<text x=\"").append(width / 2).append("\" y=\"30\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"20\" font-weight=\"700\" fill=\"#111827\">")
                .append(xml(title))
                .append("</text>\n");
        svg.append("<text x=\"20\" y=\"").append(TOP + CHART_HEIGHT / 2).append("\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"12\" fill=\"#4b5563\" transform=\"rotate(-90 20 ")
                .append(TOP + CHART_HEIGHT / 2)
                .append(")\">")
                .append(xml(axisLabel))
                .append("</text>\n");

        appendGrid(svg, width, maxValue);

        if (values.isEmpty()) {
            svg.append("<text x=\"").append(width / 2).append("\" y=\"").append(TOP + CHART_HEIGHT / 2).append("\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"16\" fill=\"#6b7280\">Sem dados suficientes</text>\n");
        } else {
            appendBars(svg, new ArrayList<>(values.entrySet()), chartWidth, maxValue, valueSuffix);
        }

        svg.append("</svg>\n");
        Files.writeString(outputFile, svg.toString(), StandardCharsets.UTF_8);
    }

    private static void appendGrid(StringBuilder svg, int width, double maxValue) {
        for (int i = 0; i <= 5; i++) {
            double percentage = i / 5.0;
            int y = TOP + CHART_HEIGHT - (int) Math.round(CHART_HEIGHT * percentage);
            double value = maxValue * percentage;

            svg.append("<line x1=\"").append(LEFT).append("\" y1=\"").append(y).append("\" x2=\"")
                    .append(width - RIGHT)
                    .append("\" y2=\"")
                    .append(y)
                    .append("\" stroke=\"#e5e7eb\" stroke-width=\"1\"/>\n");
            svg.append("<text x=\"").append(LEFT - 10).append("\" y=\"").append(y + 4).append("\" text-anchor=\"end\" font-family=\"Arial\" font-size=\"11\" fill=\"#6b7280\">")
                    .append(format(value))
                    .append("</text>\n");
        }

        svg.append("<line x1=\"").append(LEFT).append("\" y1=\"").append(TOP + CHART_HEIGHT).append("\" x2=\"")
                .append(width - RIGHT)
                .append("\" y2=\"")
                .append(TOP + CHART_HEIGHT)
                .append("\" stroke=\"#374151\" stroke-width=\"1\"/>\n");
        svg.append("<line x1=\"").append(LEFT).append("\" y1=\"").append(TOP).append("\" x2=\"").append(LEFT).append("\" y2=\"")
                .append(TOP + CHART_HEIGHT)
                .append("\" stroke=\"#374151\" stroke-width=\"1\"/>\n");
    }

    private static void appendBars(
            StringBuilder svg,
            List<Map.Entry<String, Double>> entries,
            int chartWidth,
            double maxValue,
            String valueSuffix) {
        entries.sort(Comparator.comparingInt(entry -> orderForLabel(entry.getKey())));

        double slotWidth = chartWidth / (double) entries.size();
        double barWidth = Math.min(58.0, slotWidth * 0.6);

        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Double> entry = entries.get(i);
            double value = entry.getValue();
            double barHeight = value <= 0.0 ? 0.0 : CHART_HEIGHT * (value / maxValue);
            double x = LEFT + i * slotWidth + (slotWidth - barWidth) / 2.0;
            double y = TOP + CHART_HEIGHT - barHeight;
            String color = COLORS[i % COLORS.length];

            svg.append("<rect x=\"").append(format(x)).append("\" y=\"").append(format(y)).append("\" width=\"")
                    .append(format(barWidth))
                    .append("\" height=\"")
                    .append(format(barHeight))
                    .append("\" fill=\"")
                    .append(color)
                    .append("\" rx=\"4\"/>\n");
            svg.append("<text x=\"").append(format(x + barWidth / 2.0)).append("\" y=\"").append(format(y - 8.0)).append("\" text-anchor=\"middle\" font-family=\"Arial\" font-size=\"11\" fill=\"#111827\">")
                    .append(format(value))
                    .append(xml(valueSuffix))
                    .append("</text>\n");

            double labelX = x + barWidth / 2.0;
            double labelY = TOP + CHART_HEIGHT + 18.0;
            svg.append("<text x=\"").append(format(labelX)).append("\" y=\"").append(format(labelY)).append("\" text-anchor=\"start\" font-family=\"Arial\" font-size=\"11\" fill=\"#374151\" transform=\"rotate(35 ")
                    .append(format(labelX))
                    .append(' ')
                    .append(format(labelY))
                    .append(")\">")
                    .append(xml(entry.getKey()))
                    .append("</text>\n");
        }
    }

    private static int orderForLabel(String label) {
        if ("SerialCPU".equals(label)) {
            return 0;
        }

        if (label.startsWith("ParallelCPU")) {
            return 10 + extractFirstNumber(label);
        }

        if ("ParallelGPU".equals(label)) {
            return 1000;
        }

        return 500;
    }

    private static int extractFirstNumber(String value) {
        StringBuilder number = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current >= '0' && current <= '9') {
                number.append(current);
            } else if (number.length() > 0) {
                break;
            }
        }

        if (number.length() == 0) {
            return 0;
        }

        return Integer.parseInt(number.toString());
    }

    private static double maxValue(Map<String, Double> values) {
        double max = 0.0;
        for (double value : values.values()) {
            max = Math.max(max, value);
        }

        return max <= 0.0 ? 1.0 : max * 1.15;
    }

    private static String format(double value) {
        if (value >= 100.0) {
            return String.format(Locale.ROOT, "%.0f", value);
        }

        if (value >= 10.0) {
            return String.format(Locale.ROOT, "%.1f", value);
        }

        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String xml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static final class AverageAccumulator {
        private double total;
        private int count;

        private void add(double value) {
            total += value;
            count++;
        }

        private double average() {
            return count == 0 ? 0.0 : total / count;
        }

        private int count() {
            return count;
        }
    }
}
