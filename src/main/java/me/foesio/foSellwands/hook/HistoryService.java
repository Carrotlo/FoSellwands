package me.foesio.foSellwands.hook;

import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foSellwands.config.ConfigManager;
import me.foesio.foSellwands.sell.SellResult;
import me.foesio.foSellwands.wand.WandData;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public final class HistoryService {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FoScheduler scheduler;
    private final FoFileLogger fileLogger;
    private final Queue<String> pendingLines = new ConcurrentLinkedQueue<>();
    private final Object flushLock = new Object();
    private boolean running;
    private String latestDate;

    public HistoryService(JavaPlugin plugin, ConfigManager configManager, FoScheduler scheduler, FoFileLogger fileLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.scheduler = scheduler;
        this.fileLogger = fileLogger;
    }

    public void start() {
        if (configManager.config().getBoolean("history.enabled", true)) {
            resetLatestLog();
        }
        running = true;
        scheduleFlush();
        fileLogger.info("Sale history service started.");
    }

    public void close() {
        running = false;
        flush();
    }

    public void logSale(Player player, Block block, WandData data, int usesRemaining, SellResult result) {
        if (!configManager.config().getBoolean("history.enabled", true)) {
            return;
        }
        String breakdown = result.breakdown().stream()
                .map(entry -> entry.itemName() + " x" + entry.amount() + " $" + entry.price())
                .collect(Collectors.joining(", "));
        String line = Instant.now()
                + " player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " world=" + block.getWorld().getName()
                + " x=" + block.getX()
                + " y=" + block.getY()
                + " z=" + block.getZ()
                + " container=" + block.getType()
                + " wandId=" + data.id()
                + " wandUuid=" + data.uuid()
                + " multiplier=" + data.multiplier()
                + " usesRemaining=" + usesRemaining
                + " soldItems=" + result.itemAmount()
                + " money=" + result.money()
                + " breakdown=[" + breakdown + "]"
                + System.lineSeparator();

        pendingLines.add(line);
    }

    private void resetLatestLog() {
        scheduler.runAsync(() -> {
            synchronized (flushLock) {
                try {
                    Path folder = historyFolder();
                    Files.createDirectories(folder);
                    String date = LocalDate.now(ZoneId.systemDefault()).toString();
                    Files.writeString(folder.resolve("latest.log"), "", StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    latestDate = date;
                } catch (IOException exception) {
                    plugin.getLogger().warning("Could not prepare sell history log: " + exception.getMessage());
                    fileLogger.warn("Could not prepare sell history log: " + exception.getMessage());
                }
            }
        });
    }

    private void scheduleFlush() {
        if (!running) {
            return;
        }
        scheduler.runGlobalLater(() -> {
            scheduler.runAsync(this::flush);
            scheduleFlush();
        }, 40L);
    }

    private void flush() {
        if (pendingLines.isEmpty()) {
            return;
        }
        synchronized (flushLock) {
            if (pendingLines.isEmpty()) {
                return;
            }
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = pendingLines.poll()) != null) {
                builder.append(line);
            }
            try {
                Path folder = historyFolder();
                Files.createDirectories(folder);
                String date = LocalDate.now(ZoneId.systemDefault()).toString();
                Path daily = folder.resolve(date + ".log");
                Path latest = folder.resolve("latest.log");
                if (!date.equals(latestDate)) {
                    Files.writeString(latest, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    latestDate = date;
                }
                String output = builder.toString();
                Files.writeString(daily, output, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                Files.writeString(latest, output, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not write sell history: " + exception.getMessage());
                fileLogger.warn("Could not write sell history: " + exception.getMessage());
            }
        }
    }

    private Path historyFolder() {
        String configured = configManager.config().getString("history.folder", "sale-history");
        String folder = configured == null || configured.isBlank() ? "sale-history" : configured;
        return plugin.getDataFolder().toPath().resolve(folder);
    }
}
