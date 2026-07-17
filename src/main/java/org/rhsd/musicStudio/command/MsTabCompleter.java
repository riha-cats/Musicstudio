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
// 탭 자동완성 (영문 서브커맨드)
// =================================================================
// 서브커맨드는 영문으로 제안한다. 한글(생성/목록 등)도 normalizeSub 가 그대로 받으므로
// 직접 치면 여전히 동작한다 — 제안만 영문일 뿐이다.
// 곡 이름은 본인 곡만 제안. 관리자 메뉴는 권한자에게만
public final class MsTabCompleter implements TabCompleter {

    // 탭에 제안할 서브커맨드(영문). 전부 normalizeSub 가 자기 자신으로 받는 표준형이라야 한다
    // (CommandRoutingTest 가 검증). 한글은 제안만 안 될 뿐 쳐서 넣으면 여전히 동작한다
    static final List<String> USER_SUBS =
            List.of("create", "list", "open", "rename", "disc", "delete", "help");

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
                subs.add("admin");
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
                return filter(List.of("import", "reload"), args[1]);
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
