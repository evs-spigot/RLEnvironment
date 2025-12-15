package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;

import java.util.Random;

public class RandomPolicy implements Policy
{
    private final Random random = new Random();

    @Override
    public Action chooseAction(Observation observation) {
        Action[] actions = Action.values();
        return actions[random.nextInt(actions.length)];
    }

    // observeTransition and onEpisodeEnd use default no-op implementations
}
