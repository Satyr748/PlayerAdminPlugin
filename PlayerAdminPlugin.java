package de.example.playeradmin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerAdminPlugin extends JavaPlugin implements Listener, TabExecutor {
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Set<UUID> invulnerable = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> menuTargets = new HashMap<>();
    private final Map<UUID, RenameRequest> renameRequests = new ConcurrentHashMap<>();
    private final Map<UUID, String> originalDisplayNames = new HashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private NamespacedKey actionKey;
    private NamespacedKey targetKey;

    private record RenameRequest(UUID targetId, boolean tabList) {}

    @Override
    public void onEnable() {
        actionKey = new NamespacedKey(this, "action");
        targetKey = new NamespacedKey(this, "target");
        Objects.requireNonNull(getCommand("player")).setExecutor(this);
        Objects.requireNonNull(getCommand("player")).setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("PlayerAdminPlugin aktiviert.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("Dieser Befehl kann nur im Spiel verwendet werden.");
            return true;
        }
        if (!admin.hasPermission("playeradmin.use")) {
            admin.sendMessage(Component.text("Dafür hast du keine Berechtigung.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            admin.sendMessage(Component.text("Verwendung: /player <Name>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            admin.sendMessage(Component.text("Dieser Spieler ist nicht online.", NamedTextColor.RED));
            return true;
        }
        openMenu(admin, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    private void openMenu(Player admin, Player target) {
        menuTargets.put(admin.getUniqueId(), target.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Spieler: " + target.getName(), NamedTextColor.DARK_AQUA));

        inv.setItem(10, item(Material.PLAYER_HEAD, "&bSkin ändern",
                "&7Skin von einem anderen Online-Spieler",
                "&7übernehmen (danach ggf. neu verbinden).", "skin"));
        inv.setItem(11, item(Material.NAME_TAG, "&eSichtbaren Namen ändern",
                "&7Ändert den Namen über dem Spieler.", "rename"));
        inv.setItem(12, item(Material.WRITABLE_BOOK, "&6Tablist-Namen ändern",
                "&7Ändert den Namen in der Spielerliste.", "tabname"));
        inv.setItem(14, item(invulnerable.contains(target.getUniqueId()) ? Material.SHIELD : Material.IRON_CHESTPLATE,
                invulnerable.contains(target.getUniqueId()) ? "&aUnsterblichkeit: AN" : "&cUnsterblichkeit: AUS",
                "&7Klicken zum Umschalten.", "invulnerable"));
        inv.setItem(15, item(frozen.contains(target.getUniqueId()) ? Material.BLUE_ICE : Material.PACKED_ICE,
                frozen.contains(target.getUniqueId()) ? "&bEingefroren: JA" : "&fEinfrieren",
                "&7Klicken zum Ein-/Auftauen.", "freeze"));
        inv.setItem(22, item(Material.BARRIER, "&cSchließen", "&7Menü schließen.", "close"));
        admin.openInventory(inv);
    }

    private ItemStack item(Material material, String title, String lore, String action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(legacy.deserialize(title));
        meta.lore(Arrays.stream(lore.split("\\n")).map(legacy::deserialize).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (!event.getView().title().toString().contains("Spieler:")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        UUID targetId = menuTargets.get(admin.getUniqueId());
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (action.equals("close")) {
            admin.closeInventory();
            return;
        }
        if (target == null) {
            admin.closeInventory();
            admin.sendMessage(Component.text("Der Zielspieler ist nicht mehr online.", NamedTextColor.RED));
            return;
        }

        switch (action) {
            case "invulnerable" -> {
                if (!invulnerable.add(target.getUniqueId())) invulnerable.remove(target.getUniqueId());
                target.setInvulnerable(invulnerable.contains(target.getUniqueId()));
                admin.sendMessage(Component.text("Unsterblichkeit für " + target.getName() + ": "
                        + (target.isInvulnerable() ? "AN" : "AUS"), NamedTextColor.GREEN));
                openMenu(admin, target);
            }
            case "freeze" -> {
                if (frozen.add(target.getUniqueId())) {
                    target.sendMessage(legacy.deserialize("&bDu wurdest eingefroren."));
                    admin.sendMessage(Component.text(target.getName() + " wurde eingefroren.", NamedTextColor.AQUA));
                } else {
                    frozen.remove(target.getUniqueId());
                    target.sendMessage(legacy.deserialize("&aDu bist nicht mehr eingefroren."));
                    admin.sendMessage(Component.text(target.getName() + " wurde aufgetaut.", NamedTextColor.GREEN));
                }
                openMenu(admin, target);
            }
            case "rename" -> beginRename(admin, target, false);
            case "tabname" -> beginRename(admin, target, true);
            case "skin" -> beginSkinChange(admin, target);
            default -> { }
        }
    }

    private void beginRename(Player admin, Player target, boolean tabList) {
        admin.closeInventory();
        renameRequests.put(admin.getUniqueId(), new RenameRequest(target.getUniqueId(), tabList));
        admin.sendMessage(Component.text(
                "Schreibe jetzt den neuen " + (tabList ? "Tablist-Namen" : "sichtbaren Namen") +
                        " in den Chat. Schreibe -cancel zum Abbrechen.", NamedTextColor.YELLOW));
    }

    private void beginSkinChange(Player admin, Player target) {
        admin.closeInventory();
        renameRequests.put(admin.getUniqueId(), new RenameRequest(target.getUniqueId(), false));
        admin.sendMessage(Component.text("Schreibe den Minecraft-Spielernamen, dessen Skin übernommen werden soll. "
                + "Schreibe -cancel zum Abbrechen.", NamedTextColor.YELLOW));
        skinRequests.add(admin.getUniqueId());
    }

    private final Set<UUID> skinRequests = ConcurrentHashMap.newKeySet();

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        UUID adminId = admin.getUniqueId();
        RenameRequest request = renameRequests.remove(adminId);
        if (request == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        if (input.equalsIgnoreCase("-cancel")) {
            skinRequests.remove(adminId);
            admin.sendMessage(Component.text("Abgebrochen.", NamedTextColor.YELLOW));
            return;
        }
        Player target = Bukkit.getPlayer(request.targetId());
        if (target == null) {
            skinRequests.remove(adminId);
            admin.sendMessage(Component.text("Der Zielspieler ist nicht mehr online.", NamedTextColor.RED));
            return;
        }

        if (skinRequests.remove(adminId)) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> applySkin(admin, target, input));
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            String safeName = input.length() > 32 ? input.substring(0, 32) : input;
            if (request.tabList()) {
                target.playerListName(legacy.deserialize(safeName));
            } else {
                target.displayName(safeName);
                target.customName(safeName);
                target.setCustomNameVisible(true);
            }
            admin.sendMessage(Component.text("Name aktualisiert. Hinweis: Der echte Accountname bleibt unverändert.",
                    NamedTextColor.GREEN));
        });
    }

    private void applySkin(Player admin, Player target, String sourceName) {
        try {
            Player source = Bukkit.getPlayerExact(sourceName);
            PlayerProfile sourceProfile;
            if (source != null) {
                sourceProfile = source.getPlayerProfile();
            } else {
                // Resolve a cached/offline profile through the Paper profile API.
                sourceProfile = Bukkit.createPlayerProfile(sourceName).update().join();
            }
            PlayerProfile targetProfile = target.getPlayerProfile();
            PlayerTextures textures = sourceProfile.getTextures();
            targetProfile.setTextures(textures);
            Bukkit.getScheduler().runTask(this, () -> {
                if (!target.isOnline()) {
                    admin.sendMessage(Component.text("Zielspieler ist nicht mehr online.", NamedTextColor.RED));
                    return;
                }
                target.setPlayerProfile(targetProfile);
                admin.sendMessage(Component.text("Skin-Profil wurde gesetzt. Der Spieler muss sich eventuell neu verbinden, "
                        + "damit der Skin überall sichtbar wird.", NamedTextColor.GREEN));
            });
        } catch (Exception ex) {
            getLogger().warning("Skin konnte nicht für " + target.getName() + " gesetzt werden: " + ex.getMessage());
            Bukkit.getScheduler().runTask(this, () ->
                    admin.sendMessage(Component.text("Skin konnte nicht geladen werden. Prüfe den Spielernamen und die Serververbindung.",
                            NamedTextColor.RED)));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!frozen.contains(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && invulnerable.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        frozen.remove(id);
        invulnerable.remove(id);
        renameRequests.remove(id);
        skinRequests.remove(id);
        menuTargets.values().removeIf(targetId -> targetId.equals(id));
    }
}
