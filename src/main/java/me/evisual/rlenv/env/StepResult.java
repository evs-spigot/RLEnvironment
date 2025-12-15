package me.evisual.rlenv.env;

public final class StepResult
{
    private final Observation observation;
    private final double reward;
    private final boolean done;

    public StepResult(Observation observation, double reward, boolean done) {
        this.observation = observation;
        this.reward = reward;
        this.done = done;
    }

    public Observation getObservation() { return observation; }
    public double getReward() { return reward; }
    public boolean isDone() { return done; }
}