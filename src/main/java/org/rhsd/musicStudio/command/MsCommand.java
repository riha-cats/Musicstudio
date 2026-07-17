package org.rhsd.musicStudio.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.rhsd.musicStudio.MessageManager;
import org.rhsd.musicStudio.disc.DiscManager;
import org.rhsd.musicStudio.gui.GuiManager;
import org.rhsd.musicStudio.model.Song;
import org.rhsd.musicStudio.nbs.NbsImporter;
import org.rhsd.musicStudio.storage.SongStorage;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// =================================================================
// /음악스튜디오 명령어 (alias: ms, 음스)
// =================================================================
// 서브커맨드는 한국어 우선, 영문도 허용
// 곡은 소유자별로 격리 — 본인 곡만 보이고 편집된다. 관리자는 전체 접근
public final class MsCommand implements CommandExecutor {

    public static final String PERM_USE   = "musicstudio.use";
    public static final String PERM_ADMIN = "musicstudio.admin";

    private static final int MAX_NAME_LENGTH = 32;

    private final JavaPlugin plugin;
    private final SongStorage storage;
    private final GuiManager gui;
    private final DiscManager discManager;
    private final MessageManager msg;

    public MsCommand(JavaPlugin plugin, SongStorage storage, GuiManager gui,
                     DiscManager discManager, MessageManager msg) {
        this.plugin = plugin;
        this.storage = storage;
        this.gui = gui;
        this.discManager = discManager;
        this.msg = msg;
    }

    // 플레이어당 곡 개수 상한. config 에서 직접 읽어 리로드가 즉시 반영되게 한다
    private int maxSongsPerPlayer() {
        return Math.max(1, plugin.getConfig().getInt("limits.max-songs-per-player", 30));
    }

    // 곡당 레이어 상한. 임포트 시 가져올 레이어 수를 이 값으로 제한
    private int maxLayers() {
        return Song.clampLayerLimit(plugin.getConfig().getInt("limits.max-layers", 9));
    }

