# MusicStudio

In-game music composition studio — compose songs in a tracker-style GUI, press them to music discs, and play them anywhere.

> 🌏 한국어 문서: [README.ko.md](README.ko.md)

## Features

- **Tracker-style editor** inside a 54-slot inventory GUI — a layers × ticks grid where you place, move, and delete notes
- **16 vanilla note block instruments**, **25 pitches** (F#3–F#5, two octaves)
- Per-layer **instrument**, **volume**, and **mute** control
- Adjustable **tempo** (ticks per cell)
- **Copy / paste** and in-editor **preview playback** (only you hear it)
- **Press songs to music discs** — right-click to play; the music follows the holder like a portable radio
- Optional **extraction cost**, **ItemsAdder** custom item support, and vanilla `glint` / `unbreakable` / `custom-model-data`
- **Import Note Block Studio `.nbs` files**
- **Per-player song ownership** — you only see and edit your own songs (admins see all)
- **Full i18n** (`ko_kr` / `en_us`); every string is configurable with MiniMessage
- **Update notifications** — console and admins are notified when a newer GitHub release exists
- **Wide version support** — Paper / Purpur **1.20.x – 1.21.x and 26.x**

## Supported Versions

One jar is provided per version series under `build/libs/Upload/<series>/`. Each jar runs on **both Paper and Purpur**.

| Folder | Supported server versions |
|--------|---------------------------|
| `1.20` | 1.20 – 1.20.6 (all 1.20.x) |
| `1.21` | 1.21 – 1.21.11 (all 1.21.x) |
| `26.1.2` | 26.1.2 only |

> The Java requirement follows the server: 1.20–1.20.4 need Java 17, 1.20.5+ need Java 21, 26.x needs Java 25.

## Permissions

| Permission | Default | Grants |
|------------|---------|--------|
| `musicstudio.use` | `true` (everyone) | Create, edit, and play your own songs |
| `musicstudio.admin` | `op` | Import `.nbs`, reload config, access **all** players' songs, bypass the per-player song limit |

## Commands

Base command: `/음악스튜디오` &nbsp;(aliases: `/음스`, `/ms`, `/musicstudio`). Both Korean and English subcommands work.

### Song Management
| Command | Description |
|---------|-------------|
| `/ms create <name>` &nbsp;(`생성`) | Create a new empty song |
| `/ms list` &nbsp;(`목록`) | List your songs (all songs, if admin) |
| `/ms open <name>` &nbsp;(`열기`) | Open the song in the editor |
| `/ms rename <old> <new>` &nbsp;(`이름변경`) | Rename a song |
| `/ms disc <name>` &nbsp;(`음반`) | Press the song onto a music disc |
| `/ms delete <name>` &nbsp;(`삭제`) | Delete a song |
| `/ms help` &nbsp;(`도움말`) | Show help |

### Admin
| Command | Description |
|---------|-------------|
| `/ms admin import <file.nbs>` &nbsp;(`관리자 임포트`) | Import an `.nbs` from the import folder (no argument = list available files) |
| `/ms admin reload` &nbsp;(`관리자 리로드`) | Reload config, text, and song data |

> Names may contain spaces (joined with `_`) and are limited to 32 characters.

## Editor Controls

The editor is an inventory GUI. Rows are **layers**, columns are **ticks**; the bottom rows hold the ruler and control bar.

| Target | Action |
|--------|--------|
| **Empty cell** | Left-click — create a note at the current pitch |
| **Note** | Left: select · Right: +1 semitone · Shift+Right: −1 semitone · Shift+Left: delete |
| **Layer header** | Left: change instrument · Right: toggle mute · Shift+Left/Right: volume ±10% · Q (drop): delete layer |
| **Info (jukebox)** | Left: pitch up · Right: pitch down · Shift: by octave — sets the pitch used for new notes |
| **Ruler (paper)** | Click: put the existing editor cursor on that tick · click again: clear it |
| **◀ / ▶ tick** | Scroll one tick · Shift: jump 8 ticks |
| **▲ / ▼ layer** | Scroll layers up / down |
| **▶ preview / ■ stop** | Preview the song to yourself / stop |
| **Settings** | Tempo and disc extraction |
| **Tick ranges** | On an editor cell: F sets range start/end; 1 copies selected ticks; 2 pastes at the hovered tick; 3 clears; Q cancels a pending start. Empty ticks, scrolling, and non-contiguous ranges are supported. |
| **Music disc item** | Right-click: play · right-click again: stop |

**Instruments (16):** Harp, Bass, Bass Drum, Snare, Hat, Guitar, Flute, Bell, Chime, Xylophone, Iron Xylophone, Cow Bell, Didgeridoo, Bit, Banjo, Pling.

## Configuration

`plugins/MusicStudio/config.yml`:

```yaml
language: ko_kr          # ko_kr (Korean) or en_us (English); untranslated strings fall back to ko_kr

update-check:
  enabled: true          # notify console + admins when a newer GitHub release exists

limits:
  max-ticks: 4096        # maximum song length in cells
  max-songs-per-player: 30   # per-player song cap (musicstudio.admin is exempt)
  max-layers: 9          # layers per song (1–256)

disc:
  material: MUSIC_DISC_5 # base vanilla music disc item
  itemsadder-id: ""      # ItemsAdder custom item id (e.g. "myitems:music_disc"); falls back to material
  glint: false           # enchantment glint
  unbreakable: false
  custom-model-data: 0   # resource-pack model id; 0 = none
  cost:
    enabled: false       # charge an item to extract a disc
    item: EMERALD
    amount: 5
```

Display text lives in `messages_<lang>.yml` and `gui_<lang>.yml` (MiniMessage syntax) — edit them to fully reskin the plugin.

## Examples

Create a song called `mysong`, open the editor, then press it to a disc:
```
/ms create mysong
/ms open mysong
/ms disc mysong
```

Import a Note Block Studio file (`plugins/MusicStudio/import/song.nbs`):
```
/ms admin import song
```

Switch the server to English: set `language: en_us` in `config.yml`, then:
```
/ms admin reload
```

## Installation

1. Pick the jar matching your Minecraft version series from `build/libs/Upload/<series>/`.
2. Drop it into your server's `plugins/` folder.
3. Restart the server (Paper or Purpur).

## Building from Source

```bash
./gradlew build                # standard jar (build/libs)
./gradlew buildAllVersions     # one jar per version series into build/libs/Upload/<series>/
```

Requires JDK 21 and JDK 25 (the latter only for the 26.x target).
