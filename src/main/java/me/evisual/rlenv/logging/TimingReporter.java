package me.evisual.rlenv.logging;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.logging.Logger;

public final class TimingReporter implements Closeable {

    private final Logger logger;
    private final File reportFile;
    private final long reportIntervalNanos;

    private final long startNanos;
    private long lastReportNanos;

    private long stepCount = 0;
    private long tickCount = 0;
    private long episodeCount = 0;

    private long totalStepNanos = 0;
    private long totalTickNanos = 0;
    private long maxStepNanos = 0;
    private long maxTickNanos = 0;

    public TimingReporter(Logger logger, File dataFolder, int reportIntervalSeconds) {
        this.logger = logger;
        this.reportFile = new File(dataFolder, "timing-report.txt");
        this.reportIntervalNanos = Math.max(1, reportIntervalSeconds) * 1_000_000_000L;
        this.startNanos = System.nanoTime();
        this.lastReportNanos = startNanos;
    }

    public void recordStep(long nanos) {
        stepCount++;
        totalStepNanos += nanos;
        if (nanos > maxStepNanos) {
            maxStepNanos = nanos;
        }
    }

    public void recordTick(long nanos) {
        tickCount++;
        totalTickNanos += nanos;
        if (nanos > maxTickNanos) {
            maxTickNanos = nanos;
        }
    }

    public void recordEpisode() {
        episodeCount++;
    }

    public void maybeReport() {
        long now = System.nanoTime();
        if ((now - lastReportNanos) < reportIntervalNanos) {
            return;
        }
        lastReportNanos = now;
        logger.info(buildReportLine(now, false));
    }

    @Override
    public void close() {
        writeSummary();
    }

    private void writeSummary() {
        long now = System.nanoTime();
        String sb = "RLEnv timing summary" + System.lineSeparator() +
                buildReportLine(now, true) + System.lineSeparator();
        writeReportFile(sb);
    }

    private String buildReportLine(long now, boolean includeHeader) {
        long elapsedNanos = Math.max(1L, now - startNanos);
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;

        double avgStepMs = stepCount == 0 ? 0.0 : (totalStepNanos / 1_000_000.0) / stepCount;
        double avgTickMs = tickCount == 0 ? 0.0 : (totalTickNanos / 1_000_000.0) / tickCount;
        double maxStepMs = maxStepNanos / 1_000_000.0;
        double maxTickMs = maxTickNanos / 1_000_000.0;

        double stepsPerSecond = stepCount / elapsedSeconds;
        double ticksPerSecond = tickCount / elapsedSeconds;
        double episodesPerMinute = (episodeCount * 60.0) / elapsedSeconds;

        StringBuilder line = new StringBuilder(256);
        if (includeHeader) {
            line.append("Elapsed: ").append(formatDuration(elapsedNanos)).append(" | ");
        }
        line.append("steps=").append(stepCount)
                .append(" (avg ").append(String.format("%.3f", avgStepMs)).append(" ms, max ")
                .append(String.format("%.3f", maxStepMs)).append(" ms, ")
                .append(String.format("%.1f", stepsPerSecond)).append(" steps/s) | ");
        line.append("ticks=").append(tickCount)
                .append(" (avg ").append(String.format("%.3f", avgTickMs)).append(" ms, max ")
                .append(String.format("%.3f", maxTickMs)).append(" ms, ")
                .append(String.format("%.1f", ticksPerSecond)).append(" ticks/s) | ");
        line.append("episodes=").append(episodeCount)
                .append(" (").append(String.format("%.2f", episodesPerMinute)).append(" /min)");
        return line.toString();
    }

    private String formatDuration(long nanos) {
        Duration d = Duration.ofNanos(nanos);
        long seconds = d.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private void writeReportFile(String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile, false))) {
            writer.write(content);
        } catch (IOException e) {
            logger.warning("Failed to write timing report: " + e.getMessage());
        }
    }
}
