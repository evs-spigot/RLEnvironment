package me.evisual.rlenv.logging;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;

import java.io.*;
import java.util.Arrays;
import java.util.stream.Collectors;

public class TransitionLogger implements Closeable {

    private final BufferedWriter writer;
    private boolean closed = false;

    public TransitionLogger(File dataFolder) {
        try {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File file = new File(dataFolder, "transitions.csv");
            boolean newFile = !file.exists() || file.length() == 0;

            this.writer = new BufferedWriter(new FileWriter(file, true));

            if (newFile) {
                writeHeader();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize TransitionLogger", e);
        }
    }

    private void writeHeader() throws IOException {
        // obs_0,...,obs_n,action,reward,next_obs_0,...,next_obs_n,done
        writer.write("obs,action,reward,next_obs,done");
        writer.newLine();
        writer.flush();
    }

    public synchronized void logTransition(Observation state,
                                           Action action,
                                           double reward,
                                           Observation nextState,
                                           boolean done) {
        if (closed) {
            return;
        }

        double[] s = state.getFeatures();
        double[] sNext = nextState.getFeatures();

        String obsStr = joinArray(s);
        String nextObsStr = joinArray(sNext);
        int actionIndex = action.ordinal();
        int doneFlag = done ? 1 : 0;

        String line = obsStr + "," + actionIndex + "," + formatDouble(reward) + "," + nextObsStr + "," + doneFlag;

        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String joinArray(double[] arr) {
        return Arrays.stream(arr)
                .mapToObj(this::formatDouble)
                .collect(Collectors.joining(";"));
    }

    private String formatDouble(double value) {
        return String.format("%.6f", value);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}