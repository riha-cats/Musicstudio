package org.rhsd.musicStudio.model;

// =================================================================
// 노트
// =================================================================
// (tick, layer) 좌표에 음높이 key(0~24)를 가진다
// 같은 (tick, layer) 조합은 곡 안에서 유일. 화음은 서로 다른 레이어로 표현한다
public final class Note {

    // 노트블록 음역의 최저/최고 key. 0 = F#3, 24 = F#5
    public static final int MIN_KEY = 0;
    public static final int MAX_KEY = 24;

    // key 를 표준 음이름으로 표기할 때 쓰는 12음 배열 (C 시작)
    private static final String[] NOTE_NAMES =
            {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    private final int tick;
    private final int layer;
    private int key;

    public Note(int tick, int layer, int key) {
        this.tick = tick;
        this.layer = layer;
        this.key = clampKey(key);
    }

    public int tick() {
        return tick;
    }

    public int layer() {
        return layer;
    }

    public int key() {
        return key;
    }

    public void setKey(int key) {
        this.key = clampKey(key);
    }

    // 음을 반음 단위로 이동. 범위(0~24)를 벗어나면 클램프
    public void shiftKey(int delta) {
        setKey(this.key + delta);
    }

    public static int clampKey(int key) {
        if (key < MIN_KEY) {
            return MIN_KEY;
        }
        return Math.min(key, MAX_KEY);
    }

    // playSound 용 pitch. key 0~24 → 0.5~2.0 (key 12 = F#4 = 1.0)
    public static float pitch(int key) {
        return (float) Math.pow(2.0, (clampKey(key) - 12) / 12.0);
    }

    public float pitch() {
        return pitch(key);
    }

    // key 를 음이름+옥타브로 표기. key 0 → "F#3", key 24 → "F#5"
    public static String keyName(int key) {
        // F#3 = MIDI 54
        int midi = clampKey(key) + 54;
        return NOTE_NAMES[midi % 12] + (midi / 12 - 1);
    }

    public String keyName() {
        return keyName(key);
    }
}

// 컴플리트
