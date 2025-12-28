package me.evisual.rlenv.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingReporterTest {

    @Test
    void writesSummaryFileOnClose(@TempDir Path dir) throws IOException {
        Logger logger = Logger.getLogger("TimingReporterTest");
        TimingReporter reporter = new TimingReporter(logger, dir.toFile(), 1);
        reporter.recordStep(1_000_000L);
        reporter.recordTick(2_000_000L);
        reporter.recordEpisode();
        reporter.maybeReport();
        reporter.close();

        Path report = dir.resolve("timing-report.txt");
        List<String> lines = Files.readAllLines(report);
        assertTrue(lines.get(0).contains("RLEnv timing summary"));
        assertTrue(lines.get(1).contains("steps="));
        assertTrue(lines.get(1).contains("ticks="));
        assertTrue(lines.get(1).contains("episodes="));
    }
}
