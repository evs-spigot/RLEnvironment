package me.evisual.rlenv.world;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;

public class TerrainSnapshot
{

    private final Map<String, Material> original = new HashMap<>();

    public void capture(Block b) {
        String key = key(b.getX(), b.getY(), b.getZ());
        original.putIfAbsent(key, b.getType());
    }

    public void restore(org.bukkit.World world) {
        for (Map.Entry<String, Material> e : original.entrySet()) {
            String[] parts = e.getKey().split(":");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);

            world.getBlockAt(x, y, z).setType(e.getValue(), false);
        }
        original.clear();
    }

    private String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}