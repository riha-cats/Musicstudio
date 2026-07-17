package org.rhsd.musicStudio.gui;

import org.junit.jupiter.api.Test;
import org.rhsd.musicStudio.model.Layer;
import org.rhsd.musicStudio.model.Instrument;
import org.rhsd.musicStudio.model.Song;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EditorSessionLayerMoveTest {
    @Test void remapsSingleSelectionClipboardAndTickRangeAfterLayerMove() {
        Song song = new Song("id", "test", UUID.randomUUID());
        for (int i = 1; i < 4; i++) song.addLayer(new Layer(Instrument.HARP, "L" + i));
        song.setNote(20, 1, 12);
        EditorSession session = new EditorSession(UUID.randomUUID(), song, 9);
        session.select(20, 1);
        session.setRangeAnchor(7, 2);
        session.replaceCellSelection(List.of(new EditorSession.SelectedCell(8, 2)));
        session.setClipboard(List.of(new EditorSession.ClipboardNote(2, 1, 9)),
                List.of(new EditorSession.ClipboardCell(2, 1)));

        assertTrue(song.moveLayer(1, 3));
        session.remapAfterLayerMove(1, 3);
        assertEquals(3, session.selectedLayer());
        assertEquals(20, session.selectedTick());
        assertEquals(new EditorSession.ClipboardNote(2, 3, 9), session.clipboardNotes().getFirst());
        assertEquals(new EditorSession.ClipboardCell(2, 3), session.clipboardCells().getFirst());
        assertEquals(2, session.clipboardTickOffsets().getFirst());
        // 옛 레이어 2 는 이동 후 인덱스 1 로 내려간다. 범위 선택과 시작점도 같이 따라와야 한다
        assertEquals(7, session.rangeAnchorTick());
        assertEquals(1, session.rangeAnchorLayer());
        assertTrue(session.isCellSelected(8, 1));
        assertFalse(session.isCellSelected(8, 2));
    }

    @Test void deletionDropsSelectedCellsOnDeletedLayer() {
        Song song = new Song("id", "test", UUID.randomUUID());
        song.addLayer(new Layer(Instrument.HARP, "B"));
        song.addLayer(new Layer(Instrument.HARP, "C"));
        EditorSession session = new EditorSession(UUID.randomUUID(), song, 9);
        session.toggleTickRange(1, 4, 6);
        session.remapAfterLayerDeletion(1);
        assertTrue(session.selectedCells().isEmpty());
    }

    @Test void deletionDropsClipboardCellsOnDeletedLayerAndRebuildsTickOffsets() {
        Song song = new Song("id", "test", UUID.randomUUID());
        song.addLayer(new Layer(Instrument.HARP, "B"));
        song.addLayer(new Layer(Instrument.HARP, "C"));
        EditorSession session = new EditorSession(UUID.randomUUID(), song, 9);
        session.setClipboard(List.of(new EditorSession.ClipboardNote(3, 2, 5)),
                List.of(new EditorSession.ClipboardCell(1, 1), new EditorSession.ClipboardCell(3, 2)));
        assertEquals(List.of(1, 3), List.copyOf(session.clipboardTickOffsets()));

        session.remapAfterLayerDeletion(1);
        // 레이어1 셀은 버리고 레이어2 셀은 1 로 당긴다. 오프셋 1 은 근거가 사라졌으니 같이 빠진다
        assertEquals(List.of(new EditorSession.ClipboardCell(3, 1)), session.clipboardCells());
        assertEquals(List.of(3), List.copyOf(session.clipboardTickOffsets()));
    }

    @Test void deletionClearsRangeAnchorOnDeletedLayer() {
        Song song = new Song("id", "test", UUID.randomUUID());
        song.addLayer(new Layer(Instrument.HARP, "B"));
        song.addLayer(new Layer(Instrument.HARP, "C"));
        EditorSession session = new EditorSession(UUID.randomUUID(), song, 9);
        session.setRangeAnchor(10, 1);
        session.remapAfterLayerDeletion(1);
        assertFalse(session.hasRangeAnchor());
    }

    @Test void pendingSourcePersistsAcrossScrollAndDeletionIsCorrected() {
        Song song = new Song("id", "test", UUID.randomUUID());
        for (int i = 1; i < 9; i++) song.addLayer(new Layer(Instrument.HARP, "L" + i));
        EditorSession session = new EditorSession(UUID.randomUUID(), song, 9);
        session.beginLayerMove(6);
        session.scrollLayer(4);
        assertEquals(6, session.movingLayerIndex());
        session.remapAfterLayerDeletion(2);
        assertEquals(5, session.movingLayerIndex());
        session.remapAfterLayerDeletion(5);
        assertFalse(session.hasMovingLayer());
    }

    @Test void deletionDropsClipboardNotesForDeletedLayerAndShiftsHigherLayers() {
        Song song = new Song("id", "test", UUID.randomUUID());
        song.addLayer(new Layer(Instrument.HARP, "B"));
        song.addLayer(new Layer(Instrument.HARP, "C"));
        EditorSession session = new EditorSession(UUID.randomUUID(), song, 9);
        session.setClipboard(List.of(
                new EditorSession.ClipboardNote(1, 1, 4),
                new EditorSession.ClipboardNote(3, 2, 5)), List.of());
        session.remapAfterLayerDeletion(1);
        assertEquals(List.of(new EditorSession.ClipboardNote(3, 1, 5)), session.clipboardNotes());
    }
}
