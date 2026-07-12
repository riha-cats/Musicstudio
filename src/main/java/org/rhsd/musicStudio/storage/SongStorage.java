package org.rhsd.musicStudio.storage;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.rhsd.musicStudio.model.Instrument;
import org.rhsd.musicStudio.model.Layer;
import org.rhsd.musicStudio.model.Note;
import org.rhsd.musicStudio.model.Song;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// =================================================================
// 곡 저장소 (DB)
// =================================================================
// 곡을 plugins/MusicStudio/songs/<id>.yml 에 YAML 로 저장/로드한다. 외부 의존성 없음
// 인메모리 캐시와 이름 색인을 함께 유지. 곡 이름은 대소문자 무시 유일
public final class SongStorage {

    private final JavaPlugin plugin;
    private final File songsDir;

    private final Map<String, Song> byId = new ConcurrentHashMap<>();
    // 소유자 스코프 이름 색인 :: "<owner> <소문자이름>" -> id. 사람마다 같은 이름 곡을 가질 수 있다
    private final Map<String, String> nameIndex = new ConcurrentHashMap<>();

    public SongStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.songsDir = new File(plugin.getDataFolder(), "songs");
        if (!songsDir.exists() && !songsDir.mkdirs()) {
            plugin.getLogger().warning("songs 디렉터리 생성 실패: " + songsDir.getAbsolutePath());
        }
    }

    // 디스크의 모든 곡을 캐시로 로드. onEnable 에서 호출
    public void loadAll() {
        byId.clear();
        nameIndex.clear();
        File[] files = songsDir.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) {
            return;
        }
        int loaded = 0;
        for (File file : files) {
            Song song = read(file);
            if (song != null) {
                index(song);
                loaded++;
            }
        }
        plugin.getLogger().info("곡 " + loaded + "개 로드 완료.");
    }

    // 소유자 스코프 이름 키. owner=null(서버/임포트 곡)은 "~server" 스코프
    private static String nameKey(UUID owner, String name) {
        String o = (owner == null) ? "~server" : owner.toString();
        return o + " " + name.toLowerCase(Locale.ROOT);
    }

    private void index(Song song) {
        byId.put(song.id(), song);
        nameIndex.put(nameKey(song.owner(), song.name()), song.id());
    }

    // 새 곡 생성 후 즉시 저장. 같은 소유자에게 동일 이름이 있다면? null
    public Song create(String name, UUID owner) {
        if (getByName(owner, name) != null) {
            return null;
        }
        Song song = new Song(UUID.randomUUID().toString(), name, owner);
        index(song);
        save(song);
        return song;
    }

    // 외부에서 만든 곡(NBS 임포트 등)을 캐시에 등록하고 저장
    public void register(Song song) {
        index(song);
        save(song);
    }

    // 해당 소유자 스코프에서 이름이 충돌한다면? _2, _3... 을 붙여 유일화
    public String uniqueName(UUID owner, String base) {
        if (getByName(owner, base) == null) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "_" + i;
            if (getByName(owner, candidate) == null) {
                return candidate;
            }
        }
        return base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public Song getById(String id) {
        return id == null ? null : byId.get(id);
    }

    // 특정 소유자의 곡을 이름으로 조회
    public Song getByName(UUID owner, String name) {
        if (name == null) {
            return null;
        }
        String id = nameIndex.get(nameKey(owner, name));
        return id == null ? null : byId.get(id);
    }

    // 소유자 무관 이름 조회 (관리자용). 동일 이름이 여럿이라면 첫 매칭
    public Song getAnyByName(String name) {
        if (name == null) {
            return null;
        }
        for (Song song : byId.values()) {
            if (song.name().equalsIgnoreCase(name)) {
                return song;
            }
        }
        return null;
    }

    public List<Song> all() {
        return new ArrayList<>(byId.values());
    }

    public List<Song> getByOwner(UUID owner) {
        List<Song> result = new ArrayList<>();
        for (Song song : byId.values()) {
            if (owner != null && owner.equals(song.owner())) {
                result.add(song);
            }
        }
        return result;
    }

    // 곡 이름 변경 (색인 갱신 포함). 새 이름이 이미 점유되어 있다면? false
    public boolean rename(Song song, String newName) {
        Song occupied = getByName(song.owner(), newName);
        if (occupied != null && occupied != song) {
            return false;
        }
        nameIndex.remove(nameKey(song.owner(), song.name()));
        song.setName(newName);
        nameIndex.put(nameKey(song.owner(), newName), song.id());
        save(song);
        return true;
    }

    public boolean delete(Song song) {
        if (song == null) {
            return false;
        }
        File file = new File(songsDir, song.id() + ".yml");
        // 파일을 못 지웠다면? 캐시도 유지한다 — 그래야 재시작 때 되살아나 불일치가 안 생긴다
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("곡 파일 삭제 실패(캐시 유지): " + file.getName());
            return false;
        }
        byId.remove(song.id());
        nameIndex.remove(nameKey(song.owner(), song.name()));
        return true;
    }

    // =================================================================
    // 저장 (직렬화 + 파일 쓰기)
    // =================================================================

    // 모든 캐시된 곡을 디스크에 flush. onDisable 에서 호출
    public void saveAll() {
        for (Song song : byId.values()) {
            save(song);
        }
    }

    // 단일 곡 동기 저장 (즉시 필요/onDisable 용)
    public void save(Song song) {
        writeFile(song.id(), serialize(song), song.name());
    }

    // 단일 곡 비동기 저장
    // 직렬화는 메인 스레드(호출 시점)에서 스냅샷하고 파일 I/O 만 비동기로 돌려 렉을 줄인다
    // 에디터 닫힘 등 즉시성이 필요 없는 저장에 사용
    public void saveAsync(Song song) {
        YamlConfiguration config = serialize(song);
        String id = song.id();
        String name = song.name();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeFile(id, config, name));
    }

    // 곡 → YamlConfiguration 직렬화. 메인 스레드에서 호출, 곡 읽기만 한다
    private YamlConfiguration serialize(Song song) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("id", song.id());
        config.set("name", song.name());
        config.set("owner", song.owner() == null ? null : song.owner().toString());
        config.set("ticksPerCell", song.ticksPerCell());
        config.set("length", song.length());
        config.set("public", song.isPublic());

        List<Map<String, Object>> layerList = new ArrayList<>();
        for (Layer layer : song.layers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("instrument", layer.instrument().name());
            map.put("name", layer.name());
            map.put("volume", (double) layer.volume());
            map.put("muted", layer.muted());
            layerList.add(map);
        }
        config.set("layers", layerList);

        List<String> noteList = new ArrayList<>();
        for (Note note : song.allNotes()) {
            noteList.add(note.tick() + "," + note.layer() + "," + note.key());
        }
        config.set("notes", noteList);
        return config;
    }

    // 임시 파일에 쓴 뒤 원자적으로 옮긴다
    // 비동기 저장이 같은 곡에 겹쳐도 반쪽짜리 파일이 남지 않고, 마지막 쓰기만 살아남는다
    private void writeFile(String id, YamlConfiguration config, String name) {
        File file = new File(songsDir, id + ".yml");
        File tmp = null;
        try {
            tmp = File.createTempFile(id + "-", ".tmp", songsDir);
            config.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "곡 저장 실패: " + name, ex);
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    // =================================================================
    // 로드 (역직렬화)
    // =================================================================

    private Song read(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            // [1] :: 필수 필드가 있는가?
            String id = config.getString("id");
            String name = config.getString("name");
            if (id == null || name == null) {
                plugin.getLogger().warning("손상된 곡 파일(무시): " + file.getName());
                return null;
            }
            // [2] :: 소유자 UUID 파싱. 깨졌다면 소유자 없음으로 취급
            String ownerStr = config.getString("owner");
            UUID owner = null;
            if (ownerStr != null) {
                try {
                    owner = UUID.fromString(ownerStr);
                } catch (IllegalArgumentException ignored) {
                }
            }
            Song song = new Song(id, name, owner);
            song.setTicksPerCell(config.getInt("ticksPerCell", Song.DEFAULT_TICKS_PER_CELL));
            song.setLength(config.getInt("length", 16));
            song.setPublic(config.getBoolean("public", false));

            // [3] :: 레이어 복원. 하나도 없다면 기본 레이어 1개
            song.clearLayers();
            for (Map<?, ?> map : config.getMapList("layers")) {
                Instrument inst = Instrument.fromString(asString(map.get("instrument")));
                String layerName = map.get("name") != null ? asString(map.get("name")) : "레이어";
                Layer layer = new Layer(inst, layerName);
                if (map.get("volume") instanceof Number n) {
                    layer.setVolume(n.floatValue());
                }
                if (map.get("muted") instanceof Boolean b) {
                    layer.setMuted(b);
                }
                song.addLayer(layer);
            }
            if (song.layerCount() == 0) {
                song.addLayer();
            }

            // [4] :: 노트 복원. 손상된 항목은 건너뛴다
            for (String entry : config.getStringList("notes")) {
                String[] parts = entry.split(",");
                if (parts.length == 3) {
                    try {
                        song.setNote(Integer.parseInt(parts[0].trim()),
                                Integer.parseInt(parts[1].trim()),
                                Integer.parseInt(parts[2].trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return song;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "곡 로드 실패: " + file.getName(), ex);
            return null;
        }
        // [STOP] :: 로드 끝
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}

// 컴플리트
