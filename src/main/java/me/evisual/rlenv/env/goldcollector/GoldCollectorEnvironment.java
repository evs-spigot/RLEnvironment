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
public class GoldCollectorEnvironment implements RLEnvironment {

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

        // Distance before moving (for shaping)
        int prevDist = manhattanDistance(agentX, agentZ, goalX, goalZ);

        int newX = agentX;
        int newZ = agentZ;

        switch (action) {
            case MOVE_NORTH -> newZ -= 1;
            case MOVE_SOUTH -> newZ += 1;
            case MOVE_EAST -> newX += 1;
            case MOVE_WEST -> newX -= 1;
            case STAY -> { }
            default -> { }
        }

        newX = LocationUtil.clamp(newX, config.minX(), config.maxX());
        newZ = LocationUtil.clamp(newZ, config.minZ(), config.maxZ());

        agentX = newX;
        agentZ = newZ;

        int newDist = manhattanDistance(agentX, agentZ, goalX, goalZ);

        boolean terminal = false;
        double reward;

        if (isGoalTile(agentX, agentZ)) {
            reward = 10.0;
            terminal = true;
        } else if (isHazardTile(agentX, agentZ)) {
            reward = -10.0;
            terminal = true;
        } else {
            // small step cost so shorter paths are preferred
            reward = -0.01;

            // ✅ reward shaping: moving closer is good, moving away is bad
            if (newDist < prevDist) reward += 0.20;
            else if (newDist > prevDist) reward -= 0.20;
        }

        steps++;
        if (steps >= config.maxStepsPerEpisode()) {
            terminal = true;
        }

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

    /**
     * New observation format (compact + directional):
     * 0: dxSign (-1,0,1)
     * 1: dzSign (-1,0,1)
     * 2: normalized manhattan distance to goal (0..~1)
     * 3..6: blocked (N,S,E,W) {0,1}
     * 7..10: hazard adjacent (N,S,E,W) {0,1}
     */
    private Observation buildObservation() {
        int dxSign = Integer.compare(goalX, agentX); // -1,0,1
        int dzSign = Integer.compare(goalZ, agentZ);

        double normDist = manhattanDistance(agentX, agentZ, goalX, goalZ)
                / (double) (arenaWidth + arenaLength);

        double[] features = new double[] {
                dxSign,
                dzSign,
                normDist,
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

    private int manhattanDistance(int x1, int z1, int x2, int z2) {
        return Math.abs(x1 - x2) + Math.abs(z1 - z2);
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

    public ArenaConfig getConfig() {
        return config;
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
}