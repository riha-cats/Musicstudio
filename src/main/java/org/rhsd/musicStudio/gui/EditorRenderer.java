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
import java.util.NavigableSet;

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
    public static final int SLOT_COPY        = 46;
    public static final int SLOT_PASTE       = 47;
    public static final int SLOT_SETTINGS    = 48;
    public static final int SLOT_PLAY        = 49;
    public static final int SLOT_OUTPUT      = 50;
    public static final int SLOT_UP_LAYER    = 51;
    public static final int SLOT_DOWN_LAYER  = 52;
    public static final int SLOT_NEXT_TICK   = 53;
    public static final int SLOT_INFO        = 36;

    // 컨트롤/장식 아이콘 Material. 텍스트는 GuiConfig 가, 아이콘은 여기서 관리한다
    private static final Material MAT_PREV         = Material.SPECTRAL_ARROW;
    private static final Material MAT_NEXT         = Material.ARROW;
    private static final Material MAT_COPY         = Material.WRITTEN_BOOK;
    private static final Material MAT_PASTE        = Material.BOOK;
    private static final Material MAT_SETTINGS     = Material.REPEATER;
    private static final Material MAT_OUTPUT       = Material.MUSIC_DISC_11;
    private static final Material MAT_LAYER        = Material.PAPER;
    private static final Material MAT_PLAY         = Material.GREEN_BANNER;
    private static final Material MAT_STOP         = Material.RED_BANNER;
    private static final Material MAT_EMPTY        = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material MAT_ADD          = Material.LIME_STAINED_GLASS_PANE;
    private static final Material MAT_FILLER       = Material.BLACK_STAINED_GLASS_PANE;
    private static final Material MAT_INFO         = Material.JUKEBOX;
    // 틱 눈금 배너 5색 :: 흰=미선택, 노랑=선택됨(복사 대상), 파랑=붙여넣을 자리,
    //                     주황=대기 중 시작점, 연두=재생 헤드
    private static final Material MAT_TICK          = Material.WHITE_BANNER;
    private static final Material MAT_TICK_SELECTED = Material.YELLOW_BANNER;
    private static final Material MAT_TICK_PASTE    = Material.LIGHT_BLUE_BANNER;
    private static final Material MAT_TICK_ANCHOR   = Material.ORANGE_BANNER;
    private static final Material MAT_TICK_PLAYING  = Material.LIME_BANNER;

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
                inv.setItem(base, headerItem(gui, layerIdx, layer, song.layerCount(), session.isMovingLayer(layerIdx)));
                for (int c = 0; c < EditorSession.VISIBLE_TICKS; c++) {
                    int tick = tickOffset + c;
                    int slot = base + GRID_COL_START + c;
                    Note note = song.getNote(tick, layerIdx);
                    if (note != null) {
                        inv.setItem(slot, noteItem(gui, layer, note, session.isSelected(tick, layerIdx),
                                session.isCellSelected(tick, layerIdx)));
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
        button(inv, SLOT_COPY, MAT_COPY, gui, "editor.buttons.copy", session);
        button(inv, SLOT_PASTE, MAT_PASTE, gui, "editor.buttons.paste", session);
        button(inv, SLOT_SETTINGS, MAT_SETTINGS, gui, "editor.buttons.settings");
        // 재생 버튼은 토글이다. 재생 중이면 빨간 배너(정지)로 바뀌어 현재 상태를 겸해 보여준다
        boolean playing = session.playingTick() >= 0;
        button(inv, SLOT_PLAY, playing ? MAT_STOP : MAT_PLAY, gui,
                playing ? "editor.buttons.stop" : "editor.buttons.play");
        button(inv, SLOT_OUTPUT, MAT_OUTPUT, gui, "editor.buttons.output");
        button(inv, SLOT_UP_LAYER, MAT_LAYER, gui, "editor.buttons.up-layer");
        button(inv, SLOT_DOWN_LAYER, MAT_LAYER, gui, "editor.buttons.down-layer");
        button(inv, SLOT_NEXT_TICK, MAT_NEXT, gui, "editor.buttons.next-tick");
        // [STOP] :: 렌더 끝
    }

    // 눈금 행만 갱신 (재생 헤드 이동, 스크롤 미변경 시)
    public static void renderRuler(Inventory inv, EditorSession session, GuiConfig gui) {
        int tickOffset = session.tickOffset();
        // 선택 틱 집합은 매번 새로 만들어지니 루프 밖에서 한 번만 뽑는다
        NavigableSet<Integer> selected = session.selectedTicks();
        boolean hasAnchor = session.hasRangeAnchor();
        int anchorTick = hasAnchor ? session.rangeAnchorTick() : -1;
        for (int c = 0; c < EditorSession.VISIBLE_TICKS; c++) {
            int tick = tickOffset + c;
            inv.setItem(RULER_ROW * 9 + GRID_COL_START + c,
                    rulerItem(gui, tick, session.playingTick() == tick,
                            hasAnchor && anchorTick == tick,
                            session.pasteTick() == tick, selected.contains(tick)));
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

    private static ItemStack headerItem(GuiConfig gui, int layerIdx, Layer layer, int total, boolean moving) {
        String[] ph = {
                "n", String.valueOf(layerIdx + 1),
                "layer_name", layer.name(),
                "instrument", layer.instrument().displayName(),
                "volume", String.valueOf(Math.round(layer.volume() * 100)),
                "muted", layer.muted() ? " (음소거)" : "",
                "total", String.valueOf(total)
        };
        Component name = gui.name(moving ? "editor.header.name-moving"
                : layer.muted() ? "editor.header.name-muted" : "editor.header.name", ph);
        ItemStack result = item(layer.instrument().icon(), name,
                gui.lore(moving ? "editor.header.lore-moving" : "editor.header.lore", ph));
        if (moving) setGlint(result);
        return result;
    }

    private static ItemStack noteItem(GuiConfig gui, Layer layer, Note note, boolean selected, boolean tickSelected) {
        String[] ph = {
                "key_name", note.keyName(),
                "key", String.valueOf(note.key()),
                "tick", String.valueOf(note.tick()),
                "layer", String.valueOf(note.layer() + 1)
        };
        Component name = gui.name(selected ? "editor.note.name-selected" : "editor.note.name", ph);
        ItemStack it = item(layer.instrument().icon(), name, gui.lore("editor.note.lore", ph));
        // 선택된 노트라면? 글린트로 강조
        if (selected || tickSelected) {
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
        ItemStack it = item(MAT_EMPTY, gui.name("editor.empty-cell.name", ph), gui.lore("editor.empty-cell.lore", ph));
        if (session.isCellSelected(tick, layer)) setGlint(it);
        return it;
    }

    private static ItemStack infoItem(GuiConfig gui, Song song, EditorSession session) {
        String[] ph = {
                "song", song.name(),
                "tempo", String.valueOf(song.ticksPerCell()),
                "length", String.valueOf(song.length()),
                "notes", String.valueOf(song.noteCount()),
                "layers", String.valueOf(song.layerCount()),
                "max_layers", String.valueOf(session.maxLayers()),
                "key_name", Note.keyName(session.currentKey()),
                "selected_ticks", String.valueOf(session.selectedTicks().size()),
                "anchor_tick", session.hasRangeAnchor() ? String.valueOf(session.rangeAnchorTick()) : "-"
        };
        return item(MAT_INFO, gui.name("editor.info.name", ph), gui.lore("editor.info.lore", ph));
    }

    // 우선순위 :: 재생 헤드 > 대기 중 시작점 > 붙여넣을 자리 > 선택됨 > 미선택
    // (붙여넣을 자리가 선택보다 위다. 선택된 틱에 커서를 놓아도 커서가 보여야 하기 때문)
    private static ItemStack rulerItem(GuiConfig gui, int tick, boolean playing, boolean anchor,
                                       boolean pasteTarget, boolean selected) {
        String tickStr = String.valueOf(tick);
        if (playing) {
            return item(MAT_TICK_PLAYING, gui.name("editor.ruler-playing.name", "tick", tickStr), null);
        }
        // [1] :: 끝점을 아직 안 찍은 시작점인가? 주황 + 글린트로 대기 중임을 알린다
        if (anchor) {
            ItemStack it = item(MAT_TICK_ANCHOR, gui.name("editor.ruler-anchor.name", "tick", tickStr),
                    gui.lore("editor.ruler-anchor.lore", "tick", tickStr));
            setGlint(it);
            return it;
        }
        // [2] :: 붙여넣기 버튼이 겨냥하는 자리인가?
        if (pasteTarget) {
            return item(MAT_TICK_PASTE, gui.name("editor.ruler-paste.name", "tick", tickStr),
                    gui.lore("editor.ruler-paste.lore", "tick", tickStr));
        }
        String base = selected ? "editor.ruler-selected" : "editor.ruler";
        return item(selected ? MAT_TICK_SELECTED : MAT_TICK,
                gui.name(base + ".name", "tick", tickStr), gui.lore(base + ".lore", "tick", tickStr));
    }

    private static void button(Inventory inv, int slot, Material material, GuiConfig gui, String basePath) {
        inv.setItem(slot, item(material, gui.name(basePath + ".name"), gui.lore(basePath + ".lore")));
    }

    private static void button(Inventory inv, int slot, Material material, GuiConfig gui,
                               String basePath, EditorSession session) {
        String[] ph = {"selected_ticks", String.valueOf(session.selectedTicks().size()),
                "anchor_tick", session.hasRangeAnchor() ? String.valueOf(session.rangeAnchorTick()) : "-",
                "paste_tick", session.hasPasteTick() ? String.valueOf(session.pasteTick()) : "-"};
        inv.setItem(slot, item(material, gui.name(basePath + ".name", ph), gui.lore(basePath + ".lore", ph)));
    }

    private static void setGlint(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { ItemCompat.setGlint(meta, true); it.setItemMeta(meta); }
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
