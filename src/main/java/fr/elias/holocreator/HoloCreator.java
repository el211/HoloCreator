package fr.elias.holocreator;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HoloCreator extends JavaPlugin implements CommandExecutor, TabCompleter {

    private File configFile;
    private FileConfiguration holograms;
    private final List<Entity> spawnedHolograms = new ArrayList<>();

    @Override
    public void onEnable() {
        // Register commands
        this.getCommand("holocreate").setExecutor(this);
        this.getCommand("holodelete").setExecutor(this);
        this.getCommand("holoreload").setExecutor(this);
        this.getCommand("holodelete").setTabCompleter(this);

        // Load config and holograms
        createConfig();
        loadHolograms();
    }

    @Override
    public void onDisable() {
        removeAllHolograms(); // Cleanup all holograms on shutdown
        saveHolograms();
    }

    private void createConfig() {
        configFile = new File(getDataFolder(), "holograms.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("holograms.yml", false);
        }
        holograms = YamlConfiguration.loadConfiguration(configFile);
    }

    private void saveHolograms() {
        try {
            holograms.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadHolograms() {
        removeAllHolograms(); // Remove all existing holograms first

        for (String key : holograms.getKeys(false)) {
            double x = holograms.getDouble(key + ".x");
            double y = holograms.getDouble(key + ".y");
            double z = holograms.getDouble(key + ".z");
            String worldName = holograms.getString(key + ".world");
            String message = ColorUtils.parseAllColors(holograms.getString(key + ".message"));

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("Could not load hologram " + key + " because the world " + worldName + " is not loaded.");
                continue;
            }

            Location loc = new Location(world, x, y, z);
            spawnHologram(loc, message);
        }
    }

    private void removeAllHolograms() {
        for (Entity entity : spawnedHolograms) {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        spawnedHolograms.clear(); // Clear the list after removing entities
        getLogger().info("All holograms have been removed.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("holocreate")) {
            if (!player.hasPermission("holocreator.create")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Usage: /holocreate <message>");
                return true;
            }

            String message = String.join(" ", args);
            message = ColorUtils.parseAllColors(message);
            Location loc = player.getLocation();

            spawnHologram(loc, message);

            // Save hologram to config
            String id = UUID.randomUUID().toString();
            holograms.set(id + ".x", loc.getX());
            holograms.set(id + ".y", loc.getY());
            holograms.set(id + ".z", loc.getZ());
            holograms.set(id + ".world", loc.getWorld().getName());
            holograms.set(id + ".message", message);
            saveHolograms();

            player.sendMessage(ChatColor.GREEN + "Hologram created!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("holodelete")) {
            if (!player.hasPermission("holocreator.delete")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /holodelete <id>");
                return true;
            }

            String id = args[0];
            if (!holograms.contains(id)) {
                player.sendMessage(ChatColor.RED + "No hologram found with that ID.");
                return true;
            }

            // Remove the hologram from the world and config
            removeHologram(id);

            player.sendMessage(ChatColor.GREEN + "Hologram deleted!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("holoreload")) {
            if (!player.hasPermission("holocreator.reload")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            createConfig();
            loadHolograms();
            player.sendMessage(ChatColor.GREEN + "Holograms reloaded!");
            return true;
        }

        return false;
    }

    private void removeHologram(String id) {
        if (!holograms.contains(id)) {
            getLogger().warning("Tried to remove a hologram that does not exist: " + id);
            return;
        }

        // Retrieve hologram data from config
        double x = holograms.getDouble(id + ".x");
        double y = holograms.getDouble(id + ".y");
        double z = holograms.getDouble(id + ".z");
        String worldName = holograms.getString(id + ".world");
        String configMessage = ColorUtils.parseAllColors(holograms.getString(id + ".message"));

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Could not remove hologram " + id + " because the world " + worldName + " is not loaded.");
            return;
        }

        Location loc = new Location(world, x, y, z);

        // Log debug information about the location and message
        getLogger().info("Looking for hologram at location: " + loc + " with message: " + configMessage);

        // Stripped version of the message for comparison
        String strippedConfigMessage = ChatColor.stripColor(configMessage);

        // Search for matching hologram entities near the specified location
        boolean removed = false;
        for (Entity entity : world.getNearbyEntities(loc, 2, 3, 2)) { // Increased radius for safety
            if (entity instanceof TextDisplay) {
                // Get the text of the TextDisplay entity
                TextDisplay textDisplay = (TextDisplay) entity;
                String entityText = textDisplay.getText();
                String strippedEntityText = entityText != null ? ChatColor.stripColor(entityText) : "";

                // Log debug information about found entities
                getLogger().info("Found TEXT_DISPLAY with text: " + entityText);

                // Compare stripped names
                if (strippedEntityText.equals(strippedConfigMessage)) {
                    entity.remove(); // Remove the matching entity
                    removed = true;
                    getLogger().info("Hologram " + id + " removed from the world.");
                }
            } else if (entity instanceof ArmorStand) {
                // Get the custom name of the ArmorStand entity
                String entityName = entity.getCustomName();
                String strippedEntityName = entityName != null ? ChatColor.stripColor(entityName) : "";

                // Log debug information about found entities
                getLogger().info("Found ARMOR_STAND with name: " + entityName);

                // Compare stripped names
                if (strippedEntityName.equals(strippedConfigMessage)) {
                    entity.remove(); // Remove the matching entity
                    removed = true;
                    getLogger().info("Hologram " + id + " removed from the world.");
                }
            }
        }

        if (!removed) {
            // Log if no matching hologram was found
            getLogger().warning("Could not find hologram entity at location " + loc + " with message " + configMessage);
        }

        // Remove hologram from config and save
        holograms.set(id, null);
        saveHolograms();
    }



    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("holodelete") && args.length == 1) {
            completions.addAll(holograms.getKeys(false));
        }
        return completions;
    }

    private void spawnHologram(Location location, String text) {
        if (location.getWorld() == null) {
            getLogger().warning("Failed to spawn hologram because the world is null!");
            return;
        }

        boolean isBedrock = false;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId())) {
                isBedrock = true;
                break;
            }
        }

        Entity hologram;

        if (Bukkit.getVersion().contains("1.21") && !isBedrock) {
            TextDisplay holo = location.getWorld().spawn(location, TextDisplay.class);
            holo.setText(text);
            holo.setBillboard(Display.Billboard.CENTER);
            holo.setPersistent(true);
            hologram = holo;
        } else {
            ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
            stand.setCustomName(text);
            stand.setCustomNameVisible(true);
            stand.setGravity(false);
            stand.setInvisible(true);
            stand.setMarker(true);
            hologram = stand;
        }

        spawnedHolograms.add(hologram);
    }
}
