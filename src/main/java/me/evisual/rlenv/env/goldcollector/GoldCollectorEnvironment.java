package me.evisual.rlenv.env.goldcollector;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;
import me.evisual.rlenv.env.RLEnvironment;
import me.evisual.rlenv.env.StepResult;
import me.evisual.rlenv.util.BlockUtil;
import me.evisual.rlenv.util.LocationUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/**
 * A simple gridworld-like RL environment implemented inside a Minecraft world.
 *
 * Arena:
 *   - Rectangular region [minX, maxX] × [minZ, maxZ] at a fixed Y level.
 *   - One goal tile (internally tracked; optionally visualized as a GOLD_BLOCK).
 *   - Hazard tiles are blocks like LAVA / MAGMA within the arena.
 *
 * State:
 *   - Agent position (x, z) within the arena.
 *   - Goal position (x, z) within the arena.
 *
 * Reward:
 *   - +10.0 on reaching the goal.
 *   - -10.0 on stepping into a hazard.
 *   - -0.01 per step otherwise (small step cost).
 */
public class GoldCollectorEnvironment implements RLEnvironment
{
    private enum Direction {
        NORTH, SOUTH, EAST, WEST
    }

    private final ArenaConfig config;
    private final World world;
    private final int arenaWidth;
    private final int arenaLength;
    private final Random random = new Random();

    private final Set<Material> hazardMaterials =
            EnumSet.of(Material.LAVA, Material.MAGMA_BLOCK);

    private final Material goalMarkerMaterial = Material.GOLD_BLOCK;

    private int agentX;
    private int agentZ;

    private int goalX;
    private int goalZ;

    private int lastGoalX;
    private int lastGoalZ;
    private Material lastGoalOriginalType;
    private boolean hasPlacedGoalBefore = false;

    private int steps;
    private boolean done;

    public GoldCollectorEnvironment(ArenaConfig config) {
        this.config = config;
        this.world = config.world();
        this.arenaWidth = (config.maxX() - config.minX()) + 1;
        this.arenaLength = (config.maxZ() - config.minZ()) + 1;
    }

    @Override
    public Observation reset() {
        done = false;
        steps = 0;

        int[] agentPos = sampleRandomWalkableTile();
        agentX = agentPos[0];
        agentZ = agentPos[1];

        int[] goalPos = sampleRandomGoalTileDifferentFrom(agentX, agentZ);
        setGoal(goalPos[0], goalPos[1]);

        return buildObservation();
    }

    @Override
    public StepResult step(Action action) {
        if (done) {
            return new StepResult(buildObservation(), 0.0, true);
        }

        int newX = agentX;
        int newZ = agentZ;

        switch (action) {
            case MOVE_NORTH -> newZ -= 1;
            case MOVE_SOUTH -> newZ += 1;
            case MOVE_EAST -> newX += 1;
            case MOVE_WEST -> newX -= 1;
            case STAY -> {}
            default -> {}
        }

        newX = LocationUtil.clamp(newX, config.minX(), config.maxX());
        newZ = LocationUtil.clamp(newZ, config.minZ(), config.maxZ());

        agentX = newX;
        agentZ = newZ;

        double reward;
        boolean terminal = false;

        if (isGoalTile(agentX, agentZ)) {
            reward = 10.0;
            terminal = true;
        } else if (isHazardTile(agentX, agentZ)) {
            reward = -10.0;
            terminal = true;
        } else {
            reward = -0.01;
        }

        steps++;
        if (steps >= config.maxStepsPerEpisode()) {
            terminal = true;
        }

        done = terminal;
        Observation obs = buildObservation();
        return new StepResult(obs, reward, terminal);
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
        double agentNormX = (agentX - config.minX()) / (double) (arenaWidth - 1);
        double agentNormZ = (agentZ - config.minZ()) / (double) (arenaLength - 1);

        double goalRelX = (goalX - agentX) / (double) arenaWidth;
        double goalRelZ = (goalZ - agentZ) / (double) arenaLength;

        double[] features = new double[] {
                agentNormX,
                agentNormZ,
                goalRelX,
                goalRelZ,
                isBlocked(Direction.NORTH) ? 1.0 : 0.0,
                isBlocked(Direction.SOUTH) ? 1.0 : 0.0,
                isBlocked(Direction.EAST)  ? 1.0 : 0.0,
                isBlocked(Direction.WEST)  ? 1.0 : 0.0,
                isHazard(Direction.NORTH)  ? 1.0 : 0.0,
                isHazard(Direction.SOUTH)  ? 1.0 : 0.0,
                isHazard(Direction.EAST)   ? 1.0 : 0.0,
                isHazard(Direction.WEST)   ? 1.0 : 0.0
        };

        return new Observation(features);
    }

    private boolean isGoalTile(int x, int z) {
        return x == goalX && z == goalZ;
    }

    private boolean isHazardTile(int x, int z) {
        return BlockUtil.isHazard(world, x, config.y(), z, hazardMaterials);
    }

    private boolean isBlocked(Direction dir) {
        int[] next = nextCoords(agentX, agentZ, dir);
        int x = next[0];
        int z = next[1];
        return !LocationUtil.isWithinBounds(x, z,
                config.minX(), config.maxX(),
                config.minZ(), config.maxZ());
    }

    private boolean isHazard(Direction dir) {
        int[] next = nextCoords(agentX, agentZ, dir);
        int x = LocationUtil.clamp(next[0], config.minX(), config.maxX());
        int z = LocationUtil.clamp(next[1], config.minZ(), config.maxZ());
        return isHazardTile(x, z);
    }

    private int[] nextCoords(int x, int z, Direction dir) {
        return switch (dir) {
            case NORTH -> new int[] { x, z - 1 };
            case SOUTH -> new int[] { x, z + 1 };
            case EAST  -> new int[] { x + 1, z };
            case WEST  -> new int[] { x - 1, z };
        };
    }

    private int[] sampleRandomWalkableTile() {
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = config.minX() + random.nextInt(arenaWidth);
            int z = config.minZ() + random.nextInt(arenaLength);
            if (!isHazardTile(x, z)) {
                return new int[] { x, z };
            }
        }

        for (int x = config.minX(); x <= config.maxX(); x++) {
            for (int z = config.minZ(); z <= config.maxZ(); z++) {
                if (!isHazardTile(x, z)) {
                    return new int[] { x, z };
                }
            }
        }

        throw new IllegalStateException("No walkable tiles available in the arena");
    }

    private int[] sampleRandomGoalTileDifferentFrom(int avoidX, int avoidZ) {
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = config.minX() + random.nextInt(arenaWidth);
            int z = config.minZ() + random.nextInt(arenaLength);
            if ((x != avoidX || z != avoidZ) && !isHazardTile(x, z)) {
                return new int[] { x, z };
            }
        }

        for (int x = config.minX(); x <= config.maxX(); x++) {
            for (int z = config.minZ(); z <= config.maxZ(); z++) {
                if ((x != avoidX || z != avoidZ) && !isHazardTile(x, z)) {
                    return new int[] { x, z };
                }
            }
        }

        throw new IllegalStateException("No suitable goal tile available in the arena");
    }

    private void setGoal(int x, int z) {
        if (hasPlacedGoalBefore) {
            Block previous = world.getBlockAt(lastGoalX, config.y(), lastGoalZ);
            previous.setType(lastGoalOriginalType, false);
        }

        Block block = world.getBlockAt(x, config.y(), z);
        lastGoalOriginalType = block.getType();
        lastGoalX = x;
        lastGoalZ = z;
        hasPlacedGoalBefore = true;

        block.setType(goalMarkerMaterial, false);

        goalX = x;
        goalZ = z;
    }

    public World getWorld() {
        return world;
    }

    public int getAgentX() {
        return agentX;
    }

    public int getAgentZ() {
        return agentZ;
    }

    public int getGoalX() {
        return goalX;
    }

    public int getGoalZ() {
        return goalZ;
    }

    public ArenaConfig getConfig() {
        return config;
    }
}
