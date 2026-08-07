package me.foesio.foSellwands.hook;

import me.foesio.core.message.FoMessageService;
import me.foesio.foSellwands.FoSellwands;
import me.foesio.foSellwands.config.ConfigManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class HologramService implements Listener {
    private final FoSellwands plugin;
    private final ConfigManager configManager;
    private final FoMessageService messages;
    private final NamespacedKey hologramKey;

    public HologramService(FoSellwands plugin, ConfigManager configManager, FoMessageService messages) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messages = messages;
        this.hologramKey = new NamespacedKey(plugin, "sell_hologram");
    }

    public void cleanupLoadedHolograms() {
        plugin.getServer().getWorlds().forEach(world -> {
            world.getEntitiesByClass(ArmorStand.class).forEach(this::removeIfTagged);
            world.getEntitiesByClass(TextDisplay.class).forEach(this::removeIfTagged);
        });
    }

    public void spawn(Block block, Map<String, String> replacements) {
        if (!configManager.config().getBoolean("feedback.hologram.enabled", false)) {
            return;
        }
        List<String> lines = configManager.config().getStringList("feedback.hologram.lines");
        if (lines.isEmpty()) {
            return;
        }
        String text = lines.stream()
                .map(line -> messages.renderTemplate(line, replacements))
                .collect(Collectors.joining("\n"));
        Location location = block.getLocation().add(0.5D, configManager.config().getDouble("feedback.hologram.y-offset", 1.35D), 0.5D);
        TextDisplay display = block.getWorld().spawn(location, TextDisplay.class);
        display.setPersistent(false);
        display.setGravity(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setLineWidth(Math.max(40, configManager.config().getInt("feedback.hologram.line-width", 180)));
        display.setViewRange((float) Math.max(1.0D, configManager.config().getDouble("feedback.hologram.view-range", 24.0D)));
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(clamp(configManager.config().getInt("feedback.hologram.background-alpha", 0)), 0, 0, 0));
        display.setTextOpacity((byte) 255);
        display.setShadowed(configManager.config().getBoolean("feedback.hologram.shadow", true));
        display.setSeeThrough(configManager.config().getBoolean("feedback.hologram.see-through", false));
        display.setText(text);
        display.getPersistentDataContainer().set(hologramKey, PersistentDataType.BYTE, (byte) 1);

        long duration = Math.max(1, configManager.config().getLong("feedback.hologram.duration-ticks", 45));
        plugin.core().scheduler().runGlobalLater(display::remove, duration);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ArmorStand || entity instanceof TextDisplay) {
                removeIfTagged(entity);
            }
        }
    }

    private void removeIfTagged(Entity entity) {
        if (entity.getPersistentDataContainer().has(hologramKey, PersistentDataType.BYTE)) {
            entity.remove();
        }
    }

    private int clamp(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }
}
