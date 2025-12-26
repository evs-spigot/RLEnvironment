package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;
import me.evisual.rlenv.env.RLEnvironment;
import me.evisual.rlenv.env.StepResult;
import me.evisual.rlenv.env.goldcollector.GoldCollectorEnvironment;
import me.evisual.rlenv.logging.TransitionLogger;
import me.evisual.rlenv.visual.AgentVisualizer;
import me.evisual.rlenv.visual.ProgressGraphVisualizer;
import org.bukkit.scheduler.BukkitRunnable;

public class EpisodeRunner extends BukkitRunnable {

    private final RLEnvironment environment;
    private final TransitionLogger logger;
    private final Policy policy;
    private final AgentVisualizer visualizer;
    private final ProgressGraphVisualizer graph;

    private Observation currentObservation;
    private boolean closed = false;

    // Speed: environment steps per second (can be < 1.0)
    private double stepsPerSecond = 10.0;
    private double stepAccumulator = 0.0;

    // Safety cap so we don't spiral if someone sets absurd speeds
    private int maxStepsPerTick = 200;

    private long episodesCompleted = 0;

    private double currentEpisodeReward = 0.0;
    private int stepsThisEpisode = 0;

    // Reward moving average (kept for graph)
    private final double[] rewardWindow = new double[50];
    private int rewardWindowSize = 0;
    private int rewardWindowIndex = 0;
    private double rewardWindowSum = 0.0;

    // Graph sampling: push one point every N episodes (helps performance at high speed)
    private final int graphSampleEveryEpisodes = 5;
    private final int resetDelayTicks = 8;
    private int resetCooldownTicks = 0;

    // ✅ Learning / improvement stats
    private long startMillis = System.currentTimeMillis();

    private long successCount = 0;
    private long failureCount = 0;

    private long totalStepsToGoal = 0; // only counts successful episodes
    private int bestStepsToGoal = Integer.MAX_VALUE;

    // Recent window metrics (shows improvement over time)
    private static final int RECENT_WINDOW = 50;
    private final boolean[] recentSuccess = new boolean[RECENT_WINDOW];
    private final int[] recentStepsToGoal = new int[RECENT_WINDOW]; // -1 for failures
    private int recentSize = 0;
    private int recentIndex = 0;

    public EpisodeRunner(RLEnvironment environment,
                         TransitionLogger logger,
                         Policy policy,
                         AgentVisualizer visualizer,
                         ProgressGraphVisualizer graph) {
        this.environment = environment;
        this.logger = logger;
        this.policy = policy;
        this.visualizer = visualizer;
        this.graph = graph;

        this.currentObservation = environment.reset();
        updateVisualizer();
    }

    @Override
    public void run() {
        if (closed) {
            cancel();
            return;
        }

        if (resetCooldownTicks > 0) {
            resetCooldownTicks--;
            if (resetCooldownTicks == 0) {
                currentObservation = environment.reset();
                currentEpisodeReward = 0.0;
                stepsThisEpisode = 0;
                teleportVisualizerToCurrent();
            }
            return;
        }

        if (currentObservation == null) {
            currentObservation = environment.getObservation();
        }

        // Convert steps/sec into steps per tick using an accumulator
        stepAccumulator += (stepsPerSecond / 20.0);

        int stepsToRun = (int) Math.floor(stepAccumulator);
        if (stepsToRun <= 0) {
            updateVisualizer();
            return;
        }

        stepAccumulator -= stepsToRun;

        if (stepsToRun > maxStepsPerTick) {
            stepsToRun = maxStepsPerTick;
        }

        for (int i = 0; i < stepsToRun; i++) {
            if (closed) break;

            Action action = policy.chooseAction(currentObservation);
            StepResult result = environment.step(action);

            currentEpisodeReward += result.getReward();
            stepsThisEpisode++;

            logger.logTransition(
                    currentObservation,
                    action,
                    result.getReward(),
                    result.getObservation(),
                    result.isDone()
            );

            policy.observeTransition(
                    currentObservation,
                    action,
                    result.getReward(),
                    result.getObservation(),
                    result.isDone()
            );

            currentObservation = result.getObservation();

            if (result.isDone()) {
                boolean success = finishEpisode(result);
                policy.onEpisodeEnd();
                if (success) {
                    showGoalBreakEffect();
                }
                resetCooldownTicks = resetDelayTicks;
                break;
            }
        }
        updateVisualizer();
    }

