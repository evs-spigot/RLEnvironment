package me.evisual.rlenv.command;

import me.evisual.rlenv.RLEnvPlugin;
import me.evisual.rlenv.control.EpisodeStats;
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
            case "start" -> {
                handleStart(sender);
                return true;
            }
            case "stop" -> {
                handleStop(sender);
                return true;
            }
            case "status" -> {
                handleStatus(sender);
                return true;
            }
            case "showarena" -> {
                handleShowArena(sender);
                return true;
            }
            case "speed" -> {
                handleSpeed(sender, args);
                return true;
            }
            case "graph" -> {
                handleGraph(sender);
                return true;
            }
            default -> {
                sendUsage(sender);
                return true;
            }
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

    private void handleGraph(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can toggle graphs.");
            return;
        }

        if (!plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.RED + "Environment is not running.");
            return;
        }

        boolean newState = plugin.toggleGraphFor(player);
        sender.sendMessage(ChatColor.GREEN + "Graph display is now " +
                (newState ? "enabled." : "disabled."));
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

        EpisodeStats stats = plugin.getEpisodeStats();
        if (stats == null) {
            sender.sendMessage(ChatColor.YELLOW + "No stats available yet.");
            return;
        }

        sender.sendMessage(ChatColor.AQUA + "RL Environment Status:");
        sender.sendMessage(ChatColor.GRAY + "  Episodes: " + ChatColor.WHITE + stats.episodesCompleted());
        sender.sendMessage(ChatColor.GRAY + "  Successes: " + ChatColor.GREEN + stats.successCount());
        sender.sendMessage(ChatColor.GRAY + "  Failures: " + ChatColor.RED + stats.failureCount());
        sender.sendMessage(ChatColor.GRAY + "  Avg Reward: " + ChatColor.WHITE + String.format("%.3f", stats.averageReward()));
        sender.sendMessage(ChatColor.GRAY + "  Steps per tick: " + ChatColor.WHITE + stats.stepsPerTick());
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
            sender.sendMessage(ChatColor.RED + "Usage: /rlenv speed <stepsPerTick>");
            return;
        }

        if (!plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.RED + "Environment is not running.");
            return;
        }

        int steps;
        try {
            steps = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Speed must be an integer (e.g., 1, 2, 5, 10).");
            return;
        }

        boolean ok = plugin.setEnvironmentSpeed(steps);
        if (!ok) {
            sender.sendMessage(ChatColor.RED + "Failed to change speed (runner not available).");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Environment speed set to " + steps + " steps per tick.");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Usage: /rlenv <start|stop|status|showarena|speed>");
    }
}