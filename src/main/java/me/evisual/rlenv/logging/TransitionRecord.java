package me.evisual.rlenv.logging;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;

public class TransitionRecord
{
    private final Observation state;
    private final Action action;
    private final double reward;
    private final Observation nextState;
    private final boolean done;

    public TransitionRecord(Observation state,
                            Action action,
                            double reward,
                            Observation nextState,
                            boolean done) {
        this.state = state;
        this.action = action;
        this.reward = reward;
        this.nextState = nextState;
        this.done = done;
    }

    public Observation state() {
        return state;
    }

    public Action action() {
        return action;
    }

    public double reward() {
        return reward;
    }

    public Observation nextState() {
        return nextState;
    }

    public boolean done() {
        return done;
    }
}
