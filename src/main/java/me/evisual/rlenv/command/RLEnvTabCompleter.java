package me.evisual.rlenv.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RLEnvTabCompleter implements TabCompleter {

    private static final List<String> ROOT_ARGS = List.of(
            "start",
            "stop",
            "status",
            "showarena",
            "speed",
            "graph",
            "progression",
            "reload"
    );

    private static final List<String> GRAPH_ARGS = List.of("mode");
    private static final List<String> GRAPH_MODES = List.of("rolling", "condense");
    private static final List<String> PROGRESSION_ARGS = List.of("start", "next", "stop");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return match(args[0], ROOT_ARGS);
        }

        String root = args[0].toLowerCase();
        if (args.length == 2) {
            return switch (root) {
                case "graph" -> match(args[1], GRAPH_ARGS);
                case "progression" -> match(args[1], PROGRESSION_ARGS);
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && root.equals("graph") && args[1].equalsIgnoreCase("mode")) {
            return match(args[2], GRAPH_MODES);
        }

        return Collections.emptyList();
    }

    private static List<String> match(String token, List<String> options) {
        List<String> matches = new ArrayList<>(options.size());
        StringUtil.copyPartialMatches(token, options, matches);
        Collections.sort(matches);
        return matches;
    }
}
