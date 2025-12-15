package me.evisual.rlenv.control;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;

public interface Policy
{
    Action chooseAction(Observation observation);

    default void onEpisodeEnd()
    {}
}
