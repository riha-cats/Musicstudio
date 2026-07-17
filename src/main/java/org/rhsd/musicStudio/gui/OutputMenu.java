package org.rhsd.musicStudio.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.rhsd.musicStudio.GuiConfig;
import org.rhsd.musicStudio.compat.ItemCompat;
import org.rhsd.musicStudio.disc.DiscManager;
import org.rhsd.musicStudio.model.Song;

import java.util.List;

// =================================================================
// 음반 추출 메뉴 (27칸)
// =================================================================
// 레이아웃 (3행x9열) :: 행1 에만 버튼이 있고 나머지는 검은 판으로 채운다
// 가격(10), 결제(13~15), 돌아가기(16)
// 결제 버튼을 3칸으로 넓게 둔 건 실수로 스쳐 눌리는 대신 명확히 겨냥하게 하려는 것
public final class OutputMenu implements MsHolder {

    public static final int SLOT_PRICE       = 10;
    public static final int SLOT_BUY_START   = 13;
    public static final int SLOT_BUY_END     = 15;
    public static final int SLOT_BACK        = 16;

    private static final int SIZE = 27;

    private static final Material MAT_PRICE  = Material.GOLD_BLOCK;
    private static final Material MAT_BUY    = Material.LIME_STAINED_GLASS_PANE;
    private static final Material MAT_BACK   = Material.ARROW;
    private static final Material MAT_FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private final String songId;
    private Inventory inventory;

    private OutputMenu(String songId) {
        this.songId = songId;
    }

    public String songId() {
        return songId;
    }

    // 결제 버튼 칸인가? 13~15 셋 다 같은 동작이다
    public static boolean isBuySlot(int slot) {
        return slot >= SLOT_BUY_START && slot <= SLOT_BUY_END;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static Inventory build(Song song, GuiConfig gui, DiscManager discs) {
        OutputMenu holder = new OutputMenu(song.id());
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                gui.title("output.title", "song", song.name()));
        holder.inventory = inv;
        render(inv, song, gui, discs);
        return inv;
    }

    public static void render(Inventory inv, Song song, GuiConfig gui, DiscManager discs) {
        inv.clear();
        String[] ph = {
                "song", song.name(),
                "cost", discs.costLabel(),
                "length", String.valueOf(song.length()),
                "notes", String.valueOf(song.noteCount()),
                "layers", String.valueOf(song.layerCount())
        };
        // [1] :: 기능 없는 칸을 먼저 검은 판으로 덮는다
        ItemStack filler = item(MAT_FILLER, gui, "output.filler", ph);
        for (int slot = 0; slot < SIZE; slot++) {
            inv.setItem(slot, filler);
        }
        // [2] :: 비용을 끈 서버라면? 가격 대신 "무료"를 띄운다
        String priceKey = discs.isCostEnabled() ? "output.price" : "output.price-free";
        inv.setItem(SLOT_PRICE, item(MAT_PRICE, gui, priceKey, ph));
        ItemStack buy = item(MAT_BUY, gui, "output.buy", ph);
        for (int slot = SLOT_BUY_START; slot <= SLOT_BUY_END; slot++) {
            inv.setItem(slot, buy);
        }
        inv.setItem(SLOT_BACK, item(MAT_BACK, gui, "output.back", ph));
        // [STOP] :: 렌더 끝
    }

    private static ItemStack item(Material material, GuiConfig gui, String base, String[] ph) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(gui.name(base + ".name", ph));
            List<Component> lore = gui.lore(base + ".lore", ph);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            ItemCompat.hideExtraTooltip(meta);
            it.setItemMeta(meta);
        }
        return it;
    }
}

// 컴플리트
