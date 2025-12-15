package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;

public interface Policy
{
    Action chooseAction(Observation observation);

    /**
     * Called after each environment step so the policy can learn.
     */
    default void observeTransition(Observation state,
                                   Action action,
                                   double reward,
                                   Observation nextState,
                                   boolean done) {
    }

    default void onEpisodeEnd() {
    }
}
