package me.evisual.rlenv.control;

public record EpisodeStats(
        long episodesCompleted,
        long successCount,
        long failureCount,

        double overallSuccessRate,
        double recentSuccessRate,

        double overallAvgStepsToGoal,
        double recentAvgStepsToGoal,
        int bestStepsToGoal,

        double episodesPerMinute,

        double epsilon,     // -1 if not available
        int stateCount,     // -1 if not available

        double stepsPerSecond
) {
}