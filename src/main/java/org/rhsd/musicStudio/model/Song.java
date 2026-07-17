package org.rhsd.musicStudio.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// =================================================================
// 곡 데이터
// =================================================================
// 레이어 목록 + (tick, layer)→Note 맵 + 템포(셀당 서버틱) + 메타데이터
// 노트는 (tick<<8)|layer 형태의 long 키로 색인해 조회/충돌검사를 O(1)로 처리한다
// 레이어 인덱스가 하위 8비트에 들어가므로 256 이 구조적 상한
public final class Song {

    // 레이어 개수의 절대 상한. 노트 키 패킹(layer & 0xFF) 때문에 이 값을 넘기면
    // 서로 다른 레이어의 노트 키가 충돌한다. 운영상 허용 개수(기본 9)는
    // config 의 limits.max-layers 로 호출부가 정하고, 이 값으로 한 번 더 조인다
    public static final int LAYER_CAP = 256;
    public static final int MIN_TICKS_PER_CELL = 1;
    public static final int MAX_TICKS_PER_CELL = 6;
    public static final int DEFAULT_TICKS_PER_CELL = 4;

    private final String id;
    private String name;
    private UUID owner;
    private int ticksPerCell = DEFAULT_TICKS_PER_CELL;
    // 논리적 길이 (셀 수). 노트 추가 시 확장
    private int length = 16;
    private boolean isPublic = false;

    private final List<Layer> layers = new ArrayList<>();
    private final Map<Long, Note> notes = new HashMap<>();

    public Song(String id, String name, UUID owner) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        // 신규 곡은 기본 레이어 1개로 시작
        layers.add(new Layer(Instrument.HARP, "레이어 1"));
    }

    private static long index(int tick, int layer) {
        return ((long) tick << 8) | (layer & 0xFF);
    }

    // config 의 max-layers 값을 1~LAYER_CAP 범위로 조이기
    public static int clampLayerLimit(int configured) {
        return Math.max(1, Math.min(LAYER_CAP, configured));
    }

    // 메타데이터
    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public int ticksPerCell() {
        return ticksPerCell;
    }

    public void setTicksPerCell(int ticksPerCell) {
        this.ticksPerCell = Math.max(MIN_TICKS_PER_CELL, Math.min(MAX_TICKS_PER_CELL, ticksPerCell));
    }

    public int length() {
        return length;
    }

    public void setLength(int length) {
        this.length = Math.max(1, length);
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    // 레이어
    public List<Layer> layers() {
        return layers;
    }

    public int layerCount() {
        return layers.size();
    }

    public Layer layer(int index) {
        if (index < 0 || index >= layers.size()) {
            return null;
        }
        return layers.get(index);
    }

    // 레이어 추가. 구조적 상한 도달 시 null (운영 상한은 호출부가 먼저 검사)
    public Layer addLayer() {
        if (layers.size() >= LAYER_CAP) {
            return null;
        }
        Layer layer = new Layer(Instrument.HARP, "레이어 " + (layers.size() + 1));
        layers.add(layer);
        return layer;
    }

    // 레이어 추가 (역직렬화용)
    public void addLayer(Layer layer) {
        if (layers.size() < LAYER_CAP && layer != null) {
            layers.add(layer);
        }
    }

    // 레이어 목록 초기화 (역직렬화 시작 시)
    public void clearLayers() {
        layers.clear();
    }

    // 레이어 삭제. 해당 레이어의 노트는 제거되고 상위 레이어 노트들의 인덱스가 1씩 당겨진다
    public boolean removeLayer(int index) {
        // [1] :: 마지막 레이어(1개 남음)거나 잘못된 인덱스라면? 삭제 불가
        if (layers.size() <= 1 || index < 0 || index >= layers.size()) {
            return false;
        }
        layers.remove(index);

        // [2] :: notes 맵 재구성 — 삭제 레이어 노트 제거 + 상위 레이어 인덱스 shift
        Map<Long, Note> rebuilt = new HashMap<>(notes.size());
        for (Note note : notes.values()) {
            if (note.layer() == index) {
                continue;
            }
            int newLayer = note.layer() > index ? note.layer() - 1 : note.layer();
            Note shifted = new Note(note.tick(), newLayer, note.key());
            rebuilt.put(((long) note.tick() << 8) | (newLayer & 0xFF), shifted);
        }
        notes.clear();
        notes.putAll(rebuilt);
        return true;
        // [STOP] :: 레이어 삭제 끝
    }

    public static int remapLayerIndex(int layer, int from, int to) {
        if (layer == from) return to;
        if (from < to && layer > from && layer <= to) return layer - 1;
        if (from > to && layer >= to && layer < from) return layer + 1;
        return layer;
    }

    /** Moves one complete logical layer to the exact requested index. */
    public boolean moveLayer(int from, int to) {
        if (from < 0 || to < 0 || from >= layers.size() || to >= layers.size() || from == to) {
            return false;
        }

        Layer movedLayer = layers.get(from);
        List<Layer> reordered = new ArrayList<>(layers);
        reordered.remove(from);
        reordered.add(to, movedLayer);

        Map<Long, Note> rebuilt = new HashMap<>(notes.size());
        for (Note note : notes.values()) {
            int newLayer = remapLayerIndex(note.layer(), from, to);
            Note moved = new Note(note.tick(), newLayer, note.key());
            Note collision = rebuilt.put(index(moved.tick(), moved.layer()), moved);
            if (collision != null) {
                throw new IllegalStateException("Layer move produced a note collision");
            }
        }

        layers.clear();
        layers.addAll(reordered);
        notes.clear();
        notes.putAll(rebuilt);
        return true;
    }

    // 노트
    public Note getNote(int tick, int layer) {
        return notes.get(index(tick, layer));
    }

    // (tick, layer)에 노트를 생성하거나, 이미 있다면 key 만 갱신
    public Note setNote(int tick, int layer, int key) {
        long idx = index(tick, layer);
        Note existing = notes.get(idx);
        if (existing != null) {
            existing.setKey(key);
        } else {
            existing = new Note(tick, layer, key);
            notes.put(idx, existing);
        }
        if (tick + 1 > length) {
            length = tick + 1;
        }
        return existing;
    }

    public Note removeNote(int tick, int layer) {
        return notes.remove(index(tick, layer));
    }

    // 재생용 :: 특정 틱의 모든 레이어 노트
    public List<Note> notesAtTick(int tick) {
        List<Note> result = new ArrayList<>();
        for (int layer = 0; layer < layers.size(); layer++) {
            Note note = notes.get(index(tick, layer));
            if (note != null) {
                result.add(note);
            }
        }
        return result;
    }

    // 저장용 :: 모든 노트
    public Collection<Note> allNotes() {
        return notes.values();
    }

    public int noteCount() {
        return notes.size();
    }

    // 노트가 존재하는 가장 큰 tick. 없으면 -1
    public int maxTick() {
        int max = -1;
        for (Note note : notes.values()) {
            if (note.tick() > max) {
                max = note.tick();
            }
        }
        return max;
    }
}

// 컴플리트
