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
import org.rhsd.musicStudio.model.Song;
import org.rhsd.musicStudio.storage.SongStorage;

import java.util.ArrayList;
import java.util.List;

// =================================================================
// 곡 목록 메뉴 (54칸)
// =================================================================
// 레이아웃 (6행x9열) :: 바깥 테두리는 검은 판, 안쪽 4행x7열(28칸)이 곡 목록
// 하단 테두리 가운데에 페이지 이동 :: 이전(48), 현재(49), 다음(50)
// 49 가 45~53 행의 정중앙이라 현재(시계)를 여기에 둔다
//
// 곡 목록은 열 때 한 번만 스냅샷해서 들고 있는다. 페이지를 넘길 때마다 저장소를
// 다시 훑지 않기 위함이고, 곡이 지워졌을 수 있으니 클릭 시점에 id 로 다시 조회한다
public final class SongListMenu implements MsHolder {

    public static final int SLOT_PREV_PAGE = 48;
    public static final int SLOT_PAGE_INFO = 49;
    public static final int SLOT_NEXT_PAGE = 50;

    private static final int SIZE = 54;
    private static final int ROWS = 6;
    private static final int COLS = 9;

    // 목록 칸 :: 테두리를 뺀 안쪽 (행1~4, 열1~7)
    public static final int[] CONTENT_SLOTS = contentSlots();
    public static final int PER_PAGE = CONTENT_SLOTS.length;

    private static final Material MAT_SONG   = Material.BOOK;
    private static final Material MAT_PREV   = Material.SPECTRAL_ARROW;
    private static final Material MAT_PAGE   = Material.CLOCK;
    private static final Material MAT_NEXT   = Material.ARROW;
    private static final Material MAT_BORDER = Material.BLACK_STAINED_GLASS_PANE;

    // 이 메뉴가 보여주는 곡 id 스냅샷. 곡 객체가 아니라 id 라야 삭제된 곡을 붙들지 않는다
    private final List<String> songIds;
    private int page;
    private Inventory inventory;

    private SongListMenu(List<String> songIds, int page) {
        this.songIds = songIds;
        this.page = page;
    }

    private static int[] contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * COLS + col);
            }
        }
        int[] out = new int[slots.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = slots.get(i);
        }
        return out;
    }

    public int page() {
        return page;
    }

    public int pageCount() {
        return Math.max(1, (songIds.size() + PER_PAGE - 1) / PER_PAGE);
    }

    // 슬롯이 목록 칸이라면 그 자리의 곡 id, 아니면 null.
    // 페이지 끝의 빈 칸을 눌러도 null 이 나와야 한다
    public String songIdAt(int slot) {
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == slot) {
                int index = page * PER_PAGE + i;
                return index < songIds.size() ? songIds.get(index) : null;
            }
        }
        return null;
    }

    // 페이지 이동. 범위를 벗어나면 움직이지 않고 false
    public boolean movePage(int delta) {
        int target = page + delta;
        if (target < 0 || target >= pageCount()) {
            return false;
        }
        page = target;
        return true;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static Inventory build(List<Song> songs, GuiConfig gui, SongStorage storage) {
        List<String> ids = new ArrayList<>(songs.size());
        for (Song song : songs) {
            ids.add(song.id());
        }
        SongListMenu holder = new SongListMenu(ids, 0);
        Inventory inv = Bukkit.createInventory(holder, SIZE, gui.title("song-list.title"));
        holder.inventory = inv;
        render(inv, holder, gui, storage);
        return inv;
    }

    public static void render(Inventory inv, SongListMenu menu, GuiConfig gui, SongStorage storage) {
        inv.clear();
        // [1] :: 바깥 테두리를 두른다
        ItemStack border = item(MAT_BORDER, gui.name("song-list.border.name"), null);
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / COLS;
            int col = slot % COLS;
            if (row == 0 || row == ROWS - 1 || col == 0 || col == COLS - 1) {
                inv.setItem(slot, border);
            }
        }

        // [2] :: 이 페이지의 곡을 안쪽에 채운다. 지워진 곡은 건너뛰지 않고 빈칸으로 둔다
        //        (인덱스가 밀리면 다른 곡을 클릭하게 되기 때문)
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int index = menu.page * PER_PAGE + i;
            if (index >= menu.songIds.size()) {
                break;
            }
            Song song = storage.getById(menu.songIds.get(index));
            if (song == null) {
                continue;
            }
            inv.setItem(CONTENT_SLOTS[i], songItem(gui, song, storage));
        }

        // [3] :: 페이지 이동. 끝 페이지에서는 화살표를 감춰 눌러도 안 되는 버튼을 안 보여준다
        String[] ph = {
                "page", String.valueOf(menu.page + 1),
                "pages", String.valueOf(menu.pageCount()),
                "total", String.valueOf(menu.songIds.size())
        };
        if (menu.page > 0) {
            inv.setItem(SLOT_PREV_PAGE, item(MAT_PREV, gui.name("song-list.prev-page.name", ph),
                    gui.lore("song-list.prev-page.lore", ph)));
        }
        inv.setItem(SLOT_PAGE_INFO, item(MAT_PAGE, gui.name("song-list.page-info.name", ph),
                gui.lore("song-list.page-info.lore", ph)));
        if (menu.page < menu.pageCount() - 1) {
            inv.setItem(SLOT_NEXT_PAGE, item(MAT_NEXT, gui.name("song-list.next-page.name", ph),
                    gui.lore("song-list.next-page.lore", ph)));
        }
        // [STOP] :: 렌더 끝
    }

    private static ItemStack songItem(GuiConfig gui, Song song, SongStorage storage) {
        String[] ph = {
                "song", song.name(),
                "size", formatSize(storage.fileSize(song.id())),
                "notes", String.format("%,d", song.noteCount()),
                "layers", String.valueOf(song.layerCount()),
                "length", String.valueOf(song.length())
        };
        return item(MAT_SONG, gui.name("song-list.entry.name", ph), gui.lore("song-list.entry.lore", ph));
    }

    // 바이트를 사람이 읽는 단위로. 작은 곡이 "0.00MiB" 로 보이면 쓸모가 없어 단위를 맞춘다
    static String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + "B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.2fKiB", bytes / 1024.0);
        }
        return String.format("%.2fMiB", bytes / (1024.0 * 1024.0));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            ItemCompat.hideExtraTooltip(meta);
            it.setItemMeta(meta);
        }
        return it;
    }
}

// 컴플리트
