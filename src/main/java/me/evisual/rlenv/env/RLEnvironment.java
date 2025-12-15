package me.evisual.rlenv.env;

public interface RLEnvironment
{
    Observation reset(); // start a new episode

    StepResult step(Action action); // apply action and advance environment

    boolean isDone();

    Observation getObservation();
}
