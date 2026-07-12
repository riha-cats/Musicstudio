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
// 탭 자동완성 (한국어 우선)
// =================================================================
// 곡 이름은 본인 곡만 제안. 관리자 메뉴는 권한자에게만
public final class MsTabCompleter implements TabCompleter {

    private static final List<String> USER_SUBS =
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

        // [1] :: 첫 번째 인자 — 서브커맨드 목록
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(USER_SUBS);
            if (sender.hasPermission(MsCommand.PERM_ADMIN)) {
                subs.add("관리자");
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
                return filter(List.of("임포트", "리로드"), args[1]);
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
