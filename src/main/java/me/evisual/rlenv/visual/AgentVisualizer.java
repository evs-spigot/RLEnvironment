package me.evisual.rlenv.visual;

import me.evisual.rlenv.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;

public class AgentVisualizer
{
    private final World world;
    private final int yLevel;

    private ArmorStand stand;

    public AgentVisualizer(World world, int yLevel) {
        this.world = world;
        this.yLevel = yLevel;
    }

    public void updatePosition(int x, int z) {
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }

        Location loc = LocationUtil.centerOfBlock(world, x, yLevel, z);

        if (stand == null || stand.isDead()) {
            spawn(loc);
        } else {
            stand.teleport(loc);
        }
    }

    private void spawn(Location location) {
        stand = world.spawn(location, ArmorStand.class, as -> {
            as.setInvisible(false);
            as.setMarker(false);
            as.setSmall(false);
            as.setGlowing(true);
            as.setGravity(false);
            as.setCustomName("RL Agent");
            as.setCustomNameVisible(false);
        });
    }

    public void destroy() {
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
        stand = null;
    }
}