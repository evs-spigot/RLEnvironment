package me.evisual.rlenv.util;

import org.bukkit.Location;
import org.bukkit.World;

public final class LocationUtil
{
    private LocationUtil() {
    }

    public static Location centerOfBlock(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    public static boolean isWithinBounds(int x, int z,
                                         int minX, int maxX,
                                         int minZ, int maxZ) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
