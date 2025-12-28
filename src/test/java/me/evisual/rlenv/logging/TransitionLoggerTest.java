package me.evisual.rlenv.logging;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionLoggerTest {

    @Test
    void writesHeaderAndSingleLine(@TempDir Path dir) throws IOException {
        TransitionLogger logger = new TransitionLogger(dir.toFile());
        Observation s1 = new Observation(new double[] { 1.0, 0.0, 0.5 });
        Observation s2 = new Observation(new double[] { 0.0, 1.0, 0.25 });

        logger.logTransition(s1, Action.MOVE_NORTH, 0.5, s2, true);
        logger.close();

        Path file = dir.resolve("transitions.csv");
        List<String> lines = Files.readAllLines(file);
        assertEquals(2, lines.size());
        assertEquals("obs,action,reward,next_obs,done", lines.get(0));
        assertTrue(lines.get(1).startsWith("1.000000;0.000000;0.500000,"));
    }
}
