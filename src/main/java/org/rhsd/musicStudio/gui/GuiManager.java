package org.rhsd.musicStudio.gui;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.rhsd.musicStudio.GuiConfig;
import org.rhsd.musicStudio.MessageManager;
import org.rhsd.musicStudio.MusicStudio;
import org.rhsd.musicStudio.command.MsCommand;
import org.rhsd.musicStudio.disc.DiscManager;
import org.rhsd.musicStudio.model.Instrument;
import org.rhsd.musicStudio.model.Layer;
import org.rhsd.musicStudio.model.Note;
import org.rhsd.musicStudio.model.Song;
import org.rhsd.musicStudio.storage.SongStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// =================================================================
// GUI 매니저
// =================================================================
// 에디터 세션 생성/정리, 클릭 라우팅, 복사/붙여넣기, 미리보기 재생 담당
public final class GuiManager {

    private final MusicStudio plugin;
    private final SongStorage storage;
    private final DiscManager discManager;
    private final MessageManager msg;
    private final GuiConfig gui;

    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> previews = new ConcurrentHashMap<>();

    public GuiManager(MusicStudio plugin, SongStorage storage, DiscManager discManager,
                      MessageManager msg, GuiConfig gui) {
        this.plugin = plugin;
        this.storage = storage;
        this.discManager = discManager;
        this.msg = msg;
        this.gui = gui;
    }

    // 곡 최대 길이(셀). config 에서 직접 읽어 리로드가 즉시 반영되게 한다
    private int maxTicks() {
        return Math.max(EditorSession.VISIBLE_TICKS,
                plugin.getConfig().getInt("limits.max-ticks", 4096));
    }

    // 곡당 레이어 상한. config 값을 구조적 상한 안으로 조여서 쓴다
    private int maxLayers() {
        return Song.clampLayerLimit(plugin.getConfig().getInt("limits.max-layers", 9));
    }

    public EditorSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    // =================================================================
    // 열기 / 닫기
    // =================================================================

    public void openEditor(Player player, Song song) {
        // [0] :: 다른 곡을 편집 중이었다면? 이전 곡 저장
        EditorSession old = sessions.get(player.getUniqueId());
        if (old != null && !old.song().id().equals(song.id())) {
            storage.saveAsync(old.song());
        }
        stopPreview(player);
        EditorSession session = new EditorSession(player.getUniqueId(), song, maxLayers());
        sessions.put(player.getUniqueId(), session);
        player.openInventory(EditorRenderer.build(session, gui));
    }

    private void reopenEditor(Player player, EditorSession session) {
        player.openInventory(EditorRenderer.build(session, gui));
    }

    // 인벤토리 닫힘 처리. 다음 틱에 확인 후 세션 정리 또는 유지
    public void onInventoryClose(Player player) {
        stopPreview(player);
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            // [1] :: 우리 GUI 끼리 전환 중인가? 세션 유지
            Object holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof MsHolder) {
                return;
            }
            // [2] :: 진짜로 닫았다면, 세션 정리 + 곡 저장
            EditorSession session = sessions.remove(uuid);
            if (session != null) {
                storage.saveAsync(session.song());
            }
        });
    }

    public void shutdown() {
        for (BukkitTask task : previews.values()) {
            task.cancel();
        }
        previews.clear();
    }

    // =================================================================
    // 에디터 클릭 라우팅
    // =================================================================

    public void handleEditorClick(Player player, int slot, ClickType click, int hotbarButton) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Song song = session.song();
        int row = slot / 9;
        int col = slot % 9;

        // F and number keys are reserved for real cells of existing layer rows only.
        if (click == ClickType.SWAP_OFFHAND || click == ClickType.NUMBER_KEY) {
            int hoveredLayer = session.layerOffset() + row;
            boolean editorCell = row >= 0 && row < EditorSession.VISIBLE_LAYERS
                    && col >= EditorRenderer.GRID_COL_START
                    && col < EditorRenderer.GRID_COL_START + EditorSession.VISIBLE_TICKS
                    && hoveredLayer < session.song().layerCount();
            if (!editorCell) return;
        }

        // [A] :: 컨트롤 바(행5)를 클릭했는가?
        if (row == EditorRenderer.CONTROL_ROW) {
            handleControl(player, session, slot, click);
            return;
        }

        // [B] :: 눈금 행(행4)을 클릭했는가?
        if (row == EditorRenderer.RULER_ROW) {
            // [1] :: 정보(주크박스) 칸이라면, 새 노트 음높이 변경
            if (slot == EditorRenderer.SLOT_INFO) {
                int delta = click.isShiftClick() ? 12 : 1;
                if (click.isLeftClick()) {
                    session.setCurrentKey(session.currentKey() + delta);
                    rerender(player, session);
                } else if (click.isRightClick()) {
                    session.setCurrentKey(session.currentKey() - delta);
                    rerender(player, session);
                }
            }
            // [2] :: 눈금 칸이라면, 복사/붙여넣기 커서 지정 (재클릭 시 해제)
            else if (col >= EditorRenderer.GRID_COL_START) {
                session.toggleCursor(session.tickOffset() + col - EditorRenderer.GRID_COL_START);
                rerenderRuler(player, session);
            }
            return;
        }

        int layerIdx = session.layerOffset() + row;

        // [C] :: 레이어 헤더(열0)를 클릭했는가?
        if (col == 0) {
            handleHeaderClick(player, session, song, layerIdx, click);
            return;
        }

        // [D] :: 그리드 셀 클릭
        if (row < 0 || row >= EditorSession.VISIBLE_LAYERS
                || col < EditorRenderer.GRID_COL_START
                || col >= EditorRenderer.GRID_COL_START + EditorSession.VISIBLE_TICKS
                || layerIdx >= song.layerCount()) return;
        int tick = session.tickOffset() + (col - EditorRenderer.GRID_COL_START);
        // Keyboard actions precede note lookup so empty cells work as targets.
        if (click == ClickType.SWAP_OFFHAND) {
            handleRangeToggle(player, session, tick, layerIdx);
            return;
        }
        if (click == ClickType.NUMBER_KEY) {
            switch (hotbarButton) {
                case 0 -> doCopy(player, session);
                case 1 -> doPaste(player, session, tick);
                case 2 -> clearTickRange(player, session);
                default -> { }
            }
            return;
        }
        if (click == ClickType.DROP) {
            if (session.hasRangeAnchor()) {
                session.clearRangeAnchor();
                msg.send(player, "editor.range-cancelled");
                rerender(player, session);
            }
            return;
        }
        Note note = song.getNote(tick, layerIdx);
        Layer layer = song.layer(layerIdx);

        // [1] :: 빈 칸이라면? 좌클릭으로 현재 음높이 노트 생성
        if (note == null) {
            if (click == ClickType.LEFT) {
                int max = maxTicks();
                if (tick >= max) {
                    msg.send(player, "editor.max-tick", "max", String.valueOf(max));
                    return;
                }
                Note created = song.setNote(tick, layerIdx, session.currentKey());
                session.select(tick, layerIdx);
                previewNote(player, layer, created);
                rerender(player, session);
            }
            return;
        }

        // [2] :: 노트가 있는 칸이라면, 클릭 종류별로 처리
        switch (click) {
            // 좌클릭 :: 선택 + 현재 음높이를 이 노트에 맞추기
            case LEFT -> {
                session.select(tick, layerIdx);
                session.setCurrentKey(note.key());
                rerender(player, session);
            }
            // 우클릭 :: 반음 올리기
            case RIGHT -> {
                note.shiftKey(1);
                previewNote(player, layer, note);
                rerender(player, session);
            }
            // Shift+우클릭 :: 반음 내리기
            case SHIFT_RIGHT -> {
                note.shiftKey(-1);
                previewNote(player, layer, note);
                rerender(player, session);
            }
            // Shift+좌클릭 :: 노트 삭제
            case SHIFT_LEFT -> {
                song.removeNote(tick, layerIdx);
                if (session.isSelected(tick, layerIdx)) session.clearSelection();
                rerender(player, session);
            }
            default -> {
            }
        }
        // [STOP] :: 에디터 클릭 처리 끝
    }

    private void handleHeaderClick(Player player, EditorSession session, Song song,
                                   int layerIdx, ClickType click) {
        // [A] :: 존재하는 레이어의 헤더인가?
        if (layerIdx < song.layerCount()) {
            Layer layer = song.layer(layerIdx);
            switch (click) {
                // 좌클릭 :: 악기 변경 메뉴 열기
                case LEFT -> player.openInventory(InstrumentMenu.build(song, layerIdx, gui));
                // 우클릭 :: 음소거 토글
                case RIGHT -> {
                    layer.setMuted(!layer.muted());
                    rerender(player, session);
                }
                // Shift+좌/우클릭 :: 볼륨 ±10%
                case SHIFT_LEFT -> {
                    layer.setVolume(layer.volume() + 0.1f);
                    rerender(player, session);
                }
                case SHIFT_RIGHT -> {
                    layer.setVolume(layer.volume() - 0.1f);
                    rerender(player, session);
                }
                // Q키(드롭) :: 레이어 삭제. 마지막 레이어라면? 불가
                case DROP -> {
                    if (song.layerCount() <= 1) {
                        msg.send(player, "editor.layer-min");
                        return;
                    }
                    song.removeLayer(layerIdx);
                    session.onLayerDeleted(layerIdx);
                    // 레이어 오프셋이 범위를 벗어났다면 조정
                    session.scrollLayer(0);
                    msg.send(player, "editor.layer-deleted",
                            "layer", String.valueOf(layerIdx + 1));
                    rerender(player, session);
                }
                default -> {
                }
            }
        }
        // [B] :: "레이어 추가" 버튼 자리인가?
        else if (layerIdx == song.layerCount() && song.layerCount() < session.maxLayers()) {
            if (song.addLayer() != null) {
                rerender(player, session);
            }
        }
    }

    private void handleControl(Player player, EditorSession session, int slot, ClickType click) {
        boolean shift = click.isShiftClick();
        switch (slot) {
            // 이전/다음 틱 :: 1틱 스크롤, Shift 는 8틱 점프
            case EditorRenderer.SLOT_PREV_TICK -> {
                session.scrollTick(shift ? -EditorSession.VISIBLE_TICKS : -1);
                rerender(player, session);
            }
            case EditorRenderer.SLOT_NEXT_TICK -> {
                session.scrollTick(shift ? EditorSession.VISIBLE_TICKS : 1);
                rerender(player, session);
            }
            // 레이어 위/아래 스크롤
            case EditorRenderer.SLOT_UP_LAYER -> {
                session.scrollLayer(-1);
                rerender(player, session);
            }
            case EditorRenderer.SLOT_DOWN_LAYER -> {
                session.scrollLayer(1);
                rerender(player, session);
            }
            // 미리듣기 / 정지
            case EditorRenderer.SLOT_PLAY  -> startPreview(player, session);
            case EditorRenderer.SLOT_STOP  -> {
                boolean wasPlaying = previews.containsKey(player.getUniqueId());
                stopPreview(player);
                if (wasPlaying) msg.send(player, "editor.play-stop");
                rerender(player, session);
            }
            // 설정 / 복사 / 붙여넣기
            case EditorRenderer.SLOT_SETTINGS -> player.openInventory(SettingsMenu.build(session.song(), gui));
            default -> {
            }
        }
    }

    // =================================================================
    // 복사 / 붙여넣기
    // =================================================================

    private void handleRangeToggle(Player player, EditorSession session, int tick, int layer) {
        if (!session.hasRangeAnchor()) {
            session.setRangeAnchor(tick, layer);
            msg.send(player, "editor.range-start", "anchor_tick", String.valueOf(tick));
            rerender(player, session);
            return;
        }
        int from = Math.min(session.rangeAnchorTick(), tick);
        int to = Math.max(session.rangeAnchorTick(), tick);
        int selectedLayer = session.rangeAnchorLayer();
        EditorSession.RangeAction action = session.toggleTickRange(selectedLayer, from, to);
        session.clearRangeAnchor();
        msg.send(player, action == EditorSession.RangeAction.ADDED ? "editor.range-added" : "editor.range-removed",
                "from", String.valueOf(from), "to", String.valueOf(to),
                "affected", String.valueOf(to - from + 1), "total", String.valueOf(session.selectedTicks().size()));
        rerender(player, session);
    }

    private void clearTickRange(Player player, EditorSession session) {
        session.clearTickSelection();
        session.clearRangeAnchor();
        msg.send(player, "editor.selection-cleared");
        rerender(player, session);
    }

    private void doCopy(Player player, EditorSession session) {
        if (session.selectedCells().isEmpty()) {
            msg.send(player, "editor.copy-no-selection");
            return;
        }
        int origin = session.selectedTicks().first();
        List<EditorSession.ClipboardNote> data = new ArrayList<>();
        List<EditorSession.ClipboardCell> cells = new ArrayList<>();
        for (EditorSession.SelectedCell cell : session.selectedCells()) {
            cells.add(new EditorSession.ClipboardCell(cell.tick() - origin, cell.layer()));
            Note note = session.song().getNote(cell.tick(), cell.layer());
            if (note != null)
                data.add(new EditorSession.ClipboardNote(cell.tick() - origin, note.layer(), note.key()));
        }
        if (data.isEmpty()) {
            msg.send(player, "editor.copy-no-notes");
            return;
        }
        session.setClipboard(data, cells);
        msg.send(player, "editor.copy-success", "ticks", String.valueOf(session.selectedTicks().size()),
                "notes", String.valueOf(data.size()), "from", String.valueOf(session.selectedTicks().first()),
                "to", String.valueOf(session.selectedTicks().last()));
    }

    private void doPaste(Player player, EditorSession session, int base) {
        if (!session.hasClipboard()) {
            msg.send(player, "editor.paste-empty");
            return;
        }
        int max = maxTicks();
        List<EditorSession.PasteNote> placements = EditorSession.resolvePaste(
                session.clipboardNotes(), base, max, session.song().layerCount());
        for (EditorSession.PasteNote p : placements) session.song().setNote(p.tick(), p.layer(), p.key());
        NavigableSet<EditorSession.SelectedCell> pastedCells = new TreeSet<>();
        for (EditorSession.ClipboardCell cell : session.clipboardCells()) {
            long target = (long) base + cell.relativeTick();
            if (target >= 0 && target < max && cell.layer() >= 0 && cell.layer() < session.song().layerCount())
                pastedCells.add(new EditorSession.SelectedCell((int) target, cell.layer()));
        }
        session.replaceCellSelection(pastedCells);
        session.clearRangeAnchor();
        int skipped = session.clipboardNotes().size() - placements.size();
        msg.send(player, "editor.paste-result", "pasted", String.valueOf(placements.size()),
                "skipped", String.valueOf(skipped), "tick", String.valueOf(base));
        rerender(player, session);
    }

    // =================================================================
    // 악기 메뉴 / 설정 메뉴 클릭
    // =================================================================

    public void handleInstrumentClick(Player player, InstrumentMenu menu, int slot) {
        Instrument[] values = Instrument.values();
        if (slot < 0 || slot >= values.length) return;
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        Layer layer = session.song().layer(menu.layerIndex());
        if (layer != null) layer.setInstrument(values[slot]);
        reopenEditor(player, session);
    }

    public void handleSettingsClick(Player player, SettingsMenu menu, int slot) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        Song song = session.song();
        switch (slot) {
            // 템포 :: 셀당 틱 늘리기(느리게) / 줄이기(빠르게)
            case SettingsMenu.SLOT_TEMPO_DOWN -> {
                song.setTicksPerCell(song.ticksPerCell() + 1);
                SettingsMenu.render(menu.getInventory(), song, gui);
            }
            case SettingsMenu.SLOT_TEMPO_UP -> {
                song.setTicksPerCell(song.ticksPerCell() - 1);
                SettingsMenu.render(menu.getInventory(), song, gui);
            }
            // 음반 추출
            case SettingsMenu.SLOT_DISC -> {
                // [1] :: 빈 곡인가?
                if (song.maxTick() < 0) {
                    msg.send(player, "editor.disc-empty");
                }
                // [2] :: 본인 소유 곡이 아닌가? (관리자는 통과)
                else if (song.owner() != null
                        && !song.owner().equals(player.getUniqueId())
                        && !player.hasPermission(MsCommand.PERM_ADMIN)) {
                    msg.send(player, "command.disc-not-owner");
                }
                // [3] :: 추출 비용이 부족한가?
                else if (!discManager.takeCost(player)) {
                    msg.send(player, "command.disc-cost-insufficient", discManager.costPlaceholders());
                }
                // [4] PASSED :: 음반 지급
                else {
                    discManager.giveDisc(player, song);
                    msg.send(player, "editor.disc-extracted", "name", song.name());
                }
            }
            // 에디터로 복귀
            case SettingsMenu.SLOT_BACK -> reopenEditor(player, session);
            default -> {
            }
        }
    }

    // =================================================================
    // 미리보기 재생
    // =================================================================

    private void startPreview(Player player, EditorSession session) {
        stopPreview(player);
        Song song = session.song();
        int maxTick = song.maxTick();
        // 재생할 노트가 없는가?
        if (maxTick < 0) {
            msg.send(player, "editor.no-notes");
            return;
        }
        msg.send(player, "editor.play-start");
        UUID uuid = player.getUniqueId();
        int period = Math.max(1, song.ticksPerCell());
        BukkitTask task = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // [1] :: 플레이어가 나갔거나 세션이 바뀌었는가? 재생 종료
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || sessions.get(uuid) != session) {
                    finish();
                    return;
                }
                // [2] :: 곡 끝까지 재생했는가?
                if (tick > maxTick) {
                    session.setPlayingTick(-1);
                    rerender(p, session);
                    finish();
                    return;
                }
                // [3] :: 재생 헤드가 화면 밖이라면, 헤드를 포함하는 페이지로 이동
                //        스크롤이 바뀌면 전체 재렌더, 아니면 눈금 행만 갱신해 오버헤드를 줄인다
                boolean scrolled = false;
                int vStart = session.tickOffset();
                if (tick < vStart || tick >= vStart + EditorSession.VISIBLE_TICKS) {
                    int page = (tick / EditorSession.VISIBLE_TICKS) * EditorSession.VISIBLE_TICKS;
                    session.setTickOffset(page);
                    scrolled = true;
                }
                playTick(p, song, tick);
                session.setPlayingTick(tick);
                if (scrolled) {
                    rerender(p, session);
                } else {
                    rerenderRuler(p, session);
                }
                tick++;
            }

            private void finish() {
                previews.remove(uuid);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, period);
        previews.put(uuid, task);
        // [STOP] :: 미리보기 시작 끝
    }

    public void stopPreview(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = previews.remove(uuid);
        if (task != null) task.cancel();
        EditorSession session = sessions.get(uuid);
        if (session != null) session.setPlayingTick(-1);
    }

    private void playTick(Player player, Song song, int tick) {
        for (Note note : song.notesAtTick(tick)) {
            Layer layer = song.layer(note.layer());
            if (layer == null || layer.muted()) continue;
            player.playSound(player.getLocation(), layer.instrument().sound(),
                    SoundCategory.MASTER, layer.volume(), note.pitch());
        }
    }

    private void previewNote(Player player, Layer layer, Note note) {
        if (layer == null || layer.muted()) return;
        player.playSound(player.getLocation(), layer.instrument().sound(),
                SoundCategory.MASTER, layer.volume(), note.pitch());
    }

    // =================================================================
    // 재렌더
    // =================================================================

    private void rerender(Player player, EditorSession session) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof EditorHolder holder
                && holder.songId().equals(session.song().id())) {
            EditorRenderer.render(top, session, gui);
        }
    }

    // 눈금 행만 갱신 (재생 헤드/커서 이동, 스크롤 미변경 시)
    private void rerenderRuler(Player player, EditorSession session) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof EditorHolder holder
                && holder.songId().equals(session.song().id())) {
            EditorRenderer.renderRuler(top, session, gui);
        }
    }
}

// 컴플리트
