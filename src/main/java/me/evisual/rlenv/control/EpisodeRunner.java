package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;
import me.evisual.rlenv.env.RLEnvironment;
import me.evisual.rlenv.env.StepResult;
import me.evisual.rlenv.env.goldcollector.GoldCollectorEnvironment;
import me.evisual.rlenv.logging.TransitionLogger;
import me.evisual.rlenv.visual.AgentVisualizer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

public class EpisodeRunner extends BukkitRunnable {

    private final RLEnvironment environment;
    private final TransitionLogger logger;
    private final Policy policy;
    private final AgentVisualizer visualizer;

    private Observation currentObservation;
    private boolean closed = false;

    public EpisodeRunner(RLEnvironment environment,
                         TransitionLogger logger,
                         Policy policy,
                         AgentVisualizer visualizer) {
        this.environment = environment;
        this.logger = logger;
        this.policy = policy;
        this.visualizer = visualizer;

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

        Action action = policy.chooseAction(currentObservation);
        StepResult result = environment.step(action);

        logger.logTransition(
                currentObservation,
                action,
                result.getReward(),
                result.getObservation(),
                result.isDone()
        );

        updateVisualizer();

        if (result.isDone()) {
            policy.onEpisodeEnd();
            currentObservation = environment.reset();
            updateVisualizer();
        } else {
            currentObservation = result.getObservation();
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
}