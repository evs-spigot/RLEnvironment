package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class QLearningPolicy implements Policy {

    private final Map<String, double[]> qTable = new HashMap<>();
    private final Random random = new Random();

    private final double alpha;      // learning rate
    private final double gamma;      // discount factor
    private final double epsilonStart;
    private final double epsilonEnd;
    private final int epsilonDecayEpisodes;

    private long episodesSeen = 0;

    public QLearningPolicy() {
        this(0.1, 0.95, 0.8, 0.05, 500);
    }

    public QLearningPolicy(double alpha,
                           double gamma,
                           double epsilonStart,
                           double epsilonEnd,
                           int epsilonDecayEpisodes) {
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilonStart = epsilonStart;
        this.epsilonEnd = epsilonEnd;
        this.epsilonDecayEpisodes = Math.max(1, epsilonDecayEpisodes);
    }

    @Override
    public Action chooseAction(Observation observation) {
        String stateKey = toStateKey(observation);
        double[] qValues = qTable.computeIfAbsent(stateKey,
                k -> new double[Action.values().length]);

        double epsilon = currentEpsilon();

        if (random.nextDouble() < epsilon) {
            return randomAction();
        }

        int bestIndex = 0;
        double bestValue = qValues[0];
        for (int i = 1; i < qValues.length; i++) {
            if (qValues[i] > bestValue) {
                bestValue = qValues[i];
                bestIndex = i;
            }
        }
        return Action.values()[bestIndex];
    }

    @Override
    public void observeTransition(Observation state,
                                  Action action,
                                  double reward,
                                  Observation nextState,
                                  boolean done) {
        String stateKey = toStateKey(state);
        String nextKey = toStateKey(nextState);

        double[] qValues = qTable.computeIfAbsent(stateKey,
                k -> new double[Action.values().length]);
        double[] nextQ = qTable.computeIfAbsent(nextKey,
                k -> new double[Action.values().length]);

        int actionIndex = action.ordinal();

        double maxNext = 0.0;
        if (!done) {
            maxNext = nextQ[0];
            for (int i = 1; i < nextQ.length; i++) {
                if (nextQ[i] > maxNext) {
                    maxNext = nextQ[i];
                }
            }
        }

        double target = done ? reward : reward + gamma * maxNext;
        double oldValue = qValues[actionIndex];
        double updated = oldValue + alpha * (target - oldValue);
        qValues[actionIndex] = updated;

        if (done) {
            episodesSeen++;
        }
    }

    @Override
    public void onEpisodeEnd() {
        // nothing extra for now
    }

    private double currentEpsilon() {
        double frac = Math.min(1.0, episodesSeen / (double) epsilonDecayEpisodes);
        return epsilonStart + frac * (epsilonEnd - epsilonStart);
    }

    private Action randomAction() {
        Action[] actions = Action.values();
        return actions[random.nextInt(actions.length)];
    }

    private String toStateKey(Observation observation) {
        double[] f = observation.getFeatures();
        StringBuilder sb = new StringBuilder(f.length * 3);
        for (int i = 0; i < f.length; i++) {
            int bin = quantizeToBin(f[i], 5);
            sb.append(bin);
            if (i < f.length - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    private int quantizeToBin(double value, int bins) {
        double clamped = Math.max(-1.0, Math.min(1.0, value));
        double normalized = (clamped + 1.0) / 2.0;
        int index = (int) Math.floor(normalized * bins);
        if (index >= bins) {
            index = bins - 1;
        }
        if (index < 0) {
            index = 0;
        }
        return index;
    }
}