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
    private final double gamma;  // discount factor

    // Exploration schedule
    private final double epsilonStart;
    private final double epsilonEnd;
    private final long epsilonDecayEpisodes;
    private long episodesSeen = 0;

    // Time-penalty shaping (encourages shortest paths)
    private final double timePenaltyBase;   // constant penalty each step (on top of env reward)
    private final double timePenaltySlope;  // grows with step index: penalty += slope * stepIndex
    private int stepIndexInEpisode = 0;

    public QLearningPolicy() {
        // Good defaults for a small gridworld-ish environment
        this(
                0.20, 0.95,
                0.60, 0.03, 600,
                0.00, 0.01 // prev 0.002
        );
        // timePenaltyBase = 0.00 means "only increasing penalty"
        // timePenaltySlope = 0.002: by step 200 penalty ~ 0.4 additional (noticeable but not insane)
    }

    public QLearningPolicy(double alpha,
                           double gamma,
                           double epsilonStart,
                           double epsilonEnd,
                           long epsilonDecayEpisodes,
                           double timePenaltyBase,
                           double timePenaltySlope) {
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilonStart = epsilonStart;
        this.epsilonEnd = epsilonEnd;
        this.epsilonDecayEpisodes = Math.max(1, epsilonDecayEpisodes);
        this.timePenaltyBase = Math.max(0.0, timePenaltyBase);
        this.timePenaltySlope = Math.max(0.0, timePenaltySlope);
    }

    @Override
    public Action chooseAction(Observation observation) {
        String key = toStateKey(observation);
        double[] qValues = q.computeIfAbsent(key, k -> new double[Action.values().length]);

        double eps = currentEpsilon();
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

        // stepIndexInEpisode starts at 0, increments each transition
        double timePenalty = timePenaltyBase + (timePenaltySlope * stepIndexInEpisode);
        double shapedReward = reward - timePenalty;

        String sKey = toStateKey(state);
        String s2Key = toStateKey(nextState);

        double[] qs = q.computeIfAbsent(sKey, k -> new double[Action.values().length]);
        double[] qs2 = q.computeIfAbsent(s2Key, k -> new double[Action.values().length]);

        int a = action.ordinal();

        double maxNext = done ? 0.0 : qs2[argMax(qs2)];
        double target = shapedReward + gamma * maxNext;

        qs[a] = qs[a] + alpha * (target - qs[a]);

        stepIndexInEpisode++;

        if (done) {
            episodesSeen++;
            stepIndexInEpisode = 0;
        }
    }

    @Override
    public void onEpisodeEnd() {
        // Safety reset (in case something ends an episode without calling observeTransition(done=true))
        stepIndexInEpisode = 0;
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
     * This matches the newer observation format (direction + distance bins + bits)
     *
     * Expected layout (from your improved environment):
     * 0 dxSign (-1,0,1)
     * 1 dzSign (-1,0,1)
     * 2 dySign (-1,0,1)  (if present)
     * 3 normDist (0..~1)
     * 4.. bits (blocked/hazard/etc)
     */
    private String toStateKey(Observation obs) {
        double[] f = obs.getFeatures();

        // Handle either layout:
        // If dySign: dx, dz, dy, dist...
        // If not: dx, dz, dist...
        int idx = 0;

        int dx = clampInt((int) Math.round(f[idx++]), -1, 1);
        int dz = clampInt((int) Math.round(f[idx++]), -1, 1);

        // If there is a third sign feature (dy), include it; otherwise treat as 0
        int dy = 0;
        if (f.length >= 4) {
            dy = clampInt((int) Math.round(f[idx++]), -1, 1);
        }

        double distVal = f[idx++];
        int distBin = (int) Math.floor(clamp01(distVal) * 6.0);
        if (distBin >= 6) distBin = 5;

        StringBuilder sb = new StringBuilder(48);
        sb.append(dx).append(',').append(dz).append(',').append(dy).append(',').append(distBin);

        for (int i = idx; i < f.length; i++) {
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