    private boolean finishEpisode(StepResult lastStep) {
        episodesCompleted++;

        boolean success = lastStep.getReward() > 0.0;

        if (success) {
            successCount++;
            totalStepsToGoal += stepsThisEpisode;
            if (stepsThisEpisode < bestStepsToGoal) bestStepsToGoal = stepsThisEpisode;

            // ✅ obvious “hit” indicator
            if (visualizer != null) {
                visualizer.onGoalHit();
            }
        } else {
            failureCount++;
        }

        // recent ring buffer
        if (recentSize < RECENT_WINDOW) recentSize++;
        recentSuccess[recentIndex] = success;
        recentStepsToGoal[recentIndex] = success ? stepsThisEpisode : -1;
        recentIndex = (recentIndex + 1) % RECENT_WINDOW;

        // reward window (for graph)
        if (rewardWindowSize < rewardWindow.length) {
            rewardWindow[rewardWindowIndex] = currentEpisodeReward;
            rewardWindowSum += currentEpisodeReward;
            rewardWindowSize++;
            rewardWindowIndex = (rewardWindowIndex + 1) % rewardWindow.length;
        } else {
            double old = rewardWindow[rewardWindowIndex];
            rewardWindow[rewardWindowIndex] = currentEpisodeReward;
            rewardWindowSum += (currentEpisodeReward - old);
            rewardWindowIndex = (rewardWindowIndex + 1) % rewardWindow.length;
        }

        double movingAvgReward = rewardWindowSum / Math.max(1, rewardWindowSize);

        if (graph != null && (episodesCompleted % graphSampleEveryEpisodes == 0)) {
            graph.addAvgRewardPoint(movingAvgReward);

            if (policy instanceof QLearningPolicy qlp) {
                graph.addEpsilonPoint(qlp.getEpsilon());
            }
        }

        // Feed adaptive epsilon with recent performance
        if (policy instanceof QLearningPolicy qlp) {
            qlp.updatePerformance(recentSuccessRate());
        }
        return success;
    }

    private void updateVisualizer() {
        if (visualizer == null) return;

        if (environment instanceof me.evisual.rlenv.env.goldcollector.GoldCollectorEnvironment env) {
            visualizer.updatePosition(env.getAgentX(), env.getAgentY(), env.getAgentZ());
            return;
        }

        if (environment instanceof me.evisual.rlenv.env.goldcollector.ProgressionGoldEnvironment env) {
            int y = env.getConfig().y() + 1;
            visualizer.updatePosition(env.getAgentX(), y, env.getAgentZ());
        }
    }

    private void teleportVisualizerToCurrent() {
        if (visualizer == null) return;

        if (environment instanceof me.evisual.rlenv.env.goldcollector.GoldCollectorEnvironment env) {
            visualizer.teleportTo(env.getAgentX(), env.getAgentY(), env.getAgentZ());
            return;
        }

        if (environment instanceof me.evisual.rlenv.env.goldcollector.ProgressionGoldEnvironment env) {
            int y = env.getConfig().y() + 1;
            visualizer.teleportTo(env.getAgentX(), y, env.getAgentZ());
        }
    }

    private void showGoalBreakEffect() {
        if (visualizer == null) return;

        if (environment instanceof me.evisual.rlenv.env.goldcollector.GoldCollectorEnvironment env) {
            int blockY = env.getGoalY() - 1;
            visualizer.showGoalBreak(env.getGoalX(), blockY, env.getGoalZ());
            return;
        }

        if (environment instanceof me.evisual.rlenv.env.goldcollector.ProgressionGoldEnvironment env) {
            int blockY = env.getConfig().y() + 1;
            visualizer.showGoalBreak(env.getGoalX(), blockY, env.getGoalZ());
        }
    }

    public void shutdown() {
        closed = true;
        cancel();
        logger.close();
        if (visualizer != null) visualizer.destroy();
    }

    public void setStepsPerSecond(double stepsPerSecond) {
        if (stepsPerSecond < 0.1) stepsPerSecond = 0.1;   // 1 step every 10 seconds
        if (stepsPerSecond > 2000) stepsPerSecond = 2000; // safety
        this.stepsPerSecond = stepsPerSecond;
    }

    public double getStepsPerSecond() {
        return stepsPerSecond;
    }

    private double recentSuccessRate() {
        if (recentSize == 0) return 0.0;
        int wins = 0;
        for (int i = 0; i < recentSize; i++) if (recentSuccess[i]) wins++;
        return wins / (double) recentSize;
    }

    private double recentAvgStepsToGoal() {
        int count = 0;
        int sum = 0;
        for (int i = 0; i < recentSize; i++) {
            int v = recentStepsToGoal[i];
            if (v >= 0) { sum += v; count++; }
        }
        return count == 0 ? 0.0 : (sum / (double) count);
    }

    public EpisodeStats snapshotStats() {
        double overallSuccessRate = episodesCompleted == 0 ? 0.0 : (successCount / (double) episodesCompleted);
        double overallAvgStepsToGoal = successCount == 0 ? 0.0 : (totalStepsToGoal / (double) successCount);

        long elapsed = Math.max(1, System.currentTimeMillis() - startMillis);
        double episodesPerMinute = (episodesCompleted * 60000.0) / elapsed;

        // Optional policy stats (if your QLearningPolicy exposes them)
        double epsilon = -1.0;
        int stateCount = -1;
        if (policy instanceof QLearningPolicy qlp) {
            epsilon = qlp.getEpsilon();
            stateCount = qlp.getStateCount();
        }

        return new EpisodeStats(
                episodesCompleted,
                successCount,
                failureCount,
                overallSuccessRate,
                recentSuccessRate(),
                overallAvgStepsToGoal,
                recentAvgStepsToGoal(),
                bestStepsToGoal == Integer.MAX_VALUE ? -1 : bestStepsToGoal,
                episodesPerMinute,
                epsilon,
                stateCount,
                stepsPerSecond
        );
    }
}
