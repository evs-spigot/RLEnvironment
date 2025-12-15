package me.evisual.rlenv.control;

public record EpisodeStats(
        long episodesCompleted,
        long successCount,
        long failureCount,
        double averageReward,
        int stepsPerTick
) {
}