    // 한국어/영문 서브커맨드를 표준 키로 정규화
    public static String normalizeSub(String sub) {
        return switch (sub.toLowerCase()) {
            case "생성", "만들기", "create" -> "create";
            case "목록", "list" -> "list";
            case "열기", "open" -> "open";
            case "이름변경", "이름", "rename" -> "rename";
            case "음반", "disc" -> "disc";
            case "삭제", "delete" -> "delete";
            case "관리자", "admin" -> "admin";
            case "도움말", "help" -> "help";
            default -> sub.toLowerCase();
        };
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // [A] :: 사용 권한이 있는가?
        if (!sender.hasPermission(PERM_USE)) {
            msg.send(sender, "no-permission");
            return true;
        }

        // [B] :: arguments 가 설정되어있는가? 없으면 도움말
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // [C] :: 서브커맨드 분기
        switch (normalizeSub(args[0])) {
            case "create" -> handleCreate(sender, args);
            case "list"   -> handleList(sender);
            case "open"   -> handleOpen(sender, args);
            case "rename" -> handleRename(sender, args);
            case "disc"   -> handleDisc(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "admin"  -> handleAdmin(sender, args);
            default       -> sendHelp(sender);
        }
        return true;
    }

    // 본인 곡 우선 조회. 관리자라면 전역 폴백
    private Song resolveOwned(Player player, String name) {
        Song song = storage.getByName(player.getUniqueId(), name);
        if (song == null && player.hasPermission(PERM_ADMIN)) {
            song = storage.getAnyByName(name);
        }
        return song;
    }

    // args[1..]를 합쳐 곡 이름으로 만든다. 공백은 '_'로 치환
    private String joinName(String[] args) {
        return String.join("_", Arrays.copyOfRange(args, 1, args.length)).trim();
    }

    // =================================================================
    // 곡 생성
    // =================================================================
    private void handleCreate(CommandSender sender, String[] args) {
        // [1] :: 플레이어인가?
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        // [2] :: 이름 arguments 가 설정되어 있는가?
        if (args.length < 2) {
            msg.send(sender, "command.create-usage");
            return;
        }
        // [3] :: 올바른 이름인가?
        String name = joinName(args);
        if (!isValidName(name)) {
            msg.send(sender, "command.create-name-invalid", "max", String.valueOf(MAX_NAME_LENGTH));
            return;
        }
        // [4] :: 곡 개수 상한에 걸리는가? (관리자는 면제)
        int maxSongs = maxSongsPerPlayer();
        if (!player.hasPermission(PERM_ADMIN)
                && storage.getByOwner(player.getUniqueId()).size() >= maxSongs) {
            msg.send(sender, "command.create-limit", "max", String.valueOf(maxSongs));
            return;
        }
        // [5] PASSED :: 생성. 같은 이름이 이미 있다면 null
        Song song = storage.create(name, player.getUniqueId());
        if (song == null) {
            msg.send(sender, "command.create-name-taken", "name", name);
            return;
        }
        msg.send(sender, "command.create-success", "name", name);
        // [STOP] :: 생성 끝
    }

    // =================================================================
    // 곡 목록
    // =================================================================
    private void handleList(CommandSender sender) {
        // 일반 유저는 본인 곡만, 관리자/CONSOLE 은 전체
        List<Song> songs = (sender instanceof Player p && !p.hasPermission(PERM_ADMIN))
                ? storage.getByOwner(p.getUniqueId())
                : storage.all();
        if (songs.isEmpty()) {
            msg.send(sender, "command.list-empty");
            return;
        }
        msg.send(sender, "command.list-header", "count", String.valueOf(songs.size()));
        for (Song song : songs) {
            sender.sendMessage(msg.raw("command.list-entry",
                    "name", song.name(),
                    "layers", String.valueOf(song.layerCount()),
                    "notes", String.valueOf(song.noteCount())));
        }
    }

    // =================================================================
    // 에디터 열기
    // =================================================================
    private void handleOpen(CommandSender sender, String[] args) {
        // [1] :: 플레이어인가?
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        // [2] :: 이름 없이 그냥 "열기" 인가? 곡 목록 GUI 를 띄운다
        if (args.length < 2) {
            gui.openSongList(player);
            return;
        }
        // [3] :: 곡이 존재하는가?
        Song song = resolveOwned(player, joinName(args));
        if (song == null) {
            msg.send(sender, "command.not-found", "name", joinName(args));
            return;
        }
        gui.openEditor(player, song);
    }

    // =================================================================
    // 이름 변경
    // =================================================================
    private void handleRename(CommandSender sender, String[] args) {
        // [1] :: 플레이어인가?
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        // [2] :: 기존이름 + 새이름 arguments 가 설정되어 있는가?
        if (args.length < 3) {
            msg.send(sender, "command.rename-usage");
            return;
        }
        String oldName = args[1];
        String newName = String.join("_", Arrays.copyOfRange(args, 2, args.length)).trim();
        // [3] :: 곡이 존재하는가?
        Song song = resolveOwned(player, oldName);
        if (song == null) {
            msg.send(sender, "command.not-found", "name", oldName);
            return;
        }
        // [4] :: 새 이름이 올바른가?
        if (!isValidName(newName)) {
            msg.send(sender, "command.create-name-invalid", "max", String.valueOf(MAX_NAME_LENGTH));
            return;
        }
        // [5] :: 변경. 새 이름이 이미 점유되어 있다면 실패
        if (storage.rename(song, newName)) {
            msg.send(sender, "command.rename-success", "old", oldName, "new", newName);
        } else {
            msg.send(sender, "command.rename-taken", "name", newName);
        }
    }

    // =================================================================
    // 음반 추출
    // =================================================================
    private void handleDisc(CommandSender sender, String[] args) {
        // [1] :: 플레이어인가?
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        // [2] :: 이름 arguments 가 설정되어 있는가?
        if (args.length < 2) {
            msg.send(sender, "command.disc-usage");
            return;
        }
        // [3] :: 곡이 존재하는가?
        Song song = resolveOwned(player, joinName(args));
        if (song == null) {
            msg.send(sender, "command.not-found", "name", joinName(args));
            return;
        }
        // [4] :: 빈 곡인가?
        if (song.maxTick() < 0) {
            msg.send(sender, "command.disc-empty-song");
            return;
        }
        // [5] :: 추출 비용을 낼 수 있는가?
        if (!discManager.takeCost(player)) {
            msg.send(sender, "command.disc-cost-insufficient", discManager.costPlaceholders());
            return;
        }
        // [6] PASSED :: 음반 지급
        discManager.giveDisc(player, song);
        msg.send(sender, "command.disc-success", "name", song.name());
        // [STOP] :: 음반 추출 끝
    }

    // =================================================================
    // 곡 삭제
    // =================================================================
    private void handleDelete(CommandSender sender, String[] args) {
        // [1] :: 플레이어인가?
        if (!(sender instanceof Player player)) {
            msg.send(sender, "player-only");
            return;
        }
        // [2] :: 이름 arguments 가 설정되어 있는가?
        if (args.length < 2) {
            msg.send(sender, "command.delete-usage");
            return;
        }
        // [3] :: 곡이 존재하는가?
        Song song = resolveOwned(player, joinName(args));
        if (song == null) {
            msg.send(sender, "command.not-found", "name", joinName(args));
            return;
        }
        // [4] :: 삭제 시도. 파일 삭제 실패 시 캐시도 유지된다
        String name = song.name();
        if (storage.delete(song)) {
            msg.send(sender, "command.delete-success", "name", name);
        } else {
            msg.send(sender, "command.delete-fail", "name", name);
        }
    }

    // =================================================================
    // 관리자
    // =================================================================
    private void handleAdmin(CommandSender sender, String[] args) {
        // [1] :: 관리자 권한이 있는가?
        if (!sender.hasPermission(PERM_ADMIN)) {
            sendHelp(sender);
            return;
        }
        // [2] :: 관리자 서브커맨드가 설정되어 있는가?
        if (args.length < 2) {
            sendAdminHelp(sender);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "import", "임포트" -> handleImport(sender, args);
            case "reload", "리로드" -> {
                if (plugin instanceof org.rhsd.musicStudio.MusicStudio ms) {
                    ms.reloadAll();
                } else {
                    storage.loadAll();
                }
                msg.send(sender, "admin.reload-success");
            }
            default -> sendAdminHelp(sender);
        }
    }

    // =================================================================
    // NBS 임포트
    // =================================================================
    private void handleImport(CommandSender sender, String[] args) {
        File dir = new File(plugin.getDataFolder(), "import");
        // [1] :: 파일명 arguments 가 설정되어 있는가? 없으면 파일 목록만
        if (args.length < 3) {
            msg.send(sender, "admin.import-usage");
            listImportFiles(sender, dir);
            return;
        }
        // [2] :: 경로 탈출 시도인가? (path traversal 차단)
        String fileName = args[2];
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            msg.send(sender, "no-permission");
            return;
        }
        if (!fileName.toLowerCase().endsWith(".nbs")) fileName += ".nbs";
        // [3] :: import 폴더가 준비되어 있는가?
        if (!dir.exists() && !dir.mkdirs()) {
            msg.send(sender, "admin.import-fail", "error", "import 폴더를 만들 수 없습니다");
            return;
        }
        // [4] :: 파일이 존재하는가?
        File file = new File(dir, fileName);
        if (!file.exists()) {
            msg.send(sender, "admin.import-no-file", "file", fileName);
            listImportFiles(sender, dir);
            return;
        }
        // [5] PASSED :: 임포트 실행
        try {
            String base = fileName.substring(0, fileName.length() - 4);
            NbsImporter.Result result = NbsImporter.importFile(file, base, maxLayers());
            Song song = result.song();
            // 임포트 곡은 서버 소유(owner=null). 곡명은 NBS 에서 온 값이라 길이를 자른 뒤 유일화한다
            String name = song.name();
            if (name.length() > MAX_NAME_LENGTH) {
                name = name.substring(0, MAX_NAME_LENGTH);
            }
            song.setName(storage.uniqueName(null, name));
            storage.register(song);
            msg.send(sender, "admin.import-success", "name", song.name());
            for (String warning : result.warnings()) {
                msg.send(sender, "admin.import-warning", "warning", warning);
            }
        } catch (IOException ex) {
            msg.send(sender, "admin.import-fail", "error", ex.getMessage());
            plugin.getLogger().warning("NBS 임포트 실패(" + fileName + "): " + ex.getMessage());
        }
        // [STOP] :: 임포트 끝
    }

