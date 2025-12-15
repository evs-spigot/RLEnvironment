package me.evisual.rlenv.util;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Set;

public final class BlockUtil
{
    private BlockUtil() {
    }

    public static Block getBlock(World world, int x, int y, int z) {
        return world.getBlockAt(x, y, z);
    }

    public static boolean isMaterialIn(World world, int x, int y, int z, Set<Material> materials) {
        return materials.contains(world.getBlockAt(x, y, z).getType());
    }

    public static boolean isHazard(World world, int x, int y, int z, Set<Material> hazardMaterials) {
        return isMaterialIn(world, x, y, z, hazardMaterials);
    }

    public static boolean isWalkable(World world, int x, int y, int z, Set<Material> nonWalkable) {
        Material type = world.getBlockAt(x, y, z).getType();
        return !nonWalkable.contains(type);
    }
}
