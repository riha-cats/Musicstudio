package org.rhsd.musicStudio;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.rhsd.musicStudio.command.MsCommand;
import org.rhsd.musicStudio.command.MsTabCompleter;
import org.rhsd.musicStudio.disc.DiscListener;
import org.rhsd.musicStudio.disc.DiscManager;
import org.rhsd.musicStudio.gui.GuiListener;
import org.rhsd.musicStudio.gui.GuiManager;
import org.rhsd.musicStudio.integration.ItemsAdderHook;
import org.rhsd.musicStudio.playback.PlaybackManager;
import org.rhsd.musicStudio.storage.SongStorage;
import org.rhsd.musicStudio.update.UpdateChecker;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

// =================================================================
// MusicStudio 플러그인 본체
// =================================================================
public final class MusicStudio extends JavaPlugin {

    private MessageManager messageManager;
    private GuiConfig guiConfig;
    private SongStorage songStorage;
    private PlaybackManager playbackManager;
    private DiscManager discManager;
    private GuiManager guiManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        // [0] :: config 준비 + 버전 마이그레이션
        saveDefaultConfig();
        migrateConfig();

        // [1] :: 텍스트 로더. config 의 language 를 보고 로케일 파일을 고른다
        messageManager = new MessageManager(this);
        guiConfig = new GuiConfig(this);

        // [2] :: 곡 데이터 로드
        songStorage = new SongStorage(this);
        songStorage.loadAll();

        // [3] :: 매니저 조립
        playbackManager = new PlaybackManager(this);
        ItemsAdderHook itemsAdder = new ItemsAdderHook(this);
        discManager = new DiscManager(this, guiConfig, itemsAdder);
        guiManager = new GuiManager(this, songStorage, discManager, messageManager, guiConfig);

        // [4] :: 리스너 등록
        updateChecker = new UpdateChecker(this, messageManager);
        getServer().getPluginManager().registerEvents(new GuiListener(guiManager), this);
        getServer().getPluginManager().registerEvents(
                new DiscListener(discManager, playbackManager, songStorage, messageManager), this);
        getServer().getPluginManager().registerEvents(updateChecker, this);

        // [5] :: 명령어 등록
        PluginCommand msCommand = getCommand("음악스튜디오");
        if (msCommand != null) {
            msCommand.setExecutor(new MsCommand(
                    this, songStorage, guiManager, discManager, messageManager));
            msCommand.setTabCompleter(new MsTabCompleter(songStorage));
        } else {
            getLogger().severe("'음악스튜디오' 명령을 등록하지 못했습니다. plugin.yml을 확인하세요.");
        }

        // [6] :: 새 버전 확인 (비동기)
        updateChecker.check();

        getLogger().info("MusicStudio 활성화 완료.");
        // [STOP] :: onEnable 끝
    }

    @Override
    public void onDisable() {
        if (playbackManager != null) playbackManager.shutdownAll();
        if (guiManager != null) guiManager.shutdown();
        if (songStorage != null) songStorage.saveAll();
        getLogger().info("MusicStudio 비활성화. 곡 데이터 저장 완료.");
    }

    // 운영 중 리로드 (/ms admin reload)
    // config, 텍스트(messages/gui), disc 설정, 곡 데이터를 다시 읽는다
    // language·limits.max-ticks·max-songs-per-player 는 사용 시점에 config 를 직접 읽으므로
    // 바로 반영되고, max-layers 는 에디터를 새로 열 때 반영된다
    public void reloadAll() {
        reloadConfig();
        migrateConfig();
        messageManager.reload();
        guiConfig.reload();
        discManager.reload();
        songStorage.loadAll();
        updateChecker.check();
    }

    // 번들 config 의 config-version 이 저장본보다 높다면? 누락 키를 채워 넣고 버전 갱신
    private void migrateConfig() {
        YamlConfiguration defaults;
        try (InputStream in = getResource("config.yml")) {
            if (in == null) return;
            defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            getLogger().warning("config.yml 기본값을 읽지 못했습니다: " + ex.getMessage());
            return;
        }
        int bundledVersion = defaults.getInt("config-version", 1);
        int savedVersion = getConfig().getInt("config-version", 0);
        if (savedVersion < bundledVersion) {
            getConfig().setDefaults(defaults);
            getConfig().options().copyDefaults(true);
            getConfig().set("config-version", bundledVersion);
            saveConfig();
            getLogger().info("config.yml 업데이트 완료 (v"
                    + savedVersion + " → v" + bundledVersion + ")");
        }
    }

    public MessageManager messageManager() { return messageManager; }
    public GuiConfig guiConfig() { return guiConfig; }
    public SongStorage songStorage() { return songStorage; }
    public GuiManager guiManager() { return guiManager; }
    public PlaybackManager playbackManager() { return playbackManager; }
    public DiscManager discManager() { return discManager; }
    public UpdateChecker updateChecker() { return updateChecker; }
}

// 컴플리트
