package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class QLearningPolicyTest {

    @Test
    void choosesActionWithoutThrowing() {
        QLearningPolicy policy = new QLearningPolicy();
        Observation obs = new Observation(new double[] { 1.0, 0.0, 0.0, 0.25, 0.0, 0.0, 0.0, 0.0 });

        Action action = policy.chooseAction(obs);
        assertNotNull(action);
    }

    @Test
    void observesTransitionWithoutThrowing() {
        QLearningPolicy policy = new QLearningPolicy();
        Observation s1 = new Observation(new double[] { 0.0, 1.0, 0.0, 0.5, 0.0, 0.0, 0.0, 0.0 });
        Observation s2 = new Observation(new double[] { 1.0, 0.0, 0.0, 0.4, 0.0, 0.0, 0.0, 0.0 });

        policy.observeTransition(s1, Action.MOVE_EAST, 1.0, s2, false);
        policy.onEpisodeEnd();
    }

    @Test
    void returnsStayWhenAllMovesBlocked() {
        QLearningPolicy policy = new QLearningPolicy(
                0.1, 0.9,
                0.0, 0.0, 1,
                0.0, 0.0,
                0.0,
                true,
                -1.0, 1.0
        );
        Observation blockedObs = new Observation(new double[] { 0.0, 0.0, 0.0, 0.2, 1.0, 1.0, 1.0, 1.0 });
        Action action = policy.chooseAction(blockedObs);
        assertSame(Action.STAY, action);
    }
}
