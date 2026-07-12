package org.rhsd.musicStudio.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.rhsd.musicStudio.GuiConfig;
import org.rhsd.musicStudio.compat.ItemCompat;
import org.rhsd.musicStudio.model.Layer;
import org.rhsd.musicStudio.model.Note;
import org.rhsd.musicStudio.model.Song;

import java.util.List;

// =================================================================
// 에디터 렌더러
// =================================================================
// 곡 + 세션 상태를 54칸 트래커 인벤토리로 그린다
// 레이아웃 (6행x9열) :: 행0~3 = 레이어 헤더 + 8틱 그리드, 행4 = 정보 + 눈금, 행5 = 컨트롤 바
// 모든 텍스트는 GuiConfig 에서 가져오고, 아이콘 Material 만 코드 상수다
public final class EditorRenderer {

    public static final int SIZE = 54;
    public static final int GRID_COL_START = 1;
    public static final int RULER_ROW = 4;
    public static final int CONTROL_ROW = 5;

    public static final int SLOT_PREV_TICK   = 45;
    public static final int SLOT_NEXT_TICK   = 46;
    public static final int SLOT_UP_LAYER    = 47;
    public static final int SLOT_DOWN_LAYER  = 48;
    public static final int SLOT_PLAY        = 49;
    public static final int SLOT_STOP        = 50;
    public static final int SLOT_SETTINGS    = 51;
    public static final int SLOT_COPY        = 52;
    public static final int SLOT_PASTE       = 53;
    public static final int SLOT_INFO        = 36;

    // 컨트롤/장식 아이콘 Material. 텍스트는 GuiConfig 가, 아이콘은 여기서 관리한다
    private static final Material MAT_PREV         = Material.SPECTRAL_ARROW;
    private static final Material MAT_NEXT         = Material.ARROW;
    private static final Material MAT_LAYER        = Material.FEATHER;
    private static final Material MAT_PLAY         = Material.LIME_DYE;
    private static final Material MAT_STOP         = Material.RED_DYE;
    private static final Material MAT_SETTINGS     = Material.COMPARATOR;
    private static final Material MAT_COPY         = Material.BOOK;
    private static final Material MAT_PASTE        = Material.WRITABLE_BOOK;
    private static final Material MAT_EMPTY        = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material MAT_ADD          = Material.LIME_STAINED_GLASS_PANE;
    private static final Material MAT_FILLER       = Material.BLACK_STAINED_GLASS_PANE;
    private static final Material MAT_INFO         = Material.JUKEBOX;
    private static final Material MAT_RULER        = Material.PAPER;
    private static final Material MAT_RULER_ON     = Material.LIME_DYE;
    private static final Material MAT_RULER_CURSOR = Material.YELLOW_DYE;

    private EditorRenderer() {
    }

    public static Inventory build(EditorSession session, GuiConfig gui) {
        Song song = session.song();
        EditorHolder holder = new EditorHolder(song.id());
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                gui.title("editor.title", "song", song.name()));
        holder.setInventory(inv);
        render(inv, session, gui);
        return inv;
    }

    public static void render(Inventory inv, EditorSession session, GuiConfig gui) {
        inv.clear();
        Song song = session.song();
        int tickOffset = session.tickOffset();
        int layerOffset = session.layerOffset();

        // [1] :: 레이어 행 (행0~3) 그리기
        for (int row = 0; row < EditorSession.VISIBLE_LAYERS; row++) {
            int layerIdx = layerOffset + row;
            int base = row * 9;
            // [A] :: 존재하는 레이어인가? 헤더 + 그리드 셀
            if (layerIdx < song.layerCount()) {
                Layer layer = song.layer(layerIdx);
                inv.setItem(base, headerItem(gui, layerIdx, layer, song.layerCount()));
                for (int c = 0; c < EditorSession.VISIBLE_TICKS; c++) {
                    int tick = tickOffset + c;
                    int slot = base + GRID_COL_START + c;
                    Note note = song.getNote(tick, layerIdx);
                    if (note != null) {
                        inv.setItem(slot, noteItem(gui, layer, note, session.isSelected(tick, layerIdx)));
                    } else {
                        inv.setItem(slot, emptyCell(gui, session, tick, layerIdx));
                    }
                }
            }
            // [B] :: 다음 레이어 자리이고 상한 미달인가? 레이어 추가 버튼
            else if (layerIdx == song.layerCount() && song.layerCount() < session.maxLayers()) {
                inv.setItem(base, item(MAT_ADD, gui.name("editor.add-layer.name"),
                        gui.lore("editor.add-layer.lore")));
                fillBlocked(inv, gui, base);
            }
            // [C] :: 그 아무것도 아니라면? 채움판
            else {
                inv.setItem(base, item(MAT_FILLER, gui.name("editor.filler.name"), null));
                fillBlocked(inv, gui, base);
            }
        }

        // [2] :: 정보 + 눈금 행 (행4)
        inv.setItem(SLOT_INFO, infoItem(gui, song, session));
        renderRuler(inv, session, gui);

        // [3] :: 컨트롤 바 (행5)
        button(inv, SLOT_PREV_TICK, MAT_PREV, gui, "editor.buttons.prev-tick");
        button(inv, SLOT_NEXT_TICK, MAT_NEXT, gui, "editor.buttons.next-tick");
        button(inv, SLOT_UP_LAYER, MAT_LAYER, gui, "editor.buttons.up-layer");
        button(inv, SLOT_DOWN_LAYER, MAT_LAYER, gui, "editor.buttons.down-layer");
        button(inv, SLOT_PLAY, MAT_PLAY, gui, "editor.buttons.play");
        button(inv, SLOT_STOP, MAT_STOP, gui, "editor.buttons.stop");
        button(inv, SLOT_SETTINGS, MAT_SETTINGS, gui, "editor.buttons.settings");
        button(inv, SLOT_COPY, MAT_COPY, gui, "editor.buttons.copy");
        button(inv, SLOT_PASTE, MAT_PASTE, gui, "editor.buttons.paste");
        // [STOP] :: 렌더 끝
    }

    // 눈금 행만 갱신 (재생 헤드/커서 이동, 스크롤 미변경 시)
    public static void renderRuler(Inventory inv, EditorSession session, GuiConfig gui) {
        int tickOffset = session.tickOffset();
        for (int c = 0; c < EditorSession.VISIBLE_TICKS; c++) {
            int tick = tickOffset + c;
            inv.setItem(RULER_ROW * 9 + GRID_COL_START + c,
                    rulerItem(gui, tick, session.playingTick() == tick, session.cursorTick() == tick));
        }
    }

    private static void fillBlocked(Inventory inv, GuiConfig gui, int base) {
        ItemStack filler = item(MAT_FILLER, gui.name("editor.filler.name"), null);
        for (int c = 0; c < EditorSession.VISIBLE_TICKS; c++) {
            inv.setItem(base + GRID_COL_START + c, filler);
        }
    }

    // =================================================================
    // 아이템 빌더
    // =================================================================

    private static ItemStack headerItem(GuiConfig gui, int layerIdx, Layer layer, int total) {
        String[] ph = {
                "n", String.valueOf(layerIdx + 1),
                "layer_name", layer.name(),
                "instrument", layer.instrument().displayName(),
                "volume", String.valueOf(Math.round(layer.volume() * 100)),
                "muted", layer.muted() ? " (음소거)" : "",
                "total", String.valueOf(total)
        };
        Component name = gui.name(layer.muted() ? "editor.header.name-muted" : "editor.header.name", ph);
        return item(layer.instrument().icon(), name, gui.lore("editor.header.lore", ph));
    }

    private static ItemStack noteItem(GuiConfig gui, Layer layer, Note note, boolean selected) {
        String[] ph = {
                "key_name", note.keyName(),
                "key", String.valueOf(note.key()),
                "tick", String.valueOf(note.tick()),
                "layer", String.valueOf(note.layer() + 1)
        };
        Component name = gui.name(selected ? "editor.note.name-selected" : "editor.note.name", ph);
        ItemStack it = item(layer.instrument().icon(), name, gui.lore("editor.note.lore", ph));
        // 선택된 노트라면? 글린트로 강조
        if (selected) {
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                ItemCompat.setGlint(meta, true);
                it.setItemMeta(meta);
            }
        }
        return it;
    }

    private static ItemStack emptyCell(GuiConfig gui, EditorSession session, int tick, int layer) {
        String[] ph = {
                "tick", String.valueOf(tick),
                "layer", String.valueOf(layer + 1),
                "key_name", Note.keyName(session.currentKey())
        };
        return item(MAT_EMPTY, gui.name("editor.empty-cell.name", ph), gui.lore("editor.empty-cell.lore", ph));
    }

    private static ItemStack infoItem(GuiConfig gui, Song song, EditorSession session) {
        String[] ph = {
                "song", song.name(),
                "tempo", String.valueOf(song.ticksPerCell()),
                "length", String.valueOf(song.length()),
                "notes", String.valueOf(song.noteCount()),
                "layers", String.valueOf(song.layerCount()),
                "max_layers", String.valueOf(session.maxLayers()),
                "key_name", Note.keyName(session.currentKey())
        };
        return item(MAT_INFO, gui.name("editor.info.name", ph), gui.lore("editor.info.lore", ph));
    }

    private static ItemStack rulerItem(GuiConfig gui, int tick, boolean playing, boolean cursor) {
        String tickStr = String.valueOf(tick);
        // 재생 헤드 표시가 커서 표시보다 우선
        if (playing) {
            return item(MAT_RULER_ON, gui.name("editor.ruler-playing.name", "tick", tickStr), null);
        }
        String base = cursor ? "editor.ruler-cursor" : "editor.ruler";
        return item(cursor ? MAT_RULER_CURSOR : MAT_RULER,
                gui.name(base + ".name", "tick", tickStr), gui.lore(base + ".lore", "tick", tickStr));
    }

    private static void button(Inventory inv, int slot, Material material, GuiConfig gui, String basePath) {
        inv.setItem(slot, item(material, gui.name(basePath + ".name"), gui.lore(basePath + ".lore")));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            it.setItemMeta(meta);
        }
        return it;
    }
}

// 컴플리트
