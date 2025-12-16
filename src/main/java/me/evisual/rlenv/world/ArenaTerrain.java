package me.evisual.rlenv.world;

import me.evisual.rlenv.env.goldcollector.ArenaConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;

public class ArenaTerrain
{
    private final ArenaConfig config;
    private final TerrainSnapshot snapshot;
    private final int[] height; // per (x,z) tile height offset 0..2
    private final int width;
    private final int length;

    public ArenaTerrain(ArenaConfig config) {
        this.config = config;
        this.snapshot = new TerrainSnapshot();
        this.width = (config.maxX() - config.minX()) + 1;
        this.length = (config.maxZ() - config.minZ()) + 1;
        this.height = new int[width * length];
    }

    public TerrainSnapshot snapshot() {
        return snapshot;
    }

    public void generate(long seed) {
        Random r = new Random(seed);
        // 1) noise-ish heights 0..2
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                int h = r.nextInt(3); // 0..2
                height[idx(x, z)] = h;
            }
        }
        // 2) cheap smoothing (2 passes) to look more natural
        smoothOnce();
        smoothOnce();

        // 3) build terrain columns in-world
        World world = config.world();
        int baseY = config.y();

        for (int x = config.minX(); x <= config.maxX(); x++) {
            for (int z = config.minZ(); z <= config.maxZ(); z++) {
                int localX = x - config.minX();
                int localZ = z - config.minZ();
                int h = height[idx(localX, localZ)];

                // clear 0..2 above base so we can rebuild cleanly
                for (int dy = 0; dy <= 3; dy++) {
                    Block b = world.getBlockAt(x, baseY + dy, z);
                    snapshot.capture(b);
                    b.setType(Material.AIR, false);
                }

                // build up to h
                for (int dy = 0; dy <= h; dy++) {
                    Block b = world.getBlockAt(x, baseY + dy, z);
                    snapshot.capture(b);
                    b.setType(dy == h ? Material.GRASS_BLOCK : Material.DIRT, false);
                }

                // sprinkle some “natural” variation
                if (r.nextDouble() < 0.08) {
                    Block b = world.getBlockAt(x, baseY + h + 1, z);
                    snapshot.capture(b);
                    b.setType(Material.OAK_LEAVES, false);
                } else if (r.nextDouble() < 0.05) {
                    Block b = world.getBlockAt(x, baseY + h + 1, z);
                    snapshot.capture(b);
                    b.setType(Material.TALL_GRASS, false);
                }
            }
        }
    }

    public int surfaceY(int x, int z) {
        // return the top block Y + 1 (standing position)
        int localX = x - config.minX();
        int localZ = z - config.minZ();
        int h = height[idx(localX, localZ)];
        return config.y() + h + 1;
    }

    private void smoothOnce() {
        int[] copy = height.clone();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                int sum = copy[idx(x, z)];
                int count = 1;

                if (x > 0) { sum += copy[idx(x - 1, z)]; count++; }
                if (x < width - 1) { sum += copy[idx(x + 1, z)]; count++; }
                if (z > 0) { sum += copy[idx(x, z - 1)]; count++; }
                if (z < length - 1) { sum += copy[idx(x, z + 1)]; count++; }

                int avg = (int) Math.round(sum / (double) count);
                height[idx(x, z)] = Math.max(0, Math.min(2, avg));
            }
        }
    }

    private int idx(int localX, int localZ) {
        return localX * length + localZ;
    }
}