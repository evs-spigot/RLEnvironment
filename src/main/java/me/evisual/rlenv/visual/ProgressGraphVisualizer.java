package me.evisual.rlenv.visual;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ProgressGraphVisualizer extends BukkitRunnable {

    private final Player viewer;
    private final Location origin;

    private GraphMode mode = GraphMode.ROLLING;

    // In ROLLING mode we keep only a window
    private final LinkedList<Double> rolling = new LinkedList<>();

    // In CONDENSE mode we keep the full history (bounded)
    private final ArrayList<Double> history = new ArrayList<>();

    // Storage limits (to prevent unbounded growth in long runs)
    private final int rollingMaxPoints = 300;   // points stored for rolling
    private final int historyMaxPoints = 20000; // max timeline points stored

    // Rendering
    private final int drawPoints = 60;        // width (points drawn)
    private final double xSpacing = 0.18;
    private final double yScale = 0.18;
    private final double heightClamp = 3.0;

    private final int axisEveryNTicks = 8;
    private int axisTickCounter = 0;

    private final float particleSize = 0.45f;
    private boolean enabled = true;

    private final double[] epsSeries = new double[512];
    private int epsCount = 0;

    public ProgressGraphVisualizer(Player viewer, Location origin) {
        this.viewer = viewer;
        this.origin = origin.clone();
    }

    public void setMode(GraphMode mode) {
        this.mode = mode;
    }

    public GraphMode getMode() {
        return mode;
    }

    public void addAvgRewardPoint(double avgReward) {
        if (mode == GraphMode.ROLLING) {
            rolling.addLast(avgReward);
            while (rolling.size() > rollingMaxPoints) {
                rolling.removeFirst();
            }
        } else {
            history.add(avgReward);

            // Hard cap memory: if we exceed, downsample the history in-place (keep every other point).
            if (history.size() > historyMaxPoints) {
                ArrayList<Double> compressed = new ArrayList<>(historyMaxPoints / 2);
                for (int i = 0; i < history.size(); i += 2) {
                    compressed.add(history.get(i));
                }
                history.clear();
                history.addAll(compressed);
            }
        }
    }

    public void addEpsilonPoint(double eps) {
        if (epsCount < epsSeries.length) {
            epsSeries[epsCount++] = clamp01(eps);
        } else {
            // rolling
            System.arraycopy(epsSeries, 1, epsSeries, 0, epsSeries.length - 1);
            epsSeries[epsSeries.length - 1] = clamp01(eps);
        }
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

        drawLine(selectSeriesToDraw());
    }

    private List<Double> selectSeriesToDraw() {
        if (mode == GraphMode.ROLLING) {
            return new ArrayList<>(rolling);
        }
        return history;
    }

    private void drawAxes() {
        // Baseline
        for (int i = 0; i < drawPoints; i++) {
            Location xAxis = origin.clone().add(i * xSpacing, 0, 0);
            viewer.spawnParticle(Particle.END_ROD, xAxis, 1, 0, 0, 0, 0);
        }

        // Vertical ticks at left edge
        for (int i = -6; i <= 6; i++) {
            Location yAxis = origin.clone().add(0, i * 0.25, 0);
            viewer.spawnParticle(Particle.END_ROD, yAxis, 1, 0, 0, 0, 0);
        }
    }

    private void drawLine(List<Double> series) {
        int n = series.size();
        if (n < 2) return;

        // Condense always fits whole timeline by downsampling.
        // Rolling fits a window by just showing the most recent points (still downsampled to drawPoints).
        int step = Math.max(1, n / drawPoints);

        Location prevPoint = null;
        int drawnIndex = 0;

        for (int i = 0; i < n; i += step) {
            double v = series.get(i);
            double y = clamp(v * yScale, -heightClamp, heightClamp);

            Location point = origin.clone().add(drawnIndex * xSpacing, y, 0);

            Color color = (y >= 0) ? Color.LIME : Color.RED;

            // point
            viewer.spawnParticle(
                    Particle.REDSTONE,
                    point,
                    1,
                    0, 0, 0, 0,
                    new Particle.DustOptions(color, particleSize)
            );

            // short segment
            if (prevPoint != null) {
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

            prevPoint = point;
            drawnIndex++;
            if (drawnIndex >= drawPoints) break;
        }
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double clamp01(double v) {
        return clamp(v, 0.0, 1.0);
    }
}
