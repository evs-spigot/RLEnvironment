package me.evisual.rlenv.env.goldcollector;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;
import me.evisual.rlenv.env.RLEnvironment;
import me.evisual.rlenv.env.StepResult;
import me.evisual.rlenv.util.LocationUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;

public class ProgressionGoldEnvironment implements RLEnvironment {

    private final ArenaConfig config;
    private final World world;
    private final Random random = new Random();

    private final int spawnX, spawnZ;
    private int agentX, agentZ;

    private final boolean randomGoal;
    private final int fixedGoalX, fixedGoalZ;
    private int goalX, goalZ;
    private boolean hasGoal = false;

    private boolean done = false;
    private int steps = 0;

    private final Material goalMaterial = Material.GOLD_BLOCK;
    private Material prevGoalMaterial = Material.QUARTZ_BLOCK;

    public ProgressionGoldEnvironment(ArenaConfig config,
                                      int spawnX, int spawnZ,
                                      boolean randomGoal,
                                      int fixedGoalX, int fixedGoalZ) {
        this.config = config;
        this.world = config.world();
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.randomGoal = randomGoal;
        this.fixedGoalX = fixedGoalX;
        this.fixedGoalZ = fixedGoalZ;
    }

    @Override
    public Observation reset() {
        done = false;
        steps = 0;

        // Always reset the agent to the SAME spot after every goal touch
        agentX = spawnX;
        agentZ = spawnZ;

        // Goal stays fixed in Level 1, randomizes in Level 2
        if (randomGoal) {
            int[] g = sampleGoalNotAtSpawn();
            setGoal(g[0], g[1]);
        } else {
            setGoal(fixedGoalX, fixedGoalZ);
        }

        return buildObservation();
    }

    @Override
    public StepResult step(Action action) {
        if (done) return new StepResult(buildObservation(), 0.0, true);

        int prevDist = manhattan(agentX, agentZ, goalX, goalZ);

        int nx = agentX;
        int nz = agentZ;

        switch (action) {
            case MOVE_NORTH -> nz -= 1;
            case MOVE_SOUTH -> nz += 1;
            case MOVE_EAST -> nx += 1;
            case MOVE_WEST -> nx -= 1;
            case STAY -> { }
            default -> { }
        }

        nx = LocationUtil.clamp(nx, config.minX(), config.maxX());
        nz = LocationUtil.clamp(nz, config.minZ(), config.maxZ());

        agentX = nx;
        agentZ = nz;

        int newDist = manhattan(agentX, agentZ, goalX, goalZ);

        double reward;
        boolean terminal = false;

        if (agentX == goalX && agentZ == goalZ) {
            reward = 10.0;
            terminal = true;
        } else {
            reward = -0.01; // base step cost

            // distance shaping (optional but helps early learning)
            if (newDist < prevDist) reward += 0.20;
            else if (newDist > prevDist) reward -= 0.20;
        }

        steps++;
        if (steps >= config.maxStepsPerEpisode()) terminal = true;

        done = terminal;
        return new StepResult(buildObservation(), reward, terminal);
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public Observation getObservation() {
        return buildObservation();
    }

    private Observation buildObservation() {
        int dxSign = Integer.compare(goalX, agentX);
        int dzSign = Integer.compare(goalZ, agentZ);

        double normDist = manhattan(agentX, agentZ, goalX, goalZ)
                / (double) ((config.maxX() - config.minX() + 1) + (config.maxZ() - config.minZ() + 1));

        // Compact & strong state signal
        double[] f = new double[] { dxSign, dzSign, normDist };
        return new Observation(f);
    }

    private int[] sampleGoalNotAtSpawn() {
        for (int i = 0; i < 200; i++) {
            int x = (config.minX() + 1) + random.nextInt((config.maxX() - 1) - (config.minX() + 1) + 1);
            int z = (config.minZ() + 1) + random.nextInt((config.maxZ() - 1) - (config.minZ() + 1) + 1);
            if (x == spawnX && z == spawnZ) continue;
            return new int[] { x, z };
        }
        // fallback
        return new int[] { config.maxX(), config.maxZ() };
    }

    private void setGoal(int x, int z) {
        int goalY = config.y() + 1;

        // restore previous goal block (above floor)
        if (hasGoal) {
            Block old = world.getBlockAt(goalX, goalY, goalZ);
            if (old.getType() == goalMaterial) {
                old.setType(prevGoalMaterial, false);
            }
        }

        // place new goal block (above floor)
        Block b = world.getBlockAt(x, goalY, z);
        prevGoalMaterial = b.getType();
        b.setType(goalMaterial, false);

        goalX = x;
        goalZ = z;
        hasGoal = true;
    }

    private int manhattan(int x1, int z1, int x2, int z2) {
        return Math.abs(x1 - x2) + Math.abs(z1 - z2);
    }

    public int getAgentX() { return agentX; }
    public int getAgentZ() { return agentZ; }
    public int getGoalX() { return goalX; }
    public int getGoalZ() { return goalZ; }
    public ArenaConfig getConfig() { return config; }

    public void cleanupGoal() {
        if (!hasGoal) return;
        int goalY = config.y() + 1;
        Block old = world.getBlockAt(goalX, goalY, goalZ);
        if (old.getType() == goalMaterial) {
            old.setType(prevGoalMaterial, false);
        }
        hasGoal = false;
    }
}
