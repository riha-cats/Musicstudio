package org.rhsd.musicStudio.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rhsd.musicStudio.model.Song;
import org.rhsd.musicStudio.storage.SongStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// =================================================================
// 탭 자동완성 (별칭 언어에 맞춰 제안)
// =================================================================
// 부른 별칭이 한글(음악스튜디오/음스)이면 한국어, 영문(ms/musicstudio)이면 영문으로 제안한다.
// 어느 쪽으로 제안하든 normalizeSub 가 한/영을 다 받으므로 반대 언어를 쳐도 동작한다.
// 곡 이름은 본인 곡만 제안. 관리자 메뉴는 권한자에게만
public final class MsTabCompleter implements TabCompleter {

    // 두 언어의 서브커맨드 제안. 순서를 맞춰 둔다(같은 인덱스가 같은 기능).
    // 전부 normalizeSub 가 표준형으로 받는 값이라야 한다 (CommandRoutingTest 가 검증)
    static final List<String> USER_SUBS =
            List.of("create", "list", "open", "rename", "disc", "delete", "help");
    static final List<String> USER_SUBS_KO =
            List.of("생성", "목록", "열기", "이름변경", "음반", "삭제", "도움말");

    private final SongStorage storage;

    public MsTabCompleter(SongStorage storage) {
        this.storage = storage;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(MsCommand.PERM_USE)) {
            return List.of();
        }

        // 부른 별칭이 한글이면 한국어로, 아니면 영문으로 제안한다
        boolean ko = isKoreanLabel(label);

        // [1] :: 첫 번째 인자 — 서브커맨드 목록
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(ko ? USER_SUBS_KO : USER_SUBS);
            if (sender.hasPermission(MsCommand.PERM_ADMIN)) {
                subs.add(ko ? "관리자" : "admin");
            }
            return filter(subs, args[0]);
        }

        // [2] :: 두 번째 인자 — 곡 이름 또는 관리자 서브커맨드
        if (args.length == 2) {
            String sub = MsCommand.normalizeSub(args[0]);
            if (sub.equals("open") || sub.equals("delete") || sub.equals("disc") || sub.equals("rename")) {
                return filter(ownSongNames(sender), args[1]);
            }
            if (sub.equals("admin") && sender.hasPermission(MsCommand.PERM_ADMIN)) {
                return filter(ko ? List.of("임포트", "리로드") : List.of("import", "reload"), args[1]);
            }
        }

        return List.of();
    }

    // 본인 소유 곡 이름. 관리자/CONSOLE 은 전체
    private List<String> ownSongNames(CommandSender sender) {
        List<Song> songs = (sender instanceof Player p && !p.hasPermission(MsCommand.PERM_ADMIN))
                ? storage.getByOwner(p.getUniqueId())
                : storage.all();
        List<String> names = new ArrayList<>();
        for (Song song : songs) {
            names.add(song.name());
        }
        return names;
    }

    // 부른 별칭에 한글 음절이 섞였는가. 음악스튜디오·음스 는 true, ms·musicstudio 는 false.
    // 별칭이 늘어도(한글이면) 그대로 잡히도록 목록 대조 대신 한글 음절 범위로 본다
    static boolean isKoreanLabel(String label) {
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (c >= '가' && c <= '힣') {
                return true;
            }
        }
        return false;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}

// 컴플리트
