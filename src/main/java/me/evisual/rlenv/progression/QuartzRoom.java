package me.evisual.rlenv.progression;

import me.evisual.rlenv.env.goldcollector.ArenaConfig;
import me.evisual.rlenv.world.TerrainSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class QuartzRoom {

    public static final int ROOM_HEIGHT = 10;

    private final ArenaConfig arena;
    private final TerrainSnapshot snapshot = new TerrainSnapshot();

    public QuartzRoom(ArenaConfig arena) {
        this.arena = arena;
    }

    public TerrainSnapshot snapshot() {
        return snapshot;
    }

    /**
     * Builds a room around the arena bounds.
     * Floor/ceiling/walls are QUARTZ_BLOCK except one wall of BARRIER.
     */
    public void build(BlockFace barrierSide) {
        World w = arena.world();
        int minX = arena.minX();
        int maxX = arena.maxX();
        int minZ = arena.minZ();
        int maxZ = arena.maxZ();
        int baseY = arena.y();
        int height = ROOM_HEIGHT;

        int y0 = baseY;
        int y1 = baseY + height - 1;

        // Build one block outside the arena so the interior matches arena bounds.
        int wallMinX = minX - 1;
        int wallMaxX = maxX + 1;
        int wallMinZ = minZ - 1;
        int wallMaxZ = maxZ + 1;

        // Clear interior (air volume) so the agent can move freely.
        for (int x = wallMinX; x <= wallMaxX; x++) {
            for (int z = wallMinZ; z <= wallMaxZ; z++) {
                for (int y = y0; y <= y1; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    snapshot.capture(b);
                    b.setType(Material.AIR, false);
                }
            }
        }

        // Floor + ceiling
        for (int x = wallMinX; x <= wallMaxX; x++) {
            for (int z = wallMinZ; z <= wallMaxZ; z++) {
                set(w, x, y0, z, Material.QUARTZ_BLOCK);
                set(w, x, y1, z, Material.QUARTZ_BLOCK);
            }
        }

        // Walls
        for (int y = y0 + 1; y <= y1 - 1; y++) {
            for (int x = wallMinX; x <= wallMaxX; x++) {
                Material north = barrierSide == BlockFace.NORTH ? Material.BARRIER : Material.QUARTZ_BLOCK;
                Material south = barrierSide == BlockFace.SOUTH ? Material.BARRIER : Material.QUARTZ_BLOCK;
                set(w, x, y, wallMinZ, north);
                set(w, x, y, wallMaxZ, south);
            }
            for (int z = wallMinZ; z <= wallMaxZ; z++) {
                Material west = barrierSide == BlockFace.WEST ? Material.BARRIER : Material.QUARTZ_BLOCK;
                Material east = barrierSide == BlockFace.EAST ? Material.BARRIER : Material.QUARTZ_BLOCK;
                set(w, wallMinX, y, z, west);
                set(w, wallMaxX, y, z, east);
            }
        }
    }


    private void set(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        snapshot.capture(b);
        b.setType(m, false);
    }
}
