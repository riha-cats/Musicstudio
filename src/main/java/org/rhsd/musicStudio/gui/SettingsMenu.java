package org.rhsd.musicStudio.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.rhsd.musicStudio.GuiConfig;
import org.rhsd.musicStudio.model.Song;

import java.util.List;

// =================================================================
// 곡 설정 메뉴 (27칸)
// =================================================================
// 템포 조절, 음반 추출, 에디터 복귀. 텍스트는 GuiConfig 에서
public final class SettingsMenu implements MsHolder {

    public static final int SLOT_TEMPO_DOWN = 10;
    public static final int SLOT_TEMPO_INFO = 13;
    public static final int SLOT_TEMPO_UP   = 16;
    public static final int SLOT_DISC       = 22;
    public static final int SLOT_BACK       = 26;

    private static final Material MAT_TEMPO_DOWN = Material.RED_DYE;
    private static final Material MAT_TEMPO_INFO = Material.CLOCK;
    private static final Material MAT_TEMPO_UP   = Material.LIME_DYE;
    private static final Material MAT_DISC       = Material.MUSIC_DISC_5;
    private static final Material MAT_BACK       = Material.OAK_DOOR;

    private final String songId;
    private Inventory inventory;

    private SettingsMenu(String songId) {
        this.songId = songId;
    }

    public String songId() {
        return songId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static Inventory build(Song song, GuiConfig gui) {
        SettingsMenu holder = new SettingsMenu(song.id());
        Inventory inv = Bukkit.createInventory(holder, 27,
                gui.title("settings.title", "song", song.name()));
        holder.inventory = inv;
        render(inv, song, gui);
        return inv;
    }

    public static void render(Inventory inv, Song song, GuiConfig gui) {
        inv.clear();
        String cellsPerSec = String.format("%.1f", 20.0 / song.ticksPerCell());
        String[] ph = {
                "tempo", String.valueOf(song.ticksPerCell()),
                "max", String.valueOf(Song.MAX_TICKS_PER_CELL),
                "min", String.valueOf(Song.MIN_TICKS_PER_CELL),
                "cells_per_sec", cellsPerSec
        };
        inv.setItem(SLOT_TEMPO_DOWN, item(MAT_TEMPO_DOWN, gui, "settings.tempo-down", ph));
        inv.setItem(SLOT_TEMPO_INFO, item(MAT_TEMPO_INFO, gui, "settings.tempo-info", ph));
        inv.setItem(SLOT_TEMPO_UP, item(MAT_TEMPO_UP, gui, "settings.tempo-up", ph));
        inv.setItem(SLOT_DISC, item(MAT_DISC, gui, "settings.disc", ph));
        inv.setItem(SLOT_BACK, item(MAT_BACK, gui, "settings.back", ph));
    }

    private static ItemStack item(Material material, GuiConfig gui, String base, String[] ph) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(gui.name(base + ".name", ph));
            List<net.kyori.adventure.text.Component> lore = gui.lore(base + ".lore", ph);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            it.setItemMeta(meta);
        }
        return it;
    }
}

// 컴플리트
