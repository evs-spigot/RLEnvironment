package me.evisual.rlenv.env.goldcollector;

import me.evisual.rlenv.env.Action;
import me.evisual.rlenv.env.Observation;
import me.evisual.rlenv.env.RLEnvironment;
import me.evisual.rlenv.env.StepResult;
import me.evisual.rlenv.util.BlockUtil;
import me.evisual.rlenv.util.LocationUtil;
import me.evisual.rlenv.world.ArenaTerrain;
import me.evisual.rlenv.world.TerrainSnapshot;
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

    private enum Direction { NORTH, SOUTH, EAST, WEST }

    private final ArenaConfig config;
    private final World world;
    private final Random random = new Random();

    private final Material goalMarkerMaterial = Material.GOLD_BLOCK;

    private ArenaTerrain terrain;
    private TerrainSnapshot snapshot;

    private int agentX, agentY, agentZ;
    private int goalX, goalY, goalZ;

    private int lastGoalX, lastGoalY, lastGoalZ;
    private Material lastGoalOriginalType;
    private boolean hasPlacedGoalBefore = false;

    private int steps;
    private boolean done;

    public GoldCollectorEnvironment(ArenaConfig config) {
        this.config = config;
        this.world = config.world();
        this.terrain = new ArenaTerrain(config);
        this.snapshot = terrain.snapshot();
    }

    @Override
    public Observation reset() {
        done = false;
        steps = 0;

        // Build “natural” terrain inside the arena once per reset (you can change to only build on start)
        // If you prefer persistent terrain across episodes, move generate() out of reset() and into plugin start.
        terrain.generate(random.nextLong());

        int[] agentPos = sampleRandomTile();
        agentX = agentPos[0];
        agentZ = agentPos[1];
        agentY = terrain.surfaceY(agentX, agentZ);

        int[] goalPos = sampleRandomTileDifferentFrom(agentX, agentZ);
        setGoalOnSurface(goalPos[0], goalPos[1]);

        return buildObservation();
    }

    @Override
    public StepResult step(Action action) {
        if (done) return new StepResult(buildObservation(), 0.0, true);

        int prevDist = manhattan(agentX, agentZ, goalX, goalZ);

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

        // Height-based movement rule:
        // allow stepping up/down by 1; if diff > 1, treat as blocked (stay put)
        int curSurfaceY = terrain.surfaceY(agentX, agentZ);
        int nextSurfaceY = terrain.surfaceY(newX, newZ);
        int dy = nextSurfaceY - curSurfaceY;

        if (Math.abs(dy) > 1) {
            // blocked by steep slope
            newX = agentX;
            newZ = agentZ;
            nextSurfaceY = curSurfaceY;
        }

        agentX = newX;
        agentZ = newZ;
        agentY = nextSurfaceY;

        int newDist = manhattan(agentX, agentZ, goalX, goalZ);

        boolean terminal = false;
        double reward;

        if (agentX == goalX && agentZ == goalZ) {
            reward = 10.0;
            terminal = true;
        } else {
            reward = -0.01;

            // shaping (distance to goal)
            if (newDist < prevDist) reward += 0.20;
            else if (newDist > prevDist) reward -= 0.20;

            // small penalty for changing height (encourages smoother paths when equal)
            reward -= 0.01 * Math.abs(dy);
        }

        steps++;
        if (steps >= config.maxStepsPerEpisode()) terminal = true;

        done = terminal;
        return new StepResult(buildObservation(), reward, terminal);
    }

    @Override public boolean isDone() { return done; }
    @Override public Observation getObservation() { return buildObservation(); }

    /**
     * Observation:
     * 0 dxSign (-1,0,1)
     * 1 dzSign (-1,0,1)
     * 2 dySign (-1,0,1)  (surface y compare)
     * 3 normManhattanDist (0..~1)
     * 4..7 blocked N,S,E,W (0/1) by boundary or steep slope (>1 height diff)
     */
    private Observation buildObservation() {
        int dxSign = Integer.compare(goalX, agentX);
        int dzSign = Integer.compare(goalZ, agentZ);
        int dySign = Integer.compare(goalY, agentY);

        double normDist = manhattan(agentX, agentZ, goalX, goalZ)
                / (double) ((config.maxX() - config.minX() + 1) + (config.maxZ() - config.minZ() + 1));

        double[] f = new double[] {
                dxSign,
                dzSign,
                dySign,
                normDist,
                isBlocked(Direction.NORTH) ? 1.0 : 0.0,
                isBlocked(Direction.SOUTH) ? 1.0 : 0.0,
                isBlocked(Direction.EAST)  ? 1.0 : 0.0,
                isBlocked(Direction.WEST)  ? 1.0 : 0.0
        };

        return new Observation(f);
    }

    private boolean isBlocked(Direction dir) {
        int nx = agentX;
        int nz = agentZ;

        switch (dir) {
            case NORTH -> nz -= 1;
            case SOUTH -> nz += 1;
            case EAST -> nx += 1;
            case WEST -> nx -= 1;
        }

        if (!LocationUtil.isWithinBounds(nx, nz, config.minX(), config.maxX(), config.minZ(), config.maxZ())) {
            return true;
        }

        int curY = terrain.surfaceY(agentX, agentZ);
        int nextY = terrain.surfaceY(nx, nz);
        return Math.abs(nextY - curY) > 1;
    }

    private int manhattan(int x1, int z1, int x2, int z2) {
        return Math.abs(x1 - x2) + Math.abs(z1 - z2);
    }

    private int[] sampleRandomTile() {
        int x = config.minX() + random.nextInt((config.maxX() - config.minX()) + 1);
        int z = config.minZ() + random.nextInt((config.maxZ() - config.minZ()) + 1);
        return new int[] { x, z };
    }

    private int[] sampleRandomTileDifferentFrom(int avoidX, int avoidZ) {
        for (int attempt = 0; attempt < 64; attempt++) {
            int[] p = sampleRandomTile();
            if (p[0] != avoidX || p[1] != avoidZ) return p;
        }
        // fallback
        int x = (avoidX == config.minX()) ? config.maxX() : config.minX();
        int z = (avoidZ == config.minZ()) ? config.maxZ() : config.minZ();
        return new int[] { x, z };
    }

    private void setGoalOnSurface(int x, int z) {
        int y = terrain.surfaceY(x, z);

        // restore old goal block
        if (hasPlacedGoalBefore) {
            world.getBlockAt(lastGoalX, lastGoalY, lastGoalZ).setType(lastGoalOriginalType, false);

            // also clear any old "cover" if it still exists (safe)
            world.getBlockAt(lastGoalX, lastGoalY + 1, lastGoalZ).setType(Material.AIR, false);
        }

        // place gold marker on the surface block (one below standing Y)
        Block goalBlock = world.getBlockAt(x, y - 1, z);
        lastGoalOriginalType = goalBlock.getType();
        lastGoalX = x;
        lastGoalY = y - 1;
        lastGoalZ = z;
        hasPlacedGoalBefore = true;

        goalBlock.setType(goalMarkerMaterial, false);

        goalX = x;
        goalZ = z;
        goalY = y;

        // ✅ NEW: Cover the goal sometimes so the agent has to break in
        // (puts a solid block where the zombie wants to stand)
        if (random.nextDouble() < 0.65) {
            Block cover = world.getBlockAt(x, y, z); // standing space
            if (cover.getType() == Material.AIR || cover.isPassable()) {
                cover.setType(Material.DIRT, false); // breakable "cap"
            }
        }

        // ✅ NEW: Add a small 1-block “wall” around goal sometimes (forces jump/break)
        if (random.nextDouble() < 0.35) {
            placeGoalRing(x, y, z);
        }
    }

    private void placeGoalRing(int x, int y, int z) {
        // 4-neighbor ring at standing height (y)
        placeIfAirOrPassable(x + 1, y, z, Material.COBBLESTONE);
        placeIfAirOrPassable(x - 1, y, z, Material.COBBLESTONE);
        placeIfAirOrPassable(x, y, z + 1, Material.COBBLESTONE);
        placeIfAirOrPassable(x, y, z - 1, Material.COBBLESTONE);
    }

    private void placeIfAirOrPassable(int x, int y, int z, Material m) {
        if (!LocationUtil.isWithinBounds(x, z, config.minX(), config.maxX(), config.minZ(), config.maxZ())) {
            return;
        }
        Block b = world.getBlockAt(x, y, z);
        if (b.getType() == Material.AIR || b.isPassable()) {
            b.setType(m, false);
        }
    }

    public ArenaConfig getConfig() { return config; }
    public TerrainSnapshot getTerrainSnapshot() { return snapshot; }

    public int getAgentX() { return agentX; }
    public int getAgentY() { return agentY; }
    public int getAgentZ() { return agentZ; }

    public int getGoalX() { return goalX; }
    public int getGoalY() { return goalY; }
    public int getGoalZ() { return goalZ; }
}