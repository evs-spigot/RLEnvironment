package me.evisual.rlenv.visual;

import me.evisual.rlenv.env.goldcollector.ArenaConfig;
import me.evisual.rlenv.util.LocationUtil;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ArenaVisualizer {

    private ArenaVisualizer() {
    }

    public static void showOutline(Player viewer, ArenaConfig config) {
        World world = config.world();
        int y = config.y();

        int minX = config.minX();
        int maxX = config.maxX();
        int minZ = config.minZ();
        int maxZ = config.maxZ();

        // Top and bottom edges
        for (int x = minX; x <= maxX; x++) {
            viewer.spawnParticle(
                    Particle.VILLAGER_HAPPY,
                    LocationUtil.centerOfBlock(world, x, y, minZ),
                    3, 0.1, 0.1, 0.1, 0.0
            );
            viewer.spawnParticle(
                    Particle.VILLAGER_HAPPY,
                    LocationUtil.centerOfBlock(world, x, y, maxZ),
                    3, 0.1, 0.1, 0.1, 0.0
            );
        }

        // Left and right edges
        for (int z = minZ; z <= maxZ; z++) {
            viewer.spawnParticle(
                    Particle.VILLAGER_HAPPY,
                    LocationUtil.centerOfBlock(world, minX, y, z),
                    3, 0.1, 0.1, 0.1, 0.0
            );
            viewer.spawnParticle(
                    Particle.VILLAGER_HAPPY,
                    LocationUtil.centerOfBlock(world, maxX, y, z),
                    3, 0.1, 0.1, 0.1, 0.0
            );
        }
    }
}