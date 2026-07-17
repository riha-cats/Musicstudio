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
// 레이아웃 (3행x9열) :: 행1 에만 버튼이 있고 나머지는 검은 판으로 채운다
// 정보(10) · 느리게(13) · 빠르게(14) · 기본값(15) · 돌아가기(16)
// 음반 추출은 에디터 컨트롤 바의 Output 버튼으로 일원화했다
public final class SettingsMenu implements MsHolder {

    public static final int SLOT_INFO        = 10;
    public static final int SLOT_TEMPO_DOWN  = 13;
    public static final int SLOT_TEMPO_UP    = 14;
    public static final int SLOT_TEMPO_RESET = 15;
    public static final int SLOT_BACK        = 16;

    private static final int SIZE = 27;

    private static final Material MAT_INFO        = Material.JUKEBOX;
    private static final Material MAT_TEMPO_DOWN  = Material.RED_DYE;
    private static final Material MAT_TEMPO_UP    = Material.LIME_DYE;
    private static final Material MAT_TEMPO_RESET = Material.CLOCK;
    private static final Material MAT_BACK        = Material.ARROW;
    private static final Material MAT_FILLER      = Material.BLACK_STAINED_GLASS_PANE;

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
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                gui.title("settings.title", "song", song.name()));
        holder.inventory = inv;
        render(inv, song, gui);
        return inv;
    }

    public static void render(Inventory inv, Song song, GuiConfig gui) {
        inv.clear();
        String cellsPerSec = String.format("%.1f", 20.0 / song.ticksPerCell());
        String[] ph = {
                "song", song.name(),
                "tempo", String.valueOf(song.ticksPerCell()),
                "max", String.valueOf(Song.MAX_TICKS_PER_CELL),
                "min", String.valueOf(Song.MIN_TICKS_PER_CELL),
                "default", String.valueOf(Song.DEFAULT_TICKS_PER_CELL),
                "length", String.valueOf(song.length()),
                "notes", String.valueOf(song.noteCount()),
                "layers", String.valueOf(song.layerCount()),
                "cells_per_sec", cellsPerSec
        };
        // [1] :: 기능 없는 칸을 먼저 검은 판으로 덮는다. 버튼은 그 위에 얹는다
        ItemStack filler = item(MAT_FILLER, gui, "settings.filler", ph);
        for (int slot = 0; slot < SIZE; slot++) {
            inv.setItem(slot, filler);
        }
        inv.setItem(SLOT_INFO, item(MAT_INFO, gui, "settings.info", ph));
        inv.setItem(SLOT_TEMPO_DOWN, item(MAT_TEMPO_DOWN, gui, "settings.tempo-down", ph));
        inv.setItem(SLOT_TEMPO_UP, item(MAT_TEMPO_UP, gui, "settings.tempo-up", ph));
        inv.setItem(SLOT_TEMPO_RESET, item(MAT_TEMPO_RESET, gui, "settings.tempo-reset", ph));
        inv.setItem(SLOT_BACK, item(MAT_BACK, gui, "settings.back", ph));
        // [STOP] :: 렌더 끝
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
