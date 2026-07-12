package org.rhsd.musicStudio.gui;

import org.junit.jupiter.api.Test;
import org.rhsd.musicStudio.model.Song;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EditorSessionClipboardTest {
    private EditorSession session() {
        return new EditorSession(UUID.randomUUID(), new Song("id", "song", UUID.randomUUID()), 9);
    }

    @Test void rangeAnchorDoesNotChangeSelectionAndPersistsThroughScroll() {
        EditorSession s = session();
        s.replaceCellSelection(Set.of(new EditorSession.SelectedCell(2, 0)));
        s.setRangeAnchor(10, 0);
        s.scrollTick(20);
        assertEquals(Set.of(new EditorSession.SelectedCell(2, 0)), s.selectedCells());
        assertEquals(10, s.rangeAnchorTick());
    }

    @Test void forwardReverseAndSingleTickRangesWork() {
        EditorSession s = session();
        assertEquals(EditorSession.RangeAction.ADDED, s.toggleTickRange(0, 10, 15));
        assertEquals(Set.of(10, 11, 12, 13, 14, 15), s.selectedTicks());
        s.clearTickSelection();
        s.toggleTickRange(0, 15, 10);
        assertEquals(Set.of(10, 11, 12, 13, 14, 15), s.selectedTicks());
        s.clearTickSelection();
        s.toggleTickRange(0, 7, 7);
        assertEquals(Set.of(7), s.selectedTicks());
    }

    @Test void fullRangeRemovesPartialOverlapExpandsAndDisjointRangesRemain() {
        EditorSession s = session();
        s.toggleTickRange(0, 10, 20);
        assertEquals(EditorSession.RangeAction.REMOVED, s.toggleTickRange(0, 13, 15));
        assertFalse(s.isCellSelected(14, 0));
        assertEquals(EditorSession.RangeAction.ADDED, s.toggleTickRange(0, 13, 22));
        s.toggleTickRange(0, 30, 31);
        assertTrue(s.selectedTicks().containsAll(Set.of(10, 20, 22, 30, 31)));
    }

    @Test void selectionAndAnchorCanBeClearedIndependently() {
        EditorSession s = session();
        s.toggleTickRange(0, 1, 3);
        s.setRangeAnchor(9, 0);
        s.clearRangeAnchor();
        assertEquals(Set.of(1, 2, 3), s.selectedTicks());
        s.setRangeAnchor(9, 0);
        s.clearTickSelection();
        s.clearRangeAnchor();
        assertTrue(s.selectedTicks().isEmpty());
        assertFalse(s.hasRangeAnchor());
    }

    @Test void clipboardRetainsEmptyTickOffsetsAndIsReadOnly() {
        EditorSession s = session();
        s.setClipboard(List.of(new EditorSession.ClipboardNote(0, 0, 5)), List.of(
                new EditorSession.ClipboardCell(0, 0), new EditorSession.ClipboardCell(1, 0),
                new EditorSession.ClipboardCell(5, 0)));
        assertEquals(Set.of(0, 1, 5), s.clipboardTickOffsets());
        assertThrows(UnsupportedOperationException.class, () -> s.clipboardTickOffsets().add(9));
    }

    @Test void pastePreservesGapsAndSkipsTickAndLayerBounds() {
        List<EditorSession.ClipboardNote> notes = List.of(
                new EditorSession.ClipboardNote(0, 0, 5),
                new EditorSession.ClipboardNote(2, 1, 6),
                new EditorSession.ClipboardNote(6, 0, 7),
                new EditorSession.ClipboardNote(1, -1, 8),
                new EditorSession.ClipboardNote(1, 9, 9));
        List<EditorSession.PasteNote> out = EditorSession.resolvePaste(notes, 4090, 4096, 2);
        assertEquals(List.of(new EditorSession.PasteNote(4090, 0, 5),
                new EditorSession.PasteNote(4092, 1, 6)), out);
    }

    @Test void layerDeletionAdjustsOnlySingleNoteSelection() {
        EditorSession s = session();
        s.song().addLayer();
        s.select(4, 1);
        s.toggleTickRange(1, 4, 6);
        s.setRangeAnchor(10, 1);
        s.onLayerDeleted(0);
        assertEquals(0, s.selectedLayer());
        assertTrue(s.isCellSelected(5, 0));
        assertEquals(10, s.rangeAnchorTick());
    }

    @Test void selectingOneLayerDoesNotSelectOtherLayers() {
        EditorSession s = session();
        s.song().addLayer();
        s.toggleTickRange(0, 10, 12);
        assertTrue(s.isCellSelected(11, 0));
        assertFalse(s.isCellSelected(11, 1));
    }
}
