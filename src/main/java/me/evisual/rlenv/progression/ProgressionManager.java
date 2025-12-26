package me.evisual.rlenv.progression;

import me.evisual.rlenv.RLEnvPlugin;
import me.evisual.rlenv.control.Policy;
import me.evisual.rlenv.control.QLearningPolicy;
import me.evisual.rlenv.env.goldcollector.ArenaConfig;
import me.evisual.rlenv.env.goldcollector.ProgressionGoldEnvironment;
import me.evisual.rlenv.world.TerrainSnapshot;
import org.bukkit.entity.Player;

public class ProgressionManager {

    private final RLEnvPlugin plugin;

    private QuartzRoom room;
    private TerrainSnapshot roomSnapshot;

    private int levelIndex = -1;
    private ArenaConfig arenaConfig;
    private ProgressionGoldEnvironment currentEnv;
    private Policy policy;
    private double level2MinEpsilon = 0.50;
    private int level2BoostEpisodes = 25;

    // Fixed spawn/goal choices (inside arena)
    private int spawnX, spawnZ;
    private int goalX, goalZ;

    public ProgressionManager(RLEnvPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return levelIndex >= 0;
    }

    public void start(Player player) {
        stop(); // clean slate

        // Build arena around player (reuse your existing arena sizing logic)
        arenaConfig = plugin.createArenaConfigNearPlayer(player);

        // Pick a nice fixed spawn & goal inside the arena
        spawnX = arenaConfig.minX() + 2;
        spawnZ = arenaConfig.minZ() + 2;
        goalX = arenaConfig.maxX() - 2;
        goalZ = arenaConfig.maxZ() - 2;

        room = new QuartzRoom(arenaConfig);
        room.build();
        roomSnapshot = room.snapshot();

        levelIndex = 0;
        policy = new QLearningPolicy();
        currentEnv = makeLevelEnvironment(levelIndex);
        plugin.startEnvironmentWithCustomEnv(player, currentEnv, policy);
    }

    public void next(Player player) {
        if (!isRunning()) {
            start(player);
            return;
        }

        if (currentEnv != null) {
            currentEnv.cleanupGoal();
        }

        levelIndex++;
        if (levelIndex > 1) {
            levelIndex = 1; // only 2 levels for now
        }

        currentEnv = makeLevelEnvironment(levelIndex);
        if (policy instanceof QLearningPolicy qlp) {
            qlp.boostEpsilonToAtLeast(level2MinEpsilon, level2BoostEpisodes);
        }
        plugin.startEnvironmentWithCustomEnv(player, currentEnv, policy);
    }

    public void stop() {
        plugin.stopEnvironment();

        if (currentEnv != null) {
            currentEnv.cleanupGoal();
        }

        if (roomSnapshot != null && arenaConfig != null) {
            roomSnapshot.restore(arenaConfig.world());
        }

        room = null;
        roomSnapshot = null;
        arenaConfig = null;
        levelIndex = -1;
        currentEnv = null;
        policy = null;
    }

    public int getLevelIndex() {
        return levelIndex;
    }

    private ProgressionGoldEnvironment makeLevelEnvironment(int idx) {
        if (idx == 0) {
            // Level 1: fixed goal
            return new ProgressionGoldEnvironment(arenaConfig, spawnX, spawnZ, false, goalX, goalZ);
        }
        // Level 2: randomized goal
        return new ProgressionGoldEnvironment(arenaConfig, spawnX, spawnZ, true, goalX, goalZ);
    }
}
