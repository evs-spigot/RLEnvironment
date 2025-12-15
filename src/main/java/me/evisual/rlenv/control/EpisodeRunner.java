package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;
import me.evisual.rlenv.env.RLEnvironment;
import me.evisual.rlenv.env.StepResult;
import me.evisual.rlenv.env.goldcollector.GoldCollectorEnvironment;
import me.evisual.rlenv.logging.TransitionLogger;
import me.evisual.rlenv.visual.AgentVisualizer;
import org.bukkit.scheduler.BukkitRunnable;

public class EpisodeRunner extends BukkitRunnable {

    private final RLEnvironment environment;
    private final TransitionLogger logger;
    private final Policy policy;
    private final AgentVisualizer visualizer;

    private Observation currentObservation;
    private boolean closed = false;

    private int stepsPerTick = 1;

    private long episodesCompleted = 0;
    private long successCount = 0;
    private long failureCount = 0;
    private double totalRewardSum = 0.0;

    private double currentEpisodeReward = 0.0;
    private int stepsThisEpisode = 0;

    public EpisodeRunner(RLEnvironment environment,
                         TransitionLogger logger,
                         Policy policy,
                         AgentVisualizer visualizer) {
        this.environment = environment;
        this.logger = logger;
        this.policy = policy;
        this.visualizer = visualizer;

        this.currentObservation = environment.reset();
        this.currentEpisodeReward = 0.0;
        this.stepsThisEpisode = 0;
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

        int maxSteps = Math.max(1, stepsPerTick);

        for (int i = 0; i < maxSteps; i++) {
            if (closed) {
                break;
            }

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

            // NEW: let the policy learn from this transition
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

        if (lastStep.getReward() > 0) {
            successCount++;
        } else {
            failureCount++;
        }
    }

    private void updateVisualizer() {
        if (visualizer == null) {
            return;
        }
        if (environment instanceof GoldCollectorEnvironment env) {
            visualizer.updatePosition(env.getAgentX(), env.getAgentZ());
        }
    }

    public void shutdown() {
        closed = true;
        cancel();
        logger.close();
        if (visualizer != null) {
            visualizer.destroy();
        }
    }

    public void setStepsPerTick(int stepsPerTick) {
        if (stepsPerTick < 1) {
            this.stepsPerTick = 1;
        } else if (stepsPerTick > 50) {
            this.stepsPerTick = 50;
        } else {
            this.stepsPerTick = stepsPerTick;
        }
    }

    public EpisodeStats snapshotStats() {
        double avgReward = episodesCompleted > 0
                ? totalRewardSum / episodesCompleted
                : 0.0;
        return new EpisodeStats(
                episodesCompleted,
                successCount,
                failureCount,
                avgReward,
                stepsPerTick
        );
    }
}