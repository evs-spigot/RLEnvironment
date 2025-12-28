package me.evisual.rlenv.testing;

import me.evisual.rlenv.control.QLearningPolicy;
import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;
import me.evisual.rlenv.env.StepResult;

public final class StartupSelfTest {

    private StartupSelfTest() {
    }

    public static void run() {
        QLearningPolicy policy = new QLearningPolicy();
        Observation s1 = new Observation(new double[] { 1.0, 0.0, 0.0, 0.1, 0.0, 0.0, 0.0, 0.0 });
        Observation s2 = new Observation(new double[] { 0.0, 1.0, 0.0, 0.2, 0.0, 0.0, 0.0, 0.0 });

        Action action = policy.chooseAction(s1);
        policy.observeTransition(s1, action, 1.0, s2, false);
        policy.onEpisodeEnd();

        StepResult result = new StepResult(s2, 1.0, true);
        if (result.getObservation() == null) {
            throw new IllegalStateException("StepResult observation missing");
        }
    }
}
