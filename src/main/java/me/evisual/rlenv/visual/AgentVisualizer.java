package me.evisual.rlenv.visual;

import me.evisual.rlenv.env.goldcollector.ArenaConfig;
import me.evisual.rlenv.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Set;

public class AgentVisualizer {

    private final JavaPlugin plugin;
    private final ArenaConfig config;
    private final World world;

    private Zombie zombie;
    private Location target;

    // Smooth movement
    private double stepPerTick = 0.18; // 0.12 (slow/smooth) .. 0.30 (fast/smooth)
    private double stopDistance = 0.10;

    // Simulated jump
    private double yVel = 0.0;
    private final double gravity = 0.08;
    private final double jumpVel = 0.42;

    // Controlled breaking
    private boolean breakBlocks = true;

    private final Set<Material> breakable = EnumSet.of(
            Material.TALL_GRASS, Material.GRASS, Material.OAK_LEAVES,
            Material.DIRT, Material.GRASS_BLOCK, Material.SAND, Material.GRAVEL,
            Material.SNOW, Material.SNOW_BLOCK,
            Material.COBBLESTONE, Material.STONE
    );

    private BukkitRunnable task;

    public AgentVisualizer(JavaPlugin plugin, ArenaConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.world = config.world();
        startLoop();
    }

    /** Environment calls this every tick/step with logical agent coords. */
    public void updatePosition(int x, int y, int z) {
        Location t = LocationUtil.centerOfBlock(world, x, y, z);
        t.setY(t.getY() - 0.35); // visual "feet on ground" offset for baby zombie
        this.target = t;

        if (zombie == null || zombie.isDead()) {
            spawnAt(this.target);
        }
    }

    private void spawnAt(Location loc) {
        zombie = world.spawn(loc, Zombie.class, z -> {
            z.setBaby(true);
            z.setAI(false);
            z.setSilent(true);
            z.setInvulnerable(true);
            z.setCollidable(false);
            z.setCanPickupItems(false);
            z.setGlowing(true);
            z.setRemoveWhenFarAway(false);
            z.setPersistent(true);
            z.setCustomName("RL Agent");
            z.setCustomNameVisible(false);
        });
        yVel = 0.0;
    }

    private void startLoop() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (zombie == null || zombie.isDead() || target == null) return;

                Location cur = zombie.getLocation();

                // Horizontal direction
                Vector delta = target.toVector().subtract(cur.toVector());
                Vector horiz = new Vector(delta.getX(), 0, delta.getZ());
                double dist = horiz.length();

                // Apply simple vertical physics every tick
                yVel -= gravity;
                if (yVel < -0.6) yVel = -0.6;

                if (dist <= stopDistance) {
                    Location snap = cur.clone();
                    snap.setX(target.getX());
                    snap.setZ(target.getZ());

                    double nextY = snap.getY() + yVel;
                    if (nextY <= target.getY()) {
                        nextY = target.getY();
                        yVel = 0.0;
                    }
                    snap.setY(nextY);
                    snap.setYaw(yawFromVector(delta));
                    snap.setPitch(0f);

                    zombie.teleport(snap);
                    return;
                }

                Vector dir = horiz.normalize();

                handleObstacles(cur, dir);

                Vector step = dir.multiply(Math.min(stepPerTick, dist));
                Location next = cur.clone().add(step);

                double nextY = next.getY() + yVel;

                double minY = Math.min(target.getY(), next.getY());
                if (nextY < minY) {
                    nextY = minY;
                    yVel = 0.0;
                }

                if (nextY > target.getY() + 1.2) {
                    nextY = target.getY() + 1.2;
                    yVel = 0.0;
                }

                next.setY(nextY);
                next.setYaw(yawFromVector(step));
                next.setPitch(0f);

                zombie.teleport(next);
            }
        };

        task.runTaskTimer(plugin, 0L, 1L);
    }

    private void handleObstacles(Location cur, Vector dir) {
        int stepX = 0;
        int stepZ = 0;
        if (Math.abs(dir.getX()) > Math.abs(dir.getZ())) stepX = (int) Math.signum(dir.getX());
        else stepZ = (int) Math.signum(dir.getZ());

        if (stepX == 0 && stepZ == 0) return;

        int aheadX = cur.getBlockX() + stepX;
        int aheadZ = cur.getBlockZ() + stepZ;

        if (!LocationUtil.isWithinBounds(aheadX, aheadZ, config.minX(), config.maxX(), config.minZ(), config.maxZ())) {
            return;
        }

        int y = cur.getBlockY();

        Material feet = world.getBlockAt(aheadX, y, aheadZ).getType();
        Material head = world.getBlockAt(aheadX, y + 1, aheadZ).getType();

        boolean feetSolid = feet.isSolid();
        boolean headSolid = head.isSolid();

        if (breakBlocks) {
            if (feetSolid && breakable.contains(feet)) {
                world.getBlockAt(aheadX, y, aheadZ).breakNaturally();
                feetSolid = false;
            }
            if (headSolid && breakable.contains(head)) {
                world.getBlockAt(aheadX, y + 1, aheadZ).breakNaturally();
                headSolid = false;
            }
        }

        if (feetSolid && !headSolid) {
            if (yVel <= 0.05) {
                yVel = jumpVel;
            }
        }
    }

    public void onGoalHit() {
        if (zombie == null || zombie.isDead()) return;

        Location loc = zombie.getLocation().clone().add(0, 0.6, 0);

        zombie.getWorld().spawnParticle(Particle.TOTEM, loc, 80, 0.4, 0.6, 0.4, 0.02);
        zombie.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc, 30, 0.4, 0.6, 0.4, 0.02);
        zombie.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // quick "blink" to make it super obvious
        zombie.teleport(loc.clone().add(0, 0.35, 0));
    }

    private float yawFromVector(Vector v) {
        if (v.lengthSquared() < 1.0e-6) return 0f;
        double yaw = Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
        return (float) yaw;
    }

    public void setBreakBlocks(boolean enabled) {
        this.breakBlocks = enabled;
    }

    public void setStepPerTick(double s) {
        if (s < 0.05) s = 0.05;
        if (s > 0.40) s = 0.40;
        this.stepPerTick = s;
    }

    public void destroy() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (zombie != null && !zombie.isDead()) zombie.remove();
        zombie = null;
        target = null;
    }
}