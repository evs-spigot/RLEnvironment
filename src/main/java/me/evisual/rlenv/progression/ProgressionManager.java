package me.evisual.rlenv.progression;

import me.evisual.rlenv.RLEnvPlugin;
import me.evisual.rlenv.env.RLEnvironment;
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
        plugin.startEnvironmentWithCustomEnv(player, makeLevelEnvironment(levelIndex));
    }

    public void next(Player player) {
        if (!isRunning()) {
            start(player);
            return;
        }

        levelIndex++;
        if (levelIndex > 1) {
            levelIndex = 1; // only 2 levels for now
        }

        plugin.startEnvironmentWithCustomEnv(player, makeLevelEnvironment(levelIndex));
    }

    public void stop() {
        plugin.stopEnvironment();

        if (roomSnapshot != null && arenaConfig != null) {
            roomSnapshot.restore(arenaConfig.world());
        }

        room = null;
        roomSnapshot = null;
        arenaConfig = null;
        levelIndex = -1;
    }

    public int getLevelIndex() {
        return levelIndex;
    }

    private RLEnvironment makeLevelEnvironment(int idx) {
        if (idx == 0) {
            // Level 1: fixed goal
            return new ProgressionGoldEnvironment(arenaConfig, spawnX, spawnZ, false, goalX, goalZ);
        }
        // Level 2: randomized goal
        return new ProgressionGoldEnvironment(arenaConfig, spawnX, spawnZ, true, goalX, goalZ);
    }
}