package me.foesio.foSellwands.hook;

import me.foesio.core.logging.FoFileLogger;
import me.foesio.foSellwands.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ProtectionService {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FoFileLogger fileLogger;
    private boolean warnedReflectionFailure;

    public ProtectionService(JavaPlugin plugin, ConfigManager configManager, FoFileLogger fileLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.fileLogger = fileLogger;
    }

    public void reload() {
        warnedReflectionFailure = false;
    }

    public boolean canUse(Player player, Block block) {
        if (configManager.config().getBoolean("protection.admin-bypass", false) && player.hasPermission("fosellwands.admin")) {
            return true;
        }
        if (!configManager.config().getBoolean("protection.worldguard", true)) {
            return true;
        }
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) {
            return true;
        }
        return canUseWorldGuard(player, block.getLocation());
    }

    private boolean canUseWorldGuard(Player player, Location location) {
        try {
            Class<?> worldGuardPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object worldGuardPlugin = worldGuardPluginClass.getMethod("inst").invoke(null);
            Object localPlayer = worldGuardPluginClass.getMethod("wrapPlayer", Player.class).invoke(worldGuardPlugin, player);

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object worldEditLocation = bukkitAdapterClass.getMethod("adapt", Location.class).invoke(null, location);

            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            List<Object> flags = new ArrayList<>();
            addFlag(flags, flagsClass, "INTERACT");
            addFlag(flags, flagsClass, "CHEST_ACCESS");
            addFlag(flags, flagsClass, "BUILD");

            Object flagArray = Array.newInstance(stateFlagClass, flags.size());
            for (int i = 0; i < flags.size(); i++) {
                Array.set(flagArray, i, flags.get(i));
            }

            Class<?> regionAssociableClass = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
            Class<?> worldEditLocationClass = Class.forName("com.sk89q.worldedit.util.Location");
            Method testState = query.getClass().getMethod("testState", worldEditLocationClass, regionAssociableClass, flagArray.getClass());
            Object allowed = testState.invoke(query, worldEditLocation, localPlayer, flagArray);
            return Boolean.TRUE.equals(allowed);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!warnedReflectionFailure) {
                warnedReflectionFailure = true;
                plugin.getLogger().warning("WorldGuard hook failed; allowing sellwand use until restart/reload. " + exception.getMessage());
                fileLogger.warn("WorldGuard hook failed; allowing sellwand use until restart/reload. " + exception.getMessage());
            }
            return true;
        }
    }

    private void addFlag(List<Object> flags, Class<?> flagsClass, String name) throws IllegalAccessException {
        try {
            Field field = flagsClass.getField(name);
            Object flag = field.get(null);
            if (flag != null) {
                flags.add(flag);
            }
        } catch (NoSuchFieldException ignored) {
            // Different WorldGuard builds may rename/remove individual flags.
        }
    }
}
