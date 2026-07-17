package org.rhsd.musicStudio.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SongLayerMoveTest {
    private Song song() {
        Song song = new Song("id", "test", UUID.randomUUID());
        song.layer(0).setName("A");
        for (String name : List.of("B", "C", "D")) {
            Layer layer = new Layer(Instrument.HARP, name);
            song.addLayer(layer);
        }
        for (int layer = 0; layer < 4; layer++) song.setNote(10 + layer, layer, 5 + layer);
        return song;
    }

    @Test void movesDownToExactDestinationAndReindexesNotes() {
        Song song = song();
        Layer moved = song.layer(1);
        moved.setInstrument(Instrument.FLUTE);
        moved.setVolume(1.7f);
        moved.setMuted(true);
        int length = song.length();

        assertTrue(song.moveLayer(1, 3));
        assertEquals(List.of("A", "C", "D", "B"), song.layers().stream().map(Layer::name).toList());
        assertSame(moved, song.layer(3));
        assertEquals(Instrument.FLUTE, song.layer(3).instrument());
        assertEquals(1.7f, song.layer(3).volume());
        assertTrue(song.layer(3).muted());
        assertEquals(4, song.noteCount());
        assertEquals(length, song.length());
        assertEquals(6, song.getNote(11, 3).key());
        assertEquals(7, song.getNote(12, 1).key());
        assertEquals(8, song.getNote(13, 2).key());
    }

    @Test void movesUpAndAcrossEndpoints() {
        Song up = song();
        assertTrue(up.moveLayer(2, 1));
        assertEquals(List.of("A", "C", "B", "D"), up.layers().stream().map(Layer::name).toList());
        assertEquals(7, up.getNote(12, 1).key());
        assertEquals(6, up.getNote(11, 2).key());

        Song firstLast = song();
        assertTrue(firstLast.moveLayer(0, 3));
        assertEquals(List.of("B", "C", "D", "A"), firstLast.layers().stream().map(Layer::name).toList());
        assertTrue(firstLast.moveLayer(3, 0));
        assertEquals(List.of("A", "B", "C", "D"), firstLast.layers().stream().map(Layer::name).toList());
    }

    @Test void rejectsInvalidAndSameIndicesWithoutChanges() {
        Song song = song();
        assertFalse(song.moveLayer(1, 1));
        assertFalse(song.moveLayer(-1, 1));
        assertFalse(song.moveLayer(1, -1));
        assertFalse(song.moveLayer(song.layerCount(), 1));
        assertFalse(song.moveLayer(1, song.layerCount()));
        assertEquals(List.of("A", "B", "C", "D"), song.layers().stream().map(Layer::name).toList());
        assertEquals(4, song.noteCount());
    }

    @Test void remappingRuleCoversBothDirections() {
        assertEquals(List.of(0, 3, 1, 2),
                List.of(0, 1, 2, 3).stream().map(i -> Song.remapLayerIndex(i, 1, 3)).toList());
        assertEquals(List.of(0, 2, 3, 1),
                List.of(0, 1, 2, 3).stream().map(i -> Song.remapLayerIndex(i, 3, 1)).toList());
    }
}
