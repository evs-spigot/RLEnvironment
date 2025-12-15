package me.evisual.rlenv.env.goldcollector;

import org.bukkit.World;

public class ArenaConfig
{
    private final World world;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int y;
    private final int maxStepsPerEpisode;

    public ArenaConfig(World world,
                       int minX, int maxX,
                       int minZ, int maxZ,
                       int y,
                       int maxStepsPerEpisode) {
        this.world = world;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.y = y;
        this.maxStepsPerEpisode = maxStepsPerEpisode;
    }

    public World world() {
        return world;
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public int y() {
        return y;
    }

    public int maxStepsPerEpisode() {
        return maxStepsPerEpisode;
    }
}
