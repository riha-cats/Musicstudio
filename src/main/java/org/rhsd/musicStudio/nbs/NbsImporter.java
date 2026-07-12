package org.rhsd.musicStudio.nbs;

import org.rhsd.musicStudio.model.Instrument;
import org.rhsd.musicStudio.model.Layer;
import org.rhsd.musicStudio.model.Note;
import org.rhsd.musicStudio.model.Song;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// =================================================================
// NBS 임포터
// =================================================================
// Note Block Studio(.nbs) 바이너리 파서. OpenNBS 포맷(클래식 v0, 신포맷 v1~v5)을 읽어
// Song 으로 근사 변환한다
// NBS 는 노트마다 악기를 갖지만 본 모델은 레이어 하나에 악기 하나 —
// 레이어별 첫 노트의 악기를 그 레이어 악기로 삼는다
// 허용 레이어 수(maxLayers)를 넘는 레이어와 노트블록 음역(NBS key 33~57) 밖 음은 잘라낸다
// NBS 악기 0~15는 Instrument 열거 순서와 같다
public final class NbsImporter {

    // NBS key 33 = 노트블록 최저음(F#3) = 본 모델 key 0
    private static final int NBS_KEY_OFFSET = 33;

    // 손상/악성 파일이 메모리와 메인스레드를 고갈시키지 못하게 막는 한계
    private static final int MAX_NOTES = 200_000;
    private static final int MAX_STRING_BYTES = 65_535;

    private NbsImporter() {
    }

    // 변환 결과 :: 곡 + 사용자에게 보여줄 경고
    public record Result(Song song, List<String> warnings) {
    }

    public static Result importFile(File file, String fallbackName, int maxLayers) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            List<String> warnings = new ArrayList<>();

            // [0] :: 헤더 읽기. first == 0 이면 신포맷, 아니면 클래식(first = 곡 길이)
            int first = readShortLE(in);
            int version = 0;
            if (first == 0) {
                version = readUByte(in);
                // vanilla instrument count
                readUByte(in);
                if (version >= 3) {
                    // song length(틱) — 노트로부터 재계산하므로 무시
                    readShortLE(in);
                }
            }

            int layerCount = readShortLE(in);
            String name = readString(in);
            // author / original author / description
            readString(in);
            readString(in);
            readString(in);
            // tempo * 100 = TPS*100
            int tempo = readShortLE(in);
            // auto-save on/off, auto-save duration, time signature
            readUByte(in);
            readUByte(in);
            readUByte(in);
            // minutes spent, left clicks, right clicks, blocks added, blocks removed
            readIntLE(in);
            readIntLE(in);
            readIntLE(in);
            readIntLE(in);
            readIntLE(in);
            // MIDI/schematic file name
            readString(in);
            if (version >= 4) {
                // loop on/off, max loop count, loop start tick
                readUByte(in);
                readUByte(in);
                readShortLE(in);
            }

            // [1] :: 노트 블록 데이터 읽기 (jump 방식 순회)
            // 각 항목 = {tick, layer, instrument, ourKey}
            List<int[]> notes = new ArrayList<>();
            Map<Integer, Integer> firstInstrument = new HashMap<>();
            int maxLayer = -1;
            int outOfRange = 0;

            int tick = -1;
            boolean truncated = false;
            reading:
            while (true) {
                int jumpTick = readShortLE(in);
                if (jumpTick == 0) {
                    break;
                }
                tick += jumpTick;
                int layer = -1;
                while (true) {
                    int jumpLayer = readShortLE(in);
                    if (jumpLayer == 0) {
                        break;
                    }
                    layer += jumpLayer;
                    int instrument = readUByte(in);
                    int key = readUByte(in);
                    if (version >= 4) {
                        // velocity, panning, pitch(fine)
                        readUByte(in);
                        readUByte(in);
                        readShortLE(in);
                    }
                    if (key < NBS_KEY_OFFSET || key > NBS_KEY_OFFSET + Note.MAX_KEY) {
                        outOfRange++;
                    }
                    int ourKey = Note.clampKey(key - NBS_KEY_OFFSET);
                    int inst = instrument >= 0 && instrument < Instrument.values().length ? instrument : 0;
                    notes.add(new int[]{tick, layer, inst, ourKey});
                    firstInstrument.putIfAbsent(layer, inst);
                    if (layer > maxLayer) {
                        maxLayer = layer;
                    }
                    // 노트가 한계를 넘는가? 여기서 끊는다
                    if (notes.size() >= MAX_NOTES) {
                        truncated = true;
                        break reading;
                    }
                }
            }
            // [STOP] :: 노트 데이터 끝

            // [2] :: 레이어 메타(이름/볼륨) 읽기
            String[] layerNames = new String[Math.max(0, layerCount)];
            float[] layerVolumes = new float[Math.max(0, layerCount)];
            try {
                for (int i = 0; i < layerCount; i++) {
                    layerNames[i] = readString(in);
                    if (version >= 4) {
                        // lock
                        readUByte(in);
                    }
                    layerVolumes[i] = readUByte(in) / 100.0f;
                    if (version >= 2) {
                        // stereo/panning
                        readUByte(in);
                    }
                }
            } catch (EOFException ignored) {
                // 일부 파일은 레이어 섹션이 잘려있을 수 있음 — 기본값 사용
            }

            // [3] :: Song 조립
            String songName = (name != null && !name.isBlank()) ? name.trim() : fallbackName;
            Song song = new Song(UUID.randomUUID().toString(), songName, null);
            song.setPublic(true);

            // setter 가 1~6으로 클램프
            int tpc = tempo > 0 ? Math.round(2000.0f / tempo) : Song.DEFAULT_TICKS_PER_CELL;
            song.setTicksPerCell(tpc);

            int cap = Song.clampLayerLimit(maxLayers);
            int usedLayers = Math.max(1, Math.min(cap, Math.max(layerCount, maxLayer + 1)));
            if (maxLayer + 1 > cap) {
                warnings.add("레이어 " + (maxLayer + 1) + "개 중 " + cap
                        + "개만 가져왔습니다(초과 레이어 무시).");
            }

            song.clearLayers();
            for (int i = 0; i < usedLayers; i++) {
                int instId = firstInstrument.getOrDefault(i, 0);
                Instrument inst = Instrument.values()[instId];
                String lname = (i < layerNames.length && layerNames[i] != null && !layerNames[i].isBlank())
                        ? layerNames[i].trim() : "레이어 " + (i + 1);
                Layer layer = new Layer(inst, lname);
                if (i < layerVolumes.length && layerVolumes[i] > 0) {
                    layer.setVolume(layerVolumes[i]);
                }
                song.addLayer(layer);
            }

            // [4] :: 노트 옮겨 담기. 허용 레이어 밖 노트는 버린다
            int imported = 0;
            for (int[] n : notes) {
                if (n[1] >= 0 && n[1] < usedLayers) {
                    song.setNote(n[0], n[1], n[3]);
                    imported++;
                }
            }

            // [5] :: 결과 도출
            if (outOfRange > 0) {
                warnings.add("음역(F#3~F#5) 밖 노트 " + outOfRange + "개를 최근접 음으로 클램프했습니다.");
            }
            if (truncated) {
                warnings.add("노트가 너무 많아 " + MAX_NOTES + "개까지만 가져왔습니다.");
            }
            warnings.add("노트 " + imported + "개, 레이어 " + usedLayers + "개, 템포 셀당 "
                    + song.ticksPerCell() + "틱으로 변환됨.");
            return new Result(song, warnings);
        }
        // [STOP] :: 임포트 끝
    }

    // =================================================================
    // 리틀엔디안 읽기 헬퍼
    // =================================================================

    private static int readUByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException();
        }
        return b;
    }

    private static int readShortLE(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 < 0 || b1 < 0) {
            throw new EOFException();
        }
        return (b0 & 0xFF) | ((b1 & 0xFF) << 8);
    }

    private static int readIntLE(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) {
            throw new EOFException();
        }
        return (b0 & 0xFF) | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24);
    }

    private static String readString(InputStream in) throws IOException {
        int len = readIntLE(in);
        if (len < 0 || len > MAX_STRING_BYTES) {
            throw new IOException("문자열 길이가 비정상입니다: " + len);
        }
        byte[] buf = new byte[len];
        int read = 0;
        while (read < len) {
            int r = in.read(buf, read, len - read);
            if (r < 0) {
                throw new EOFException();
            }
            read += r;
        }
        return new String(buf, StandardCharsets.UTF_8);
    }
}

// 컴플리트
