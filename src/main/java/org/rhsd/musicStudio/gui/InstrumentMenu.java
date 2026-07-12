package org.rhsd.musicStudio.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.rhsd.musicStudio.GuiConfig;
import org.rhsd.musicStudio.compat.ItemCompat;
import org.rhsd.musicStudio.model.Instrument;
import org.rhsd.musicStudio.model.Song;

// =================================================================
// 악기 선택 메뉴 (27칸)
// =================================================================
// 16악기를 보여주고 선택 시 해당 레이어 악기를 바꾼다. 텍스트는 GuiConfig 에서
public final class InstrumentMenu implements MsHolder {

    private final String songId;
    private final int layerIndex;
    private Inventory inventory;

    private InstrumentMenu(String songId, int layerIndex) {
        this.songId = songId;
        this.layerIndex = layerIndex;
    }

    public String songId() {
        return songId;
    }

    public int layerIndex() {
        return layerIndex;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static Inventory build(Song song, int layerIndex, GuiConfig gui) {
        InstrumentMenu holder = new InstrumentMenu(song.id(), layerIndex);
        Inventory inv = Bukkit.createInventory(holder, 27,
                gui.title("instrument.title", "n", String.valueOf(layerIndex + 1)));
        holder.inventory = inv;

        Instrument current = song.layer(layerIndex) != null ? song.layer(layerIndex).instrument() : null;
        Instrument[] values = Instrument.values();
        for (int i = 0; i < values.length; i++) {
            inv.setItem(i, instrumentItem(gui, values[i], values[i] == current));
        }
        return inv;
    }

    private static ItemStack instrumentItem(GuiConfig gui, Instrument instrument, boolean current) {
        String base = current ? "instrument.entry-current" : "instrument.entry";
        ItemStack it = new ItemStack(instrument.icon());
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(gui.name(base + ".name", "instrument", instrument.displayName()));
            meta.lore(gui.lore(base + ".lore", "instrument", instrument.displayName()));
            // 현재 사용 중인 악기라면? 글린트로 강조
            if (current) {
                ItemCompat.setGlint(meta, true);
            }
            it.setItemMeta(meta);
        }
        return it;
    }
}

// 컴플리트
