package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class QLearningPolicy implements Policy {

    private final Map<String, double[]> q = new HashMap<>();
    private final Random rng = new Random();

    // Learning parameters
    private final double alpha;  // learning rate
    private final double gamma;  // discount

    // Exploration schedule
    private final double epsilonStart;
    private final double epsilonEnd;
    private final long epsilonDecayEpisodes;

    private long episodesSeen = 0;

    public QLearningPolicy() {
        // These tend to work well for small gridworlds
        this(0.20, 0.95, 0.60, 0.03, 600);
    }

    public QLearningPolicy(double alpha,
                           double gamma,
                           double epsilonStart,
                           double epsilonEnd,
                           long epsilonDecayEpisodes) {
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilonStart = epsilonStart;
        this.epsilonEnd = epsilonEnd;
        this.epsilonDecayEpisodes = Math.max(1, epsilonDecayEpisodes);
    }

    @Override
    public Action chooseAction(Observation observation) {
        String key = toStateKey(observation);
        double[] qValues = q.computeIfAbsent(key, k -> new double[Action.values().length]);

        double eps = currentEpsilon();

        // Epsilon-greedy
        if (rng.nextDouble() < eps) {
            return randomAction();
        }

        int bestIdx = argMax(qValues);
        return Action.values()[bestIdx];
    }

    @Override
    public void observeTransition(Observation state,
                                  Action action,
                                  double reward,
                                  Observation nextState,
                                  boolean done) {
        String sKey = toStateKey(state);
        String s2Key = toStateKey(nextState);

        double[] qs = q.computeIfAbsent(sKey, k -> new double[Action.values().length]);
        double[] qs2 = q.computeIfAbsent(s2Key, k -> new double[Action.values().length]);

        int a = action.ordinal();

        double maxNext = done ? 0.0 : qs2[argMax(qs2)];
        double target = reward + gamma * maxNext;

        qs[a] = qs[a] + alpha * (target - qs[a]);

        if (done) {
            episodesSeen++;
        }
    }

    private Action randomAction() {
        Action[] actions = Action.values();
        return actions[rng.nextInt(actions.length)];
    }

    private int argMax(double[] arr) {
        int best = 0;
        double bestVal = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > bestVal) {
                bestVal = arr[i];
                best = i;
            }
        }
        return best;
    }

    private double currentEpsilon() {
        double t = Math.min(1.0, episodesSeen / (double) epsilonDecayEpisodes);
        return epsilonStart + t * (epsilonEnd - epsilonStart);
    }

    /**
     * Matches the NEW observation layout from GoldCollectorEnvironment:
     * 0 dxSign (-1,0,1)
     * 1 dzSign (-1,0,1)
     * 2 normDist (continuous 0..~1)
     * 3..6 blocked bits
     * 7..10 hazard bits
     */
    private String toStateKey(Observation obs) {
        double[] f = obs.getFeatures();

        int dx = clampInt((int) Math.round(f[0]), -1, 1);
        int dz = clampInt((int) Math.round(f[1]), -1, 1);

        // distance bins (0..5)
        int distBin = (int) Math.floor(clamp01(f[2]) * 6.0);
        if (distBin >= 6) distBin = 5;

        StringBuilder sb = new StringBuilder(32);
        sb.append(dx).append(',').append(dz).append(',').append(distBin);

        // blocked/hazard bits should be exact 0/1
        for (int i = 3; i < f.length; i++) {
            sb.append(',').append(f[i] >= 0.5 ? 1 : 0);
        }

        return sb.toString();
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}