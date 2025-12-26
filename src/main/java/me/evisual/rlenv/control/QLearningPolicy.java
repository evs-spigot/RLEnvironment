package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;

import java.util.Arrays;
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

    // Improvements
    private final double optimisticInit;    // initial Q value for unseen states
    private final boolean useActionMasking; // respect blocked bits if available
    private final double qMin, qMax;        // clamp Q values for stability

    // Adaptive epsilon
    private double adaptiveBoost = 0.0;          // added on top of schedule
    private double targetRecentSuccess = 0.90;   // try to keep recent success around this
    private double boostStrength = 0.60;         // how hard we push epsilon up when struggling
    private double boostSmoothing = 0.15;        // 0..1, higher reacts faster
    private double maxAdaptiveBoost = 0.60;      // cap extra exploration
    private double epsilonSmoothingEpisode = 0.15; // 0..1, lower = steadier

    private double lastEpsilon = 0.0;
    private double adaptiveEpsilon = 0.0;
    private double tempBoost = 0.0;
    private int tempBoostRemaining = 0;
    private double tempBoostStep = 0.0;

    public QLearningPolicy() {
        this(
                0.20, 0.95,
                0.60, 0.03, 600,
                0.00, 0.01,      // time shaping (current slope)
                1.0,             // optimistic init (helps get moving toward goal sooner)
                true,            // action masking if blocked bits exist
                -100.0, 100.0    // Q clamp
        );
    }

    public QLearningPolicy(double alpha,
                           double gamma,
                           double epsilonStart,
                           double epsilonEnd,
                           long epsilonDecayEpisodes,
                           double timePenaltyBase,
                           double timePenaltySlope,
                           double optimisticInit,
                           boolean useActionMasking,
                           double qMin,
                           double qMax) {
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilonStart = epsilonStart;
        this.epsilonEnd = epsilonEnd;
        this.epsilonDecayEpisodes = Math.max(1, epsilonDecayEpisodes);
        this.timePenaltyBase = Math.max(0.0, timePenaltyBase);
        this.timePenaltySlope = Math.max(0.0, timePenaltySlope);

        this.optimisticInit = optimisticInit;
        this.useActionMasking = useActionMasking;
        this.qMin = qMin;
        this.qMax = qMax;

        this.adaptiveEpsilon = epsilonStart;
        this.lastEpsilon = epsilonStart;
    }

    @Override
    public Action chooseAction(Observation observation) {
        String key = toStateKey(observation);
        double[] qValues = q.computeIfAbsent(key, k -> createInitialQ());

        double eps = effectiveEpsilon();
        lastEpsilon = eps;

        // Epsilon-greedy exploration (random valid action)
        if (rng.nextDouble() < eps) {
            return randomValidAction(observation);
        }

        int bestIdx = argMaxWithTies(qValues, observation);
        return Action.values()[bestIdx];
    }

    @Override
    public void observeTransition(Observation state,
                                  Action action,
                                  double reward,
                                  Observation nextState,
                                  boolean done) {

        // Increasing time cost (stronger pressure for shortest path)
        double timePenalty = timePenaltyBase + (timePenaltySlope * stepIndexInEpisode);
        double shapedReward = reward - timePenalty;

        String sKey = toStateKey(state);
        String s2Key = toStateKey(nextState);

        double[] qs = q.computeIfAbsent(sKey, k -> createInitialQ());
        double[] qs2 = q.computeIfAbsent(s2Key, k -> createInitialQ());

        int a = action.ordinal();

        // If next state has blocked actions, ignore them when computing max
        double maxNext = done ? 0.0 : maxQ(qs2, nextState);

        double target = shapedReward + gamma * maxNext;

        qs[a] = clamp(qs[a] + alpha * (target - qs[a]));

        stepIndexInEpisode++;

        if (done) {
            episodesSeen++;
            stepIndexInEpisode = 0;
        }
    }

    @Override
    public void onEpisodeEnd() {
        stepIndexInEpisode = 0;
    }

    // -----------------------------
    // Exposed stats (for /rlenv status)
    // -----------------------------

    public double getEpsilon() {
        return lastEpsilon > 0.0 ? lastEpsilon : effectiveEpsilon();
    }

    public int getStateCount() {
        return q.size();
    }

    public long getEpisodesSeen() {
        return episodesSeen;
    }

    public int getStepIndexInEpisode() {
        return stepIndexInEpisode;
    }

    // -----------------------------
    // Internals
    // -----------------------------

    private double[] createInitialQ() {
        double[] arr = new double[Action.values().length];
        Arrays.fill(arr, optimisticInit);
        return arr;
    }

    private Action randomValidAction(Observation obs) {
        // If we have blocked bits, avoid choosing blocked moves
        boolean[] blocked = useActionMasking ? extractBlocked(obs) : null;

        // Build a small list of allowed actions (no allocations: try a few times then fallback)
        for (int tries = 0; tries < 12; tries++) {
            Action a = Action.values()[rng.nextInt(Action.values().length)];
            if (a == Action.STAY) return a; // always valid
            if (blocked == null) return a;

            if (a == Action.MOVE_NORTH && !blocked[0]) return a;
            if (a == Action.MOVE_SOUTH && !blocked[1]) return a;
            if (a == Action.MOVE_EAST  && !blocked[2]) return a;
            if (a == Action.MOVE_WEST  && !blocked[3]) return a;
        }

        // Fallback: pick first unblocked move; else stay
        if (!blocked[0]) return Action.MOVE_NORTH;
        if (!blocked[1]) return Action.MOVE_SOUTH;
        if (!blocked[2]) return Action.MOVE_EAST;
        if (!blocked[3]) return Action.MOVE_WEST;
        return Action.STAY;
    }

    private int argMaxWithTies(double[] arr, Observation obs) {
        boolean[] blocked = useActionMasking ? extractBlocked(obs) : null;

        int best = -1;
        double bestVal = -Double.MAX_VALUE;
        int ties = 0;

        for (int i = 0; i < arr.length; i++) {
            Action a = Action.values()[i];

            if (blocked != null && isMoveBlocked(a, blocked)) {
                continue;
            }

            double v = arr[i];
            if (v > bestVal) {
                bestVal = v;
                best = i;
                ties = 1;
            } else if (v == bestVal && best != -1) {
                // random tie break
                ties++;
                if (rng.nextInt(ties) == 0) best = i;
            }
        }

        // If all moves blocked (rare), allow STAY
        if (best == -1) return Action.STAY.ordinal();
        return best;
    }

    private double maxQ(double[] qValues, Observation nextState) {
        boolean[] blocked = useActionMasking ? extractBlocked(nextState) : null;

        double best = -Double.MAX_VALUE;
        boolean found = false;

        for (int i = 0; i < qValues.length; i++) {
            Action a = Action.values()[i];
            if (blocked != null && isMoveBlocked(a, blocked)) continue;

            best = Math.max(best, qValues[i]);
            found = true;
        }

        return found ? best : 0.0;
    }

    private boolean isMoveBlocked(Action a, boolean[] blocked) {
        return (a == Action.MOVE_NORTH && blocked[0])
                || (a == Action.MOVE_SOUTH && blocked[1])
                || (a == Action.MOVE_EAST  && blocked[2])
                || (a == Action.MOVE_WEST  && blocked[3]);
    }

    /**
     * Extract "blocked" bits if observation includes them.
     * <p>
     * Supports both:
     * - GoldCollectorEnvironment observation: dx,dz,dy,dist, blockedN,blockedS,blockedE,blockedW
     * - Progression observation: dx,dz,dist (no blocked bits)
     */
    private boolean[] extractBlocked(Observation obs) {
        double[] f = obs.getFeatures();

        // If it's the short progression observation, no blocking info
        // progression: [dx, dz, dist] -> length 3
        if (f.length < 8) return null;

        // assume layout: ... then 4 blocked bits at the end or starting at index 4
        // In your GoldCollectorEnvironment earlier: indexes 4..7 were blocked
        int start = 4;
        return new boolean[]{
                f[start] >= 0.5,
                f[start + 1] >= 0.5,
                f[start + 2] >= 0.5,
                f[start + 3] >= 0.5
        };

    }

    private double currentEpsilon() {
        double t = Math.min(1.0, episodesSeen / (double) epsilonDecayEpisodes);
        return epsilonStart + t * (epsilonEnd - epsilonStart);
    }

    private double clamp(double v) {
        if (v < qMin) return qMin;
        return Math.min(v, qMax);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double smoothValue(double current, double target, double smoothing) {
        double t = clamp01(smoothing);
        return current + t * (target - current);
    }

    /**
     * State key that works for both of your environments:
     * - Progression: [dx, dz, dist]
     * - GoldCollector: [dx, dz, dy, dist, blocked...]
     */
    private String toStateKey(Observation obs) {
        double[] f = obs.getFeatures();

        int idx = 0;

        int dx = clampInt((int) Math.round(f[idx++]), -1, 1);
        int dz = clampInt((int) Math.round(f[idx++]), -1, 1);

        int dy = 0;
        // If we have >=4, assume dx,dz,dy,dist
        // If we have 3, assume dx,dz,dist
        boolean hasDy = f.length >= 4;
        if (hasDy) {
            dy = clampInt((int) Math.round(f[idx++]), -1, 1);
        }

        double distVal = f[idx++];
        int distBin = (int) Math.floor(clamp01(distVal) * 8.0); // finer bins than 6
        if (distBin >= 8) distBin = 7;

        StringBuilder sb = new StringBuilder(64);
        sb.append(dx).append(',').append(dz).append(',').append(dy).append(',').append(distBin);

        // Include blocked bits if present (helps state discrimination)
        if (f.length >= 8) {
            for (int i = 4; i < 8; i++) {
                sb.append(',').append(f[i] >= 0.5 ? 1 : 0);
            }
        }

        return sb.toString();
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private double effectiveEpsilon() {
        // Performance-driven epsilon that can drop faster if success improves.
        double eps = adaptiveEpsilon + adaptiveBoost + tempBoost;
        if (eps < epsilonEnd) eps = epsilonEnd;
        if (eps > epsilonStart) eps = epsilonStart;
        return eps;
    }

    public void updatePerformance(double recentSuccessRate) {
        // If performance is below target, increase boost; if above, decay boost.
        double error = (targetRecentSuccess - recentSuccessRate); // positive => struggling
        double desiredBoost = clamp01(error * boostStrength);     // 0..boostStrength
        desiredBoost = Math.min(desiredBoost, maxAdaptiveBoost);

        // Smooth so it doesn't jitter
        adaptiveBoost = adaptiveBoost + boostSmoothing * (desiredBoost - adaptiveBoost);

        // Performance-driven epsilon: higher success => lower epsilon.
        double epsTarget = epsilonEnd + (epsilonStart - epsilonEnd) * (1.0 - clamp01(recentSuccessRate));
        adaptiveEpsilon = smoothValue(adaptiveEpsilon, epsTarget, epsilonSmoothingEpisode);
        adaptiveEpsilon = clamp(adaptiveEpsilon, epsilonEnd, epsilonStart);

        if (tempBoostRemaining > 0) {
            tempBoost = Math.max(0.0, tempBoost - tempBoostStep);
            tempBoostRemaining--;
            if (tempBoostRemaining == 0) {
                tempBoost = 0.0;
                tempBoostStep = 0.0;
            }
        }
    }

    public void boostEpsilonToAtLeast(double minEpsilon, int decayEpisodes) {
        double current = lastEpsilon > 0.0 ? lastEpsilon : adaptiveEpsilon;
        double boost = minEpsilon - current;
        if (boost <= 0.0) return;

        int episodes = Math.max(1, decayEpisodes);
        tempBoost = Math.max(tempBoost, boost);
        tempBoostRemaining = Math.max(tempBoostRemaining, episodes);
        tempBoostStep = tempBoost / (double) tempBoostRemaining;
    }
}
