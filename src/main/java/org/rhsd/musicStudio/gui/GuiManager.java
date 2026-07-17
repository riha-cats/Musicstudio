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
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;
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

    // 클릭한 그 틱에 곧바로 인벤토리를 열면, 누르고 있던 클릭이 새 GUI 의 같은 자리로 넘어가
    // 엉뚱한 버튼이 눌린다. 몇 틱 뒤에 열어 클릭이 소진되기를 기다린다
    private static final long GUI_SWITCH_DELAY = 3L;

    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> previews = new ConcurrentHashMap<>();
    // 아직 안 열린 지연 오픈 예약. 기다리는 중에 플레이어가 닫아버리면 취소해야 한다
    private final Map<UUID, BukkitTask> pendingOpen = new ConcurrentHashMap<>();

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

    // 세션 준비. 명령으로 열든 목록에서 열든 이 앞단은 같아야 한다
    private EditorSession prepareSession(Player player, Song song) {
        // [0] :: 다른 곡을 편집 중이었다면? 이전 곡 저장
        EditorSession old = sessions.get(player.getUniqueId());
        if (old != null && !old.song().id().equals(song.id())) {
            storage.saveAsync(old.song());
        }
        stopPreview(player);
        EditorSession session = new EditorSession(player.getUniqueId(), song, maxLayers());
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    // 명령으로 여는 경로.
    // 지연 없음. 아마도? 이론상은 없음 ㅎ :P
    public void openEditor(Player player, Song song) {
        EditorSession session = prepareSession(player, song);
        player.openInventory(EditorRenderer.build(session, gui));
    }

    private void reopenEditor(Player player, EditorSession session) {
        openLater(player, () -> EditorRenderer.build(session, gui));
    }

    // 인벤토리 닫힘 처리. 다음 틱에 확인 후 세션 정리 또는 유지
    // 지연 오픈 :: 클릭이 새 GUI 로 새어나가지 않도록 몇 틱 뒤에 연다.
    // 예약을 들고 있어야, 기다리는 사이에 플레이어가 닫았을 때 취소할 수 있다
    private void openLater(Player player, Supplier<Inventory> builder) {
        UUID uuid = player.getUniqueId();
        cancelPendingOpen(uuid);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 예약을 먼저 걷는다. 아래 openInventory 가 부르는 닫힘 이벤트가
            // 이 예약을 "사용자가 닫은 것"으로 오해하지 않게 하기 위한 순서다
            pendingOpen.remove(uuid);
            // [1] :: 기다리는 사이에 나갔는가? 세션을 정리하고 끝낸다
            if (!player.isOnline()) {
                closeSession(uuid);
                return;
            }
            player.openInventory(builder.get());
        }, GUI_SWITCH_DELAY);
        pendingOpen.put(uuid, task);
    }

    private void cancelPendingOpen(UUID uuid) {
        BukkitTask pending = pendingOpen.remove(uuid);
        if (pending != null) {
            pending.cancel();
        }
    }

    // 곡 목록 GUI 열기. 본인 곡만 보이고, 관리자는 전체를 본다
    public void openSongList(Player player) {
        List<Song> songs = player.hasPermission(MsCommand.PERM_ADMIN)
                ? storage.all() : storage.getByOwner(player.getUniqueId());
        // 이름순 고정. 안 그러면 열 때마다 순서가 바뀌어 같은 자리를 두 번 못 누른다
        songs.sort(Comparator.comparing(Song::name, String.CASE_INSENSITIVE_ORDER));
        player.openInventory(SongListMenu.build(songs, gui, storage));
    }

    // 곡 목록 메뉴 클릭
    public void handleSongListClick(Player player, SongListMenu menu, int slot) {
        // [1] :: 페이지 이동인가? 인벤토리를 다시 열지 않고 내용만 갈아끼운다
        if (slot == SongListMenu.SLOT_PREV_PAGE || slot == SongListMenu.SLOT_NEXT_PAGE) {
            int delta = slot == SongListMenu.SLOT_PREV_PAGE ? -1 : 1;
            if (menu.movePage(delta)) {
                SongListMenu.render(menu.getInventory(), menu, gui, storage);
            }
            return;
        }
        // [2] :: 곡 칸인가? 빈 칸이면 null 이 온다
        String songId = menu.songIdAt(slot);
        if (songId == null) {
            return;
        }
        // [3] :: 목록을 띄운 뒤에 지워졌을 수 있으니 id 로 다시 조회한다
        Song song = storage.getById(songId);
        if (song == null) {
            msg.send(player, "command.song-not-found", "name", "-");
            SongListMenu.render(menu.getInventory(), menu, gui, storage);
            return;
        }
        EditorSession session = prepareSession(player, song);
        openLater(player, () -> EditorRenderer.build(session, gui));
        // [STOP] :: 목록 클릭 끝
    }

    // 음반 추출 메뉴 클릭
    public void handleOutputClick(Player player, OutputMenu menu, int slot) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.song().id().equals(menu.songId())) {
            return;
        }
        // [1] :: 결제 칸(13~15)을 눌렀는가? 추출을 시도하고 에디터로 돌려보낸다
        if (OutputMenu.isBuySlot(slot)) {
            extractDisc(player, session.song());
            reopenEditor(player, session);
            return;
        }
        // [2] :: 돌아가기
        if (slot == OutputMenu.SLOT_BACK) {
            reopenEditor(player, session);
        }
        // [STOP] :: 추출 메뉴 클릭 끝
    }

    // 세션 제거 + 곡 저장. 닫힘 처리와 지연 오픈 실패 경로가 함께 쓴다
    private void closeSession(UUID uuid) {
        EditorSession session = sessions.remove(uuid);
        if (session != null) {
            storage.saveAsync(session.song());
        }
    }

    public void onInventoryClose(Player player) {
        stopPreview(player);
        UUID uuid = player.getUniqueId();
        // [0] :: 지연 오픈을 기다리는 중에 닫혔다면? 우리가 연 게 아니라 플레이어가 먼저 닫은 것이다
        //        (우리 openInventory 가 부른 닫힘이라면 예약은 이미 걷혀 있어 여기서 안 잡힌다)
        //        예약을 취소하지 않으면 닫은 뒤에 GUI 가 뒤늦게 열린다
        cancelPendingOpen(uuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Object holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof MsHolder) {
                return;
            }
            // [2] :: 진짜로 닫았다면, 세션 정리 + 곡 저장
            closeSession(uuid);
        });
    }

    public void shutdown() {
        for (BukkitTask task : previews.values()) {
            task.cancel();
        }
        previews.clear();
        // 아직 안 열린 예약도 걷는다. 안 그러면 종료 중에 인벤토리를 열려 든다
        for (BukkitTask task : pendingOpen.values()) {
            task.cancel();
        }
        pendingOpen.clear();
    }

    // =================================================================
    // 에디터 클릭 라우팅
    // =================================================================

    public void handleEditorClick(Player player, int slot, ClickType click) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Song song = session.song();
        int row = slot / 9;
        int col = slot % 9;

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
            // [2] :: 틱 배너를 클릭했다면? 붙여넣을 위치 지정 (같은 곳 재클릭 시 해제)
            else if (col >= EditorRenderer.GRID_COL_START) {
                int tick = session.tickOffset() + col - EditorRenderer.GRID_COL_START;
                session.togglePasteTick(tick);
                msg.send(player, session.hasPasteTick()
                                ? "editor.paste-target-set" : "editor.paste-target-cleared",
                        "tick", String.valueOf(tick));
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
        // 범위 선택은 노트 조회보다 먼저 처리한다. 빈 칸도 선택 대상이라야 빈 틱 구조가 복사된다
        if (click == ClickType.SHIFT_LEFT) {
            handleRangeToggle(player, session, tick, layerIdx);
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
            // Q(드롭) :: 노트 삭제. 레이어 헤더의 Q 와 같은 "Q = 삭제" 규칙이다
            case DROP -> {
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
            if (click == ClickType.SWAP_OFFHAND) {
                if (!session.hasMovingLayer()) {
                    session.beginLayerMove(layerIdx);
                    msg.send(player, "editor.layer-move-selected", "layer", String.valueOf(layerIdx + 1),
                            "layer_name", layer.name());
                } else {
                    int from = session.movingLayerIndex();
                    if (from == layerIdx) {
                        session.clearLayerMove();
                        msg.send(player, "editor.layer-move-cancelled", "layer", String.valueOf(layerIdx + 1),
                                "layer_name", layer.name());
                    } else if (song.moveLayer(from, layerIdx)) {
                        session.remapAfterLayerMove(from, layerIdx);
                        session.clearLayerMove();
                        session.scrollLayer(0);
                        msg.send(player, "editor.layer-moved", "from", String.valueOf(from + 1),
                                "to", String.valueOf(layerIdx + 1), "layer_name", song.layer(layerIdx).name());
                    } else {
                        session.clearLayerMove();
                        msg.send(player, "editor.layer-move-invalid");
                    }
                }
                rerender(player, session);
                return;
            }
            switch (click) {
                // 좌클릭 :: 악기 변경 메뉴 열기
                case LEFT -> openLater(player, () -> InstrumentMenu.build(song, layerIdx, gui));
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

    // 음반 추출. 컨트롤 바의 Output 버튼과 설정 메뉴가 함께 쓴다
    private void extractDisc(Player player, Song song) {
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
        // [STOP] :: 추출 끝
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
            // 미리듣기 토글 :: 재생 중이면 정지, 아니면 재생. 버튼 아이콘이 곧 현재 상태다
            case EditorRenderer.SLOT_PLAY -> {
                if (previews.containsKey(player.getUniqueId())) {
                    stopPreview(player);
                    msg.send(player, "editor.play-stop");
                    rerender(player, session);
                } else {
                    startPreview(player, session);
                }
            }
            // 복사 :: 우클릭이면 선택 전체 해제 (대기 중인 시작점도 같이 취소된다)
            case EditorRenderer.SLOT_COPY -> {
                if (click.isRightClick()) clearTickRange(player, session);
                else doCopy(player, session);
            }
            case EditorRenderer.SLOT_PASTE -> doPaste(player, session);
            // 설정 / 음반 추출
            case EditorRenderer.SLOT_SETTINGS -> openLater(player, () -> SettingsMenu.build(session.song(), gui));
            case EditorRenderer.SLOT_OUTPUT   ->
                    openLater(player, () -> OutputMenu.build(session.song(), gui, discManager));
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
        int first = session.selectedTicks().first();
        int last = session.selectedTicks().last();
        int noteCount = data.size();
        int tickCount = session.selectedTicks().size();

        msg.send(player, "editor.copy-success",
                "ticks", String.valueOf(tickCount),
                "notes", String.valueOf(noteCount),
                "from", String.valueOf(first),
                "to", String.valueOf(last));
    }

    private void doPaste(Player player, EditorSession session) {
        // [1] :: 복사한 게 없는가?
        if (!session.hasClipboard()) {
            msg.send(player, "editor.paste-empty");
            return;
        }
        // [2] :: 붙여넣을 자리를 안 정했는가? 폴백으로 몰래 아무 데나 붙이지 않고 되묻는다
        //        ("붙여넣기는 파란 배너에 붙는다"는 규칙에 예외를 두지 않기 위함)
        if (!session.hasPasteTick()) {
            msg.send(player, "editor.paste-no-target");
            return;
        }
        int base = session.pasteTick();
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
            // 템포를 기본값으로
            case SettingsMenu.SLOT_TEMPO_RESET -> {
                song.setTicksPerCell(Song.DEFAULT_TICKS_PER_CELL);
                msg.send(player, "editor.tempo-reset", "tempo",
                        String.valueOf(Song.DEFAULT_TICKS_PER_CELL));
                SettingsMenu.render(menu.getInventory(), song, gui);
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
