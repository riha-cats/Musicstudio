package org.rhsd.musicStudio.gui;

import org.rhsd.musicStudio.model.Note;
import org.rhsd.musicStudio.model.Song;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.UUID;

public final class EditorSession {

    public static final int VISIBLE_TICKS = 8;
    public static final int VISIBLE_LAYERS = 4;

    public record ClipboardNote(int relativeTick, int layer, int key) {}
    public record SelectedCell(int tick, int layer) implements Comparable<SelectedCell> {
        @Override public int compareTo(SelectedCell other) {
            int byTick = Integer.compare(tick, other.tick);
            return byTick != 0 ? byTick : Integer.compare(layer, other.layer);
        }
    }
    public record ClipboardCell(int relativeTick, int layer) {}
    public record PasteNote(int tick, int layer, int key) {}
    public enum RangeAction { ADDED, REMOVED }

    private final UUID player;
    private final Song song;
    private final int maxLayers;
    private int tickOffset;
    private int layerOffset;
    private int currentKey = 12;
    private Integer selectedTick;
    private Integer selectedLayer;
    private int cursorTick = -1;
    private int playingTick = -1;

    private final NavigableSet<SelectedCell> selectedCells = new TreeSet<>();
    private Integer rangeAnchorTick;
    private Integer rangeAnchorLayer;
    private final List<ClipboardNote> clipboardNotes = new ArrayList<>();
    private final NavigableSet<Integer> clipboardTickOffsets = new TreeSet<>();
    private final List<ClipboardCell> clipboardCells = new ArrayList<>();

    public EditorSession(UUID player, Song song, int maxLayers) {
        this.player = player;
        this.song = song;
        this.maxLayers = Song.clampLayerLimit(maxLayers);
    }

    public UUID player() { return player; }
    public Song song() { return song; }
    public int maxLayers() { return maxLayers; }
    public int tickOffset() { return tickOffset; }
    public void scrollTick(int delta) { tickOffset = Math.max(0, tickOffset + delta); }
    public void setTickOffset(int offset) { tickOffset = Math.max(0, offset); }
    public int layerOffset() { return layerOffset; }

    public void scrollLayer(int delta) {
        int extra = song.layerCount() < maxLayers ? 1 : 0;
        int max = Math.max(0, song.layerCount() + extra - VISIBLE_LAYERS);
        layerOffset = Math.max(0, Math.min(max, layerOffset + delta));
    }

    public int currentKey() { return currentKey; }
    public void setCurrentKey(int key) { currentKey = Note.clampKey(key); }
    public boolean isSelected(int tick, int layer) {
        return selectedTick != null && selectedLayer != null
                && selectedTick == tick && selectedLayer == layer;
    }
    public Integer selectedTick() { return selectedTick; }
    public Integer selectedLayer() { return selectedLayer; }
    public void select(int tick, int layer) {
        selectedTick = tick;
        selectedLayer = layer;
        cursorTick = tick;
    }
    public void clearSelection() { selectedTick = null; selectedLayer = null; }

    public void onLayerDeleted(int deletedLayer) {
        if (selectedLayer != null) {
            if (selectedLayer == deletedLayer) clearSelection();
            else if (selectedLayer > deletedLayer) selectedLayer--;
        }
        TreeSet<SelectedCell> adjusted = new TreeSet<>();
        for (SelectedCell cell : selectedCells) {
            if (cell.layer() == deletedLayer) continue;
            adjusted.add(new SelectedCell(cell.tick(), cell.layer() > deletedLayer ? cell.layer() - 1 : cell.layer()));
        }
        selectedCells.clear();
        selectedCells.addAll(adjusted);
        if (rangeAnchorLayer != null) {
            if (rangeAnchorLayer == deletedLayer) clearRangeAnchor();
            else if (rangeAnchorLayer > deletedLayer) rangeAnchorLayer--;
        }
    }

    public int cursorTick() { return cursorTick; }
    public void toggleCursor(int tick) { cursorTick = cursorTick == tick ? -1 : tick; }
    public int playingTick() { return playingTick; }
    public void setPlayingTick(int playingTick) { this.playingTick = playingTick; }

    public boolean isCellSelected(int tick, int layer) { return selectedCells.contains(new SelectedCell(tick, layer)); }
    public boolean hasRangeAnchor() { return rangeAnchorTick != null; }
    public Integer rangeAnchorTick() { return rangeAnchorTick; }
    public Integer rangeAnchorLayer() { return rangeAnchorLayer; }
    public void setRangeAnchor(int tick, int layer) { rangeAnchorTick = tick; rangeAnchorLayer = layer; }
    public void clearRangeAnchor() { rangeAnchorTick = null; rangeAnchorLayer = null; }
    public void clearTickSelection() { selectedCells.clear(); }
    public NavigableSet<SelectedCell> selectedCells() {
        return Collections.unmodifiableNavigableSet(selectedCells);
    }
    public NavigableSet<Integer> selectedTicks() {
        TreeSet<Integer> ticks = new TreeSet<>();
        for (SelectedCell cell : selectedCells) ticks.add(cell.tick());
        return Collections.unmodifiableNavigableSet(ticks);
    }
    public void replaceCellSelection(Collection<SelectedCell> cells) {
        selectedCells.clear();
        for (SelectedCell cell : cells)
            if (cell != null && cell.tick() >= 0 && cell.layer() >= 0) selectedCells.add(cell);
    }

    public RangeAction toggleTickRange(int layer, int from, int to) {
        int first = Math.min(from, to);
        int last = Math.max(from, to);
        boolean allSelected = true;
        for (int tick = first; tick <= last; tick++) {
            if (!selectedCells.contains(new SelectedCell(tick, layer))) { allSelected = false; break; }
        }
        for (int tick = first; tick <= last; tick++) {
            SelectedCell cell = new SelectedCell(tick, layer);
            if (allSelected) selectedCells.remove(cell); else selectedCells.add(cell);
        }
        return allSelected ? RangeAction.REMOVED : RangeAction.ADDED;
    }

    public List<ClipboardNote> clipboardNotes() { return List.copyOf(clipboardNotes); }
    public NavigableSet<Integer> clipboardTickOffsets() {
        return Collections.unmodifiableNavigableSet(clipboardTickOffsets);
    }
    public List<ClipboardCell> clipboardCells() { return List.copyOf(clipboardCells); }
    public boolean hasClipboard() { return !clipboardNotes.isEmpty(); }
    public void setClipboard(Collection<ClipboardNote> notes, Collection<ClipboardCell> cells) {
        clipboardNotes.clear();
        clipboardNotes.addAll(notes);
        clipboardCells.clear();
        clipboardCells.addAll(cells);
        clipboardTickOffsets.clear();
        for (ClipboardCell cell : cells) clipboardTickOffsets.add(cell.relativeTick());
    }


    public static List<PasteNote> resolvePaste(Collection<ClipboardNote> clipboard, int baseTick,
                                                int maxTicks, int layerCount) {
        List<PasteNote> out = new ArrayList<>(clipboard.size());
        for (ClipboardNote entry : clipboard) {
            long target = (long) baseTick + entry.relativeTick();
            if (target < 0 || target >= maxTicks || entry.layer() < 0 || entry.layer() >= layerCount) continue;
            out.add(new PasteNote((int) target, entry.layer(), entry.key()));
        }
        return out;
    }
}
