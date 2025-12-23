package me.evisual.rlenv.command;

import me.evisual.rlenv.RLEnvPlugin;
import me.evisual.rlenv.control.EpisodeStats;
import me.evisual.rlenv.visual.GraphMode;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RLEnvCommand implements CommandExecutor {

    private final RLEnvPlugin plugin;

    public RLEnvCommand(RLEnvPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> { handleStart(sender); return true; }
            case "stop" -> { handleStop(sender); return true; }
            case "status" -> { handleStatus(sender); return true; }
            case "showarena" -> { handleShowArena(sender); return true; }
            case "speed" -> { handleSpeed(sender, args); return true; }
            case "graph" -> { handleGraph(sender, args); return true; }
            case "progression" -> { handleProgression(sender, args); return true; }
            default -> { sendUsage(sender); return true; }
        }
    }

    private void handleStart(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can start the RL environment.");
            return;
        }
        if (plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.RED + "Environment is already running.");
            return;
        }
        plugin.startEnvironment(player);
        sender.sendMessage(ChatColor.GREEN + "RL environment started.");
    }

    private void handleStop(CommandSender sender) {
        if (!plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.RED + "Environment is not running.");
            return;
        }
        plugin.stopEnvironment();
        sender.sendMessage(ChatColor.YELLOW + "RL environment stopped.");
    }

    private void handleStatus(CommandSender sender) {
        if (!plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.RED + "Environment is not running.");
            return;
        }

        EpisodeStats s = plugin.getEpisodeStats();
        if (s == null) {
            sender.sendMessage(ChatColor.YELLOW + "No stats available yet.");
            return;
        }

        sender.sendMessage(ChatColor.AQUA + "RL Environment Status");
        sender.sendMessage(ChatColor.GRAY + "  Episodes: " + ChatColor.WHITE + s.episodesCompleted());
        sender.sendMessage(ChatColor.GRAY + "  Speed: " + ChatColor.WHITE + String.format("%.2f steps/sec", s.stepsPerSecond()));
        sender.sendMessage(ChatColor.GRAY + "  Throughput: " + ChatColor.WHITE + String.format("%.1f episodes/min", s.episodesPerMinute()));

        sender.sendMessage(ChatColor.GRAY + "  Success rate: " + ChatColor.WHITE +
                String.format("%.1f%% overall | %.1f%% recent", 100.0 * s.overallSuccessRate(), 100.0 * s.recentSuccessRate()));

        sender.sendMessage(ChatColor.GRAY + "  Steps-to-goal: " + ChatColor.WHITE +
                String.format("%.1f avg overall | %.1f avg recent | best=%d",
                        s.overallAvgStepsToGoal(), s.recentAvgStepsToGoal(), s.bestStepsToGoal()));

        sender.sendMessage(ChatColor.GRAY + "  Successes: " + ChatColor.GREEN + s.successCount()
                + ChatColor.GRAY + "  Failures: " + ChatColor.RED + s.failureCount());

        if (s.epsilon() >= 0) {
            sender.sendMessage(ChatColor.GRAY + "  Exploration (epsilon): " + ChatColor.WHITE + String.format("%.3f", s.epsilon()));
        }
        if (s.stateCount() >= 0) {
            sender.sendMessage(ChatColor.GRAY + "  Q-table states: " + ChatColor.WHITE + s.stateCount());
        }
    }

    private void handleShowArena(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can view the arena outline.");
            return;
        }
        if (!plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.RED + "Environment is not running.");
            return;
        }
        plugin.showArena(player);
    }

    private void handleSpeed(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rlenv speed <stepsPerSecond>");
            return;
        }
        if (!plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.RED + "Environment is not running.");
            return;
        }

        double sps;
        try {
            sps = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Speed must be a number (e.g., 0.5, 1, 10, 200).");
            return;
        }

        plugin.setEnvironmentSpeed(sps);
        sender.sendMessage(ChatColor.GREEN + "Environment speed set to " + sps + " steps/sec.");
    }

    private void handleGraph(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use graph commands.");
            return;
        }
        if (!plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.RED + "Environment is not running.");
            return;
        }

        if (args.length == 1) {
            boolean newState = plugin.toggleGraphFor(player);
            sender.sendMessage(ChatColor.GREEN + "Graph display is now " + (newState ? "enabled" : "disabled") + ".");
            return;
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("mode")) {
            GraphMode mode;
            if (args[2].equalsIgnoreCase("rolling")) mode = GraphMode.ROLLING;
            else if (args[2].equalsIgnoreCase("condense")) mode = GraphMode.CONDENSE;
            else {
                sender.sendMessage(ChatColor.RED + "Usage: /rlenv graph mode <rolling|condense>");
                return;
            }

            boolean ok = plugin.setGraphMode(mode);
            if (!ok) {
                sender.sendMessage(ChatColor.RED + "Graph not available.");
                return;
            }

            sender.sendMessage(ChatColor.GREEN + "Graph mode set to " + mode.name().toLowerCase() + ".");
            return;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /rlenv graph  OR  /rlenv graph mode <rolling|condense>");
    }

    private void handleProgression(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use progression.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rlenv progression <start|next|stop>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "start" -> {
                plugin.getProgressionManager().start(player);
                sender.sendMessage(ChatColor.GREEN + "Progression started (Level 1).");
            }
            case "next" -> {
                plugin.getProgressionManager().next(player);
                sender.sendMessage(ChatColor.GREEN + "Advanced progression to Level " + (plugin.getProgressionManager().getLevelIndex() + 1) + ".");
            }
            case "stop" -> {
                plugin.getProgressionManager().stop();
                sender.sendMessage(ChatColor.YELLOW + "Progression stopped and room restored.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /rlenv progression <start|next|stop>");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Usage: /rlenv <start|stop|status|showarena|speed|graph|progression>");
    }
}