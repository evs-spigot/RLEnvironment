package me.evisual.rlenv;

import me.evisual.rlenv.command.RLEnvCommand;
import me.evisual.rlenv.control.*;
import me.evisual.rlenv.env.RLEnvironment;
import me.evisual.rlenv.env.goldcollector.ArenaConfig;
import me.evisual.rlenv.env.goldcollector.GoldCollectorEnvironment;
import me.evisual.rlenv.logging.TransitionLogger;
import me.evisual.rlenv.visual.AgentVisualizer;
import me.evisual.rlenv.visual.ArenaVisualizer;
import me.evisual.rlenv.visual.GraphMode;
import me.evisual.rlenv.visual.ProgressGraphVisualizer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class RLEnvPlugin extends JavaPlugin {

    private EpisodeRunner episodeRunner;
    private TransitionLogger transitionLogger;
    private RLEnvironment environment;
    private ProgressGraphVisualizer graphVisualizer;
    private boolean graphEnabled = true;

    @Override
    public void onEnable() {
        if (getCommand("rlenv") != null) {
            getCommand("rlenv").setExecutor(new RLEnvCommand(this));
        }
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        getLogger().info("RLEnvPlugin enabled");
    }

    @Override
    public void onDisable() {
        stopEnvironment();
        getLogger().info("RLEnvPlugin disabled");
    }

    public boolean isEnvironmentRunning() {
        return episodeRunner != null;
    }

    public void startEnvironment(Player player) {
        if (isEnvironmentRunning()) {
            return;
        }

        World world = player.getWorld();

        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();
        int y = player.getLocation().getBlockY() + 1; // spawn agent on top of ground

        int halfSize = 5;
        int minX = centerX - halfSize;
        int maxX = centerX + halfSize;
        int minZ = centerZ - halfSize;
        int maxZ = centerZ + halfSize;

        int maxStepsPerEpisode = 200;

        ArenaConfig arenaConfig = new ArenaConfig(
                world,
                minX, maxX,
                minZ, maxZ,
                y,
                maxStepsPerEpisode
        );

        environment = new GoldCollectorEnvironment(arenaConfig);

        File dataFolder = getDataFolder();
        transitionLogger = new TransitionLogger(dataFolder);

        Policy policy = new QLearningPolicy(); // or RandomPolicy if you prefer
        AgentVisualizer visualizer = new AgentVisualizer(world, arenaConfig.y());
        Location graphOrigin = new Location(
                arenaConfig.world(),
                arenaConfig.maxX() + 2.0,
                arenaConfig.y() + 2.0,
                arenaConfig.minZ() + 0.0
        );

        graphVisualizer = new ProgressGraphVisualizer(player, graphOrigin);
        graphVisualizer.runTaskTimer(this, 0L, 10L); // redraw graph every 40 ticks

        episodeRunner = new EpisodeRunner(
                environment,
                transitionLogger,
                policy,
                visualizer,
                graphVisualizer
        );
        episodeRunner.runTaskTimer(this, 0L, 1L);

        getLogger().info("RL environment started at arena centered on " + player.getName());
    }

    public void stopEnvironment() {
        if (episodeRunner != null) {
            episodeRunner.shutdown();
            episodeRunner = null;
        }

        if (transitionLogger != null) {
            transitionLogger.close();
            transitionLogger = null;
        }

        if (graphVisualizer != null) {
            graphVisualizer.cancel();
            graphVisualizer = null;
        }

        environment = null;
    }

    public boolean setEnvironmentSpeed(int stepsPerTick) {
        if (episodeRunner == null) {
            return false;
        }
        episodeRunner.setStepsPerTick(stepsPerTick);
        return true;
    }

    public EpisodeStats getEpisodeStats() {
        if (episodeRunner == null) {
            return null;
        }
        return episodeRunner.snapshotStats();
    }

    public void showArena(Player player) {
        if (!(environment instanceof GoldCollectorEnvironment env)) {
            player.sendMessage("No GoldCollector environment is active.");
            return;
        }
        ArenaConfig config = env.getConfig();
        ArenaVisualizer.showOutline(player, config);
        player.sendMessage("Showing arena outline.");
    }

    public boolean toggleGraphFor(Player viewer) {
        if (graphVisualizer == null) {
            return false;
        }
        boolean newState = !graphVisualizer.isEnabled();
        graphVisualizer.setEnabled(newState);
        return newState;
    }

    public boolean setGraphMode(GraphMode mode) {
        if (graphVisualizer == null) return false;
        graphVisualizer.setMode(mode);
        return true;
    }

    public GraphMode getGraphMode() {
        if (graphVisualizer == null) return null;
        return graphVisualizer.getMode();
    }
}