package ru.meinych15.realminepapi;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RealMinePAPI extends JavaPlugin {

    private LuckPerms luckPerms;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupLuckPerms();
        new RealMinePAPIExpansion(this).register();
        Objects.requireNonNull(getCommand("rmpapi")).setExecutor(this);
        Objects.requireNonNull(getCommand("rmpapi")).setTabCompleter(new RMPAPITabCompleter());
    }

    private void setupLuckPerms() {
        luckPerms = LuckPermsProvider.get();
        EventBus eventBus = luckPerms.getEventBus();
        eventBus.subscribe(this, UserDataRecalculateEvent.class, this::onUserDataRecalculate);
    }

    private void onUserDataRecalculate(UserDataRecalculateEvent event) {
        cache.remove(event.getUser().getUniqueId());
    }

    @Override
    public void onDisable() {
        executor.shutdown();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rmpapi.reload")) {
                sender.sendMessage(colorize(getConfig().getString("messages.no_permission")));
                return true;
            }

            reloadConfig();
            cache.clear();
            sender.sendMessage(colorize(getConfig().getString("messages.reload_success")));
            return true;
        }
        sender.sendMessage(colorize("&cИспользование: /rmpapi reload"));
        return true;
    }

    String colorize(String text) {
        if (text == null) return "";

        text = ChatColor.translateAlternateColorCodes('&', text);
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        return matcher.appendTail(buffer).toString();
    }

    class RealMinePAPIExpansion extends PlaceholderExpansion {
        private final RealMinePAPI plugin;
        private final Map<String, String> chatMappings = new ConcurrentHashMap<>();

        RealMinePAPIExpansion(RealMinePAPI plugin) {
            this.plugin = plugin;
            reloadMappings();
        }

        void reloadMappings() {
            chatMappings.clear();
            plugin.getConfig().getConfigurationSection("chat").getValues(false)
                    .forEach((k, v) -> chatMappings.put(k.toLowerCase(), v.toString()));
        }

        @Override
        public @NotNull String getIdentifier() {
            return "rmpapi";
        }

        @Override
        public @NotNull String getAuthor() {
            return "meinych15";
        }

        @Override
        public @NotNull String getVersion() {
            return "1.0-SNAPSHOT";
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
            if (player == null) return "";

            if (identifier.equalsIgnoreCase("chat")) {
                return getChatFormat(player);
            }

            return handleCustomPlaceholder(player, identifier);
        }

        private String getChatFormat(Player player) {
            return cache.computeIfAbsent(player.getUniqueId(), uuid -> {
                User user = luckPerms.getUserManager().getUser(uuid);
                if (user == null) return "";

                return user.getNodes().stream()
                        .filter(node -> node.getKey().startsWith("group."))
                        .map(node -> node.getKey().substring(6))
                        .map(String::toLowerCase)
                        .filter(chatMappings::containsKey)
                        .findFirst()
                        .map(chatMappings::get)
                        .map(plugin::colorize)
                        .orElse("");
            });
        }

        private String handleCustomPlaceholder(Player player, String identifier) {
            String placeholder = "%" + identifier + "%";
            String value = PlaceholderAPI.setPlaceholders(player, placeholder);

            if (value.isEmpty() || value.equals(placeholder)) {
                return plugin.colorize(plugin.getConfig()
                        .getString("placeholder_messages." + identifier));
            }
            return plugin.colorize(value);
        }
    }

    class RMPAPITabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            return args.length == 1 && "reload".startsWith(args[0].toLowerCase())
                    ? Collections.singletonList("reload")
                    : Collections.emptyList();
        }
    }
}