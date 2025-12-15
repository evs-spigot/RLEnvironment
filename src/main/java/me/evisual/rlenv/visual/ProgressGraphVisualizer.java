package me.evisual.rlenv.visual;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.LinkedList;
import java.util.List;

public class ProgressGraphVisualizer extends BukkitRunnable {

    private final Player viewer;
    private final Location origin; // static anchor

    private final LinkedList<Double> avgRewardPoints = new LinkedList<>();

    // Performance/quality knobs
    private final int maxPoints = 120;        // store more points
    private final int drawPoints = 40;        // but draw fewer (downsample)
    private final double xSpacing = 0.18;     // width of the drawn graph
    private final double yScale = 0.18;
    private final double heightClamp = 3.0;

    private final int axisEveryNTicks = 6;    // draw axes only every N runs
    private int axisTickCounter = 0;

    private final float particleSize = 0.45f;
    private boolean enabled = true;

    public ProgressGraphVisualizer(Player viewer, Location origin) {
        this.viewer = viewer;
        this.origin = origin.clone();
    }

    public void addAvgRewardPoint(double avgReward) {
        if (avgRewardPoints.size() >= maxPoints) {
            avgRewardPoints.removeFirst();
        }
        avgRewardPoints.addLast(avgReward);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void run() {
        if (!enabled) return;
        if (!viewer.isOnline()) return;

        axisTickCounter++;
        if (axisTickCounter >= axisEveryNTicks) {
            axisTickCounter = 0;
            drawAxes();
        }

        drawLineDownsampled();
    }

    private void drawAxes() {
        // Lightweight axes: baseline + a few ticks
        int width = drawPoints;

        // Baseline (y=0)
        for (int i = 0; i < width; i++) {
            Location xAxis = origin.clone().add(i * xSpacing, 0, 0);
            viewer.spawnParticle(Particle.END_ROD, xAxis, 1, 0, 0, 0, 0);
        }

        // Vertical ticks at left edge
        for (int i = -6; i <= 6; i++) {
            Location yAxis = origin.clone().add(0, i * 0.25, 0);
            viewer.spawnParticle(Particle.END_ROD, yAxis, 1, 0, 0, 0, 0);
        }
    }

    private void drawLineDownsampled() {
        int n = avgRewardPoints.size();
        if (n < 2) return;

        // Downsample so we draw at most `drawPoints` points
        int step = Math.max(1, n / drawPoints);

        Double prevVal = null;
        Location prevPoint = null;
        int drawnIndex = 0;

        for (int i = 0; i < n; i += step) {
            double v = avgRewardPoints.get(i);
            double y = clamp(v * yScale, -heightClamp, heightClamp);

            Location point = origin.clone().add(drawnIndex * xSpacing, y, 0);

            Color color = (y >= 0) ? Color.LIME : Color.RED;

            // Draw the point
            viewer.spawnParticle(
                    Particle.REDSTONE,
                    point,
                    1,
                    0, 0, 0, 0,
                    new Particle.DustOptions(color, particleSize)
            );

            // Draw a short segment from previous point → current point
            if (prevPoint != null && prevVal != null) {
                // fewer steps = less lag
                int segSteps = 3;
                for (int s = 1; s <= segSteps; s++) {
                    double t = s / (double) segSteps;
                    Location mid = prevPoint.clone().add(
                            (point.getX() - prevPoint.getX()) * t,
                            (point.getY() - prevPoint.getY()) * t,
                            (point.getZ() - prevPoint.getZ()) * t
                    );

                    viewer.spawnParticle(
                            Particle.REDSTONE,
                            mid,
                            1,
                            0, 0, 0, 0,
                            new Particle.DustOptions(color, particleSize)
                    );
                }
            }

            prevVal = v;
            prevPoint = point;
            drawnIndex++;

            if (drawnIndex >= drawPoints) break;
        }
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}