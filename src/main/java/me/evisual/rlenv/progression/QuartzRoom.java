package me.evisual.rlenv.progression;

import me.evisual.rlenv.env.goldcollector.ArenaConfig;
import me.evisual.rlenv.world.TerrainSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class QuartzRoom {

    private final ArenaConfig arena;
    private final TerrainSnapshot snapshot = new TerrainSnapshot();

    public QuartzRoom(ArenaConfig arena) {
        this.arena = arena;
    }

    public TerrainSnapshot snapshot() {
        return snapshot;
    }

    /**
     * Builds a 10-block tall room around the arena bounds.
     * Floor/ceiling/walls are QUARTZ_BLOCK except one wall of GLASS.
     *
     * Glass wall -- NORTH (minZ side).
     */
    public void build() {
        World w = arena.world();
        int minX = arena.minX();
        int maxX = arena.maxX();
        int minZ = arena.minZ();
        int maxZ = arena.maxZ();
        int baseY = arena.y();
        int height = 10; // tall

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
                // NORTH wall (glass)
                set(w, x, y, wallMinZ, Material.GLASS);
                // SOUTH wall (quartz)
                set(w, x, y, wallMaxZ, Material.QUARTZ_BLOCK);
            }
            for (int z = wallMinZ; z <= wallMaxZ; z++) {
                // WEST / EAST walls (quartz)
                set(w, wallMinX, y, z, Material.QUARTZ_BLOCK);
                set(w, wallMaxX, y, z, Material.QUARTZ_BLOCK);
            }
        }
    }

    private void set(World w, int x, int y, int z, Material m) {
        Block b = w.getBlockAt(x, y, z);
        snapshot.capture(b);
        b.setType(m, false);
    }
}