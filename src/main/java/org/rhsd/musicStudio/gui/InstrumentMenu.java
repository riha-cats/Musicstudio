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
// 악기를 전부 늘어놓고 선택 시 해당 레이어 악기를 바꾼다. 텍스트는 GuiConfig 에서
// 앞칸부터 순서대로 채우므로 Instrument 가 27개를 넘으면 안 된다 (InstrumentMenuLayoutTest 가 지킨다)
public final class InstrumentMenu implements MsHolder {

    public static final int SIZE = 27;

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
        Inventory inv = Bukkit.createInventory(holder, SIZE,
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
        String label = gui.instrumentName(instrument);
        ItemStack it = new ItemStack(instrument.icon());
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(gui.name(base + ".name", "instrument", label));
            meta.lore(gui.lore(base + ".lore", "instrument", label));
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
