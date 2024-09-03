package so.max1soft.endblocker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class EndBlocker extends JavaPlugin implements Listener {

    private List<TimeInterval> enabledIntervals;
    private List<String> endEnabledMessages;
    private List<String> endDisabledMessages;
    private List<String> teleportMessages;
    private Location teleportLocation;
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private boolean wasEndEnabled = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getLogger().info("");
        getLogger().info("§fПлагин: §aЗапущен");
        getLogger().info("§fСоздатель: §b@max1soft");
        getLogger().info("§fВерсия: §c1.0");
        getLogger().info("");
        getLogger().info("§fИнформация: §dMax1soft.pw");
        getLogger().info("");

        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                checkEndStatus();
            }
        }.runTaskTimer(this, 0, 200); // Проверка каждые 10 секунд (200 тиков)
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();

        endEnabledMessages = config.getStringList("messages.end_enabled");
        endDisabledMessages = config.getStringList("messages.end_disabled");
        teleportMessages = config.getStringList("messages.teleport_message");

        String teleportWorldName = config.getString("teleport_world");
        World teleportWorld = Bukkit.getWorld(teleportWorldName);

        if (teleportWorld == null) {
            getLogger().severe("Teleport world '" + teleportWorldName + "' not found! Disabling plugin.");
            return;
        }

        double x = config.getDouble("teleport_coordinates.x");
        double y = config.getDouble("teleport_coordinates.y");
        double z = config.getDouble("teleport_coordinates.z");
        float yaw = (float) config.getDouble("teleport_coordinates.yaw");
        float pitch = (float) config.getDouble("teleport_coordinates.pitch");

        teleportLocation = new Location(teleportWorld, x, y, z, yaw, pitch);

        // Инициализация enabledIntervals
        enabledIntervals = new ArrayList<>();
        for (String interval : config.getStringList("time_intervals")) {
            String[] times = interval.split("-");
            if (times.length == 2) {
                try {
                    LocalTime startTime = LocalTime.parse(times[0], timeFormatter);
                    LocalTime endTime = LocalTime.parse(times[1], timeFormatter);
                    enabledIntervals.add(new TimeInterval(startTime, endTime));
                } catch (Exception e) {
                    getLogger().warning("Invalid time interval format: " + interval);
                }
            } else {
                getLogger().warning("Invalid time interval format: " + interval);
            }
        }
    }

    private boolean isEndEnabled() {
        if (enabledIntervals == null) {
            return false;
        }
        LocalTime now = LocalTime.now(ZoneId.of("Europe/Moscow"));
        for (TimeInterval interval : enabledIntervals) {
            if (interval.isWithin(now)) {
                return true;
            }
        }
        return false;
    }

    private String getNextOpeningTime() {
        if (enabledIntervals == null) {
            return "N/A";
        }
        LocalTime now = LocalTime.now(ZoneId.of("Europe/Moscow"));
        for (TimeInterval interval : enabledIntervals) {
            if (interval.getStartTime().isAfter(now)) {
                return interval.getStartTime().format(timeFormatter);
            }
        }
        return enabledIntervals.get(0).getStartTime().format(timeFormatter);
    }

    private String getEndTime() {
        if (enabledIntervals == null) {
            return "N/A";
        }
        LocalTime now = LocalTime.now(ZoneId.of("Europe/Moscow"));
        for (TimeInterval interval : enabledIntervals) {
            if (interval.isWithin(now)) {
                return interval.getEndTime().format(timeFormatter);
            }
        }
        return "N/A";
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();

        if (toWorld.getName().equals("world_the_end") && !isEndEnabled()) {
            teleportPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        World currentWorld = player.getWorld();

        if (currentWorld.getName().equals("world_the_end") && !isEndEnabled()) {
            teleportPlayer(player);
        }
    }

    private void teleportPlayer(Player player) {

        player.teleport(teleportLocation);
        for (String message : teleportMessages) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    private void checkEndStatus() {
        boolean isEndEnabled = isEndEnabled();
        if (isEndEnabled && !wasEndEnabled) {
            for (String message : endEnabledMessages) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{END_TIME}", getEndTime())));
            }
        } else if (!isEndEnabled && wasEndEnabled) {
            for (String message : endDisabledMessages) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{NEXT_OPENING_TIME}", getNextOpeningTime())));
            }
        }
        wasEndEnabled = isEndEnabled;
    }

    private static class TimeInterval {
        private final LocalTime startTime;
        private final LocalTime endTime;

        public TimeInterval(LocalTime startTime, LocalTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public LocalTime getStartTime() {
            return startTime;
        }

        public LocalTime getEndTime() {
            return endTime;
        }

        public boolean isWithin(LocalTime time) {
            return !time.isBefore(startTime) && !time.isAfter(endTime);
        }
    }
}