    private void listImportFiles(CommandSender sender, File dir) {
        File[] files = dir.exists()
                ? dir.listFiles((d, n) -> n.toLowerCase().endsWith(".nbs")) : null;
        if (files == null || files.length == 0) {
            msg.send(sender, "admin.import-no-files");
            return;
        }
        msg.send(sender, "admin.import-list-header");
        for (File file : files) {
            msg.send(sender, "admin.import-list-entry", "file", file.getName());
        }
    }

    // =================================================================
    // 도움말
    // =================================================================
    private void sendHelp(CommandSender sender) {
        msg.send(sender, "command.help-header");
        sender.sendMessage(msg.raw("command.help-create"));
        sender.sendMessage(msg.raw("command.help-list"));
        sender.sendMessage(msg.raw("command.help-open"));
        sender.sendMessage(msg.raw("command.help-rename"));
        sender.sendMessage(msg.raw("command.help-disc"));
        sender.sendMessage(msg.raw("command.help-delete"));
    }

    private void sendAdminHelp(CommandSender sender) {
        msg.send(sender, "admin.help-header");
        sender.sendMessage(msg.raw("admin.help-import"));
        sender.sendMessage(msg.raw("admin.help-reload"));
    }

    private boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.length() <= MAX_NAME_LENGTH;
    }
}

// 컴플리트
