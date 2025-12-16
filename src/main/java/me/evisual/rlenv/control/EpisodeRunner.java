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
    private long successCount = 0;
    private long failureCount = 0;
    private double totalRewardSum = 0.0;

    private double currentEpisodeReward = 0.0;
    private int stepsThisEpisode = 0;

    // Moving average window (for "learning curve")
    private final double[] rewardWindow = new double[50];
    private int rewardWindowSize = 0;
    private int rewardWindowIndex = 0;
    private double rewardWindowSum = 0.0;

    // Graph sampling: push one point every N episodes (helps performance at high speed)
    private final int graphSampleEveryEpisodes = 5;

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

        if (currentObservation == null) {
            currentObservation = environment.getObservation();
        }

        // Convert steps/sec into steps per tick using an accumulator
        stepAccumulator += (stepsPerSecond / 20.0);

        int stepsToRun = (int) Math.floor(stepAccumulator);
        if (stepsToRun <= 0) {
            // Still update visuals even if we didn't step
            updateVisualizer();
            return;
        }

        // Consume accumulator
        stepAccumulator -= stepsToRun;

        // Cap steps for safety
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
            updateVisualizer();

            if (result.isDone()) {
                finishEpisode(result);
                policy.onEpisodeEnd();

                currentObservation = environment.reset();
                currentEpisodeReward = 0.0;
                stepsThisEpisode = 0;
                updateVisualizer();
                break;
            }
        }
    }

    private void finishEpisode(StepResult lastStep) {
        episodesCompleted++;
        totalRewardSum += currentEpisodeReward;

        if (lastStep.getReward() > 0) successCount++;
        else failureCount++;

        // sliding window
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

        double movingAvg = rewardWindowSum / Math.max(1, rewardWindowSize);

        if (graph != null && (episodesCompleted % graphSampleEveryEpisodes == 0)) {
            graph.addAvgRewardPoint(movingAvg);
        }
    }

    private void updateVisualizer() {
        if (visualizer == null) return;

        if (environment instanceof GoldCollectorEnvironment env) {
            visualizer.updatePosition(env.getAgentX(), env.getAgentY(), env.getAgentZ());
        }
    }

    public void shutdown() {
        closed = true;
        cancel();
        logger.close();
        if (visualizer != null) visualizer.destroy();
    }

    public void setStepsPerSecond(double stepsPerSecond) {
        // allow very slow, and very fast
        if (stepsPerSecond < 0.1) stepsPerSecond = 0.1;   // 1 step every 10 seconds
        if (stepsPerSecond > 2000) stepsPerSecond = 2000; // safety
        this.stepsPerSecond = stepsPerSecond;
    }

    public double getStepsPerSecond() {
        return stepsPerSecond;
    }

    public EpisodeStats snapshotStats() {
        double avgReward = episodesCompleted > 0 ? totalRewardSum / episodesCompleted : 0.0;
        return new EpisodeStats(
                episodesCompleted,
                successCount,
                failureCount,
                avgReward,
                (int) Math.round(stepsPerSecond)
        );
    }
}