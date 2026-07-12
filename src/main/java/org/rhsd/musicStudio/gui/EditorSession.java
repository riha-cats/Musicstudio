package org.rhsd.musicStudio.gui;

import org.rhsd.musicStudio.model.Note;
import org.rhsd.musicStudio.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// =================================================================
// 에디터 세션 (플레이어별 편집 상태)
// =================================================================
// 대상 곡, 스크롤 오프셋, 선택/커서/재생 위치, 새 노트 기본 음높이, 클립보드
// 레이어 상한은 에디터를 열 때의 config 값을 스냅샷으로 들고 있어,
// 리로드는 다음 열기부터 반영된다
public final class EditorSession {

    // 한 화면에 보이는 틱(가로) / 레이어(세로) 수
    public static final int VISIBLE_TICKS = 8;
    public static final int VISIBLE_LAYERS = 4;

    private final UUID player;
    private final Song song;
    // 이 세션에서 허용하는 레이어 수 (config 스냅샷)
    private final int maxLayers;

    // 가로 스크롤 (가장 왼쪽 셀의 tick)
    private int tickOffset = 0;
    // 세로 스크롤 (맨 위 행의 layer 인덱스)
    private int layerOffset = 0;
    // 새 노트 생성 시 기본 음높이 (F#4)
    private int currentKey = 12;

    // 선택된 노트 좌표. 없으면 null
    private Integer selectedTick = null;
    private Integer selectedLayer = null;

    // 눈금 클릭으로 지정한 복사/붙여넣기 기준 틱. -1 = 없음
    private int cursorTick = -1;
    // 미리보기 재생 헤드. 0 이상이면 재생 중
    private int playingTick = -1;

    // 클립보드. 각 항목 = {기준 틱으로부터의 상대 틱, layer, key}
    private final List<int[]> clipboard = new ArrayList<>();

    public EditorSession(UUID player, Song song, int maxLayers) {
        this.player = player;
        this.song = song;
        this.maxLayers = Song.clampLayerLimit(maxLayers);
    }

    public UUID player() {
        return player;
    }

    public Song song() {
        return song;
    }

    public int maxLayers() {
        return maxLayers;
    }

    public int tickOffset() {
        return tickOffset;
    }

    public void scrollTick(int delta) {
        tickOffset = Math.max(0, tickOffset + delta);
    }

    public void setTickOffset(int offset) {
        tickOffset = Math.max(0, offset);
    }

    public int layerOffset() {
        return layerOffset;
    }

    public void scrollLayer(int delta) {
        // 레이어 추가 버튼이 표시될 슬롯을 확보하기 위해 +1 여유를 준다
        int extra = (song.layerCount() < maxLayers) ? 1 : 0;
        int max = Math.max(0, song.layerCount() + extra - VISIBLE_LAYERS);
        layerOffset = Math.max(0, Math.min(max, layerOffset + delta));
    }

    public int currentKey() {
        return currentKey;
    }

    public void setCurrentKey(int key) {
        this.currentKey = Note.clampKey(key);
    }

    public boolean isSelected(int tick, int layer) {
        return selectedTick != null && selectedLayer != null
                && selectedTick == tick && selectedLayer == layer;
    }

    public Integer selectedTick() {
        return selectedTick;
    }

    public Integer selectedLayer() {
        return selectedLayer;
    }

    // 노트 선택. 커서도 같은 틱으로 따라온다
    public void select(int tick, int layer) {
        this.selectedTick = tick;
        this.selectedLayer = layer;
        this.cursorTick = tick;
    }

    public void clearSelection() {
        this.selectedTick = null;
        this.selectedLayer = null;
    }

    public int cursorTick() {
        return cursorTick;
    }

    // 눈금 클릭. 같은 틱을 다시 클릭했다면? 커서 해제
    public void toggleCursor(int tick) {
        cursorTick = (cursorTick == tick) ? -1 : tick;
    }

    // 복사/붙여넣기 기준 틱. 커서가 없으면 화면 맨 왼쪽 틱
    public int anchorTick() {
        return cursorTick >= 0 ? cursorTick : tickOffset;
    }

    public int playingTick() {
        return playingTick;
    }

    public void setPlayingTick(int playingTick) {
        this.playingTick = playingTick;
    }

    public List<int[]> clipboard() {
        return clipboard;
    }

    public boolean hasClipboard() {
        return !clipboard.isEmpty();
    }

    public void setClipboard(List<int[]> data) {
        clipboard.clear();
        clipboard.addAll(data);
    }

    // 클립보드 항목을 baseTick 기준의 절대 좌표 {tick, layer, key}로 변환
    // maxTicks 를 넘어가는 틱과 곡에 없는 레이어는 걸러낸다
    public static List<int[]> resolvePaste(List<int[]> clipboard, int baseTick,
                                           int maxTicks, int layerCount) {
        List<int[]> out = new ArrayList<>(clipboard.size());
        for (int[] entry : clipboard) {
            int tick = baseTick + entry[0];
            if (tick >= maxTicks || entry[1] >= layerCount) {
                continue;
            }
            out.add(new int[]{tick, entry[1], entry[2]});
        }
        return out;
    }
}

// 컴플리트
