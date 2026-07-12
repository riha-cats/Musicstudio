package org.rhsd.musicStudio.model;

// =================================================================
// 레이어 (트랙)
// =================================================================
// 곡의 한 레이어. 악기 하나와 볼륨/뮤트 상태를 가진다
// 같은 틱의 서로 다른 레이어가 동시에 울리면 화음이 된다
public final class Layer {

    public static final float DEFAULT_VOLUME = 1.0f;

    private Instrument instrument;
    private String name;
    private float volume;
    private boolean muted;

    public Layer(Instrument instrument, String name) {
        this.instrument = instrument == null ? Instrument.HARP : instrument;
        this.name = name;
        this.volume = DEFAULT_VOLUME;
        this.muted = false;
    }

    public Instrument instrument() {
        return instrument;
    }

    public void setInstrument(Instrument instrument) {
        if (instrument != null) {
            this.instrument = instrument;
        }
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float volume() {
        return volume;
    }

    // 볼륨은 0.0 ~ 2.0 범위로 클램프
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(2.0f, volume));
    }

    public boolean muted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }
}

// 컴플리트
