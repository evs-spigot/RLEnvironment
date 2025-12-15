package me.evisual.rlenv.command;

import me.evisual.rlenv.RLEnvPlugin;
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

    private void handleStop(CommandSender sender) {
        if (!plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.RED + "Environment is not running.");
            return;
        }

        plugin.stopEnvironment();
        sender.sendMessage(ChatColor.YELLOW + "RL environment stopped.");
    }

    private void handleStatus(CommandSender sender) {
        if (plugin.isEnvironmentRunning()) {
            sender.sendMessage(ChatColor.GREEN + "Environment is currently running.");
        } else {
            sender.sendMessage(ChatColor.RED + "Environment is not running.");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Usage: /rlenv <start|stop|status>");
    }
}
