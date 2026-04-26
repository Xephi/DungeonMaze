package com.timvisee.dungeonmaze.generator.mineshaft;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room.DungeonChunkRoomType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineshaftLayoutGeneratorTest {

    @Test
    void generationIsDeterministicForTheSameCell() {
        final LocatedLayout locatedLayout = findLayout(12345L);
        assertNotNull(locatedLayout);

        final MineshaftLayoutGenerator.Layout regenerated = MineshaftLayoutGenerator.generate(12345L, locatedLayout.cellX, locatedLayout.cellZ);
        assertNotNull(regenerated);

        assertEquals(locatedLayout.layout.getStructureId(), regenerated.getStructureId());
        assertEquals(locatedLayout.layout.getLayer(), regenerated.getLayer());
        assertEquals(describe(locatedLayout.layout), describe(regenerated));
    }

    @Test
    void generatedLayoutsCanSpanMultipleChunks() {
        final LocatedLayout locatedLayout = findMultiChunkLayout(12345L);
        assertNotNull(locatedLayout);

        final List<String> chunkCoordinates = new ArrayList<>();
        for(MineshaftLayoutGenerator.RoomReservation room : locatedLayout.layout.getRooms())
            chunkCoordinates.add(room.getChunkX() + "," + room.getChunkZ());

        assertTrue(chunkCoordinates.stream().distinct().count() > 1);
    }

    @Test
    void generatedLayoutsCanSpanMultipleLayers() {
        final LocatedLayout locatedLayout = findMultiLayerLayout(12345L);
        assertNotNull(locatedLayout);

        final long distinctLayers = locatedLayout.layout.getRooms().stream()
                .map(MineshaftLayoutGenerator.RoomReservation::getLayer)
                .distinct()
                .count();

        assertTrue(distinctLayers > 1);
    }

    @Test
    void generatedLayoutsCanCreateLargeHubRooms() {
        final LocatedLayout locatedLayout = findHubLayout(12345L);
        assertNotNull(locatedLayout);

        final long broadRooms = locatedLayout.layout.getRooms().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        MineshaftLayoutGenerator.RoomReservation::getLayer,
                        java.util.stream.Collectors.toList()
                ))
                .values()
                .stream()
                .flatMap(List::stream)
                .filter(room -> Integer.bitCount(room.getConnectionMask()) >= 3)
                .count();

        assertTrue(broadRooms > 0);
    }

    @Test
    void generatedLayoutsCanCreateSpecialMineshaftRooms() {
        final LocatedLayout locatedLayout = findSpecialRoomLayout(12345L);
        assertNotNull(locatedLayout);

        final boolean hasSpiderDen = locatedLayout.layout.getRooms().stream()
                .anyMatch(room -> room.getType() == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN);
        final boolean hasCaveIn = locatedLayout.layout.getRooms().stream()
                .anyMatch(room -> room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN);

        assertTrue(hasSpiderDen || hasCaveIn);
    }

    @Test
    void generatedLayoutsCanCreateMultipleStorageRooms() {
        final LocatedLayout locatedLayout = findMultiStorageLayout(12345L);
        assertNotNull(locatedLayout);

        final long storageRooms = locatedLayout.layout.getRooms().stream()
                .filter(room -> room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE)
                .count();

        assertTrue(storageRooms >= 2);
    }

    private LocatedLayout findLayout(long seed) {
        for(int cellX = -12; cellX <= 12; cellX++) {
            for(int cellZ = -12; cellZ <= 12; cellZ++) {
                final MineshaftLayoutGenerator.Layout layout = MineshaftLayoutGenerator.generate(seed, cellX, cellZ);
                if(layout != null)
                    return new LocatedLayout(cellX, cellZ, layout);
            }
        }

        return null;
    }

    private LocatedLayout findMultiChunkLayout(long seed) {
        for(int cellX = -12; cellX <= 12; cellX++) {
            for(int cellZ = -12; cellZ <= 12; cellZ++) {
                final MineshaftLayoutGenerator.Layout layout = MineshaftLayoutGenerator.generate(seed, cellX, cellZ);
                if(layout == null)
                    continue;

                final long distinctChunkCount = layout.getRooms().stream()
                        .map(room -> room.getChunkX() + "," + room.getChunkZ())
                        .distinct()
                        .count();
                if(distinctChunkCount > 1)
                    return new LocatedLayout(cellX, cellZ, layout);
            }
        }

        return null;
    }

    private LocatedLayout findMultiLayerLayout(long seed) {
        for(int cellX = -12; cellX <= 12; cellX++) {
            for(int cellZ = -12; cellZ <= 12; cellZ++) {
                final MineshaftLayoutGenerator.Layout layout = MineshaftLayoutGenerator.generate(seed, cellX, cellZ);
                if(layout == null)
                    continue;

                final long distinctLayers = layout.getRooms().stream()
                        .map(MineshaftLayoutGenerator.RoomReservation::getLayer)
                        .distinct()
                        .count();
                if(distinctLayers > 1)
                    return new LocatedLayout(cellX, cellZ, layout);
            }
        }

        return null;
    }

    private LocatedLayout findHubLayout(long seed) {
        for(int cellX = -12; cellX <= 12; cellX++) {
            for(int cellZ = -12; cellZ <= 12; cellZ++) {
                final MineshaftLayoutGenerator.Layout layout = MineshaftLayoutGenerator.generate(seed, cellX, cellZ);
                if(layout == null)
                    continue;

                final boolean hasBroadRoom = layout.getRooms().stream()
                        .anyMatch(room -> Integer.bitCount(room.getConnectionMask()) >= 3);
                if(hasBroadRoom)
                    return new LocatedLayout(cellX, cellZ, layout);
            }
        }

        return null;
    }

    private LocatedLayout findSpecialRoomLayout(long seed) {
        for(int cellX = -12; cellX <= 12; cellX++) {
            for(int cellZ = -12; cellZ <= 12; cellZ++) {
                final MineshaftLayoutGenerator.Layout layout = MineshaftLayoutGenerator.generate(seed, cellX, cellZ);
                if(layout == null)
                    continue;

                final boolean hasSpecialRoom = layout.getRooms().stream()
                        .anyMatch(room -> room.getType() == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN ||
                                room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN);
                if(hasSpecialRoom)
                    return new LocatedLayout(cellX, cellZ, layout);
            }
        }

        return null;
    }

    private LocatedLayout findMultiStorageLayout(long seed) {
        for(int cellX = -12; cellX <= 12; cellX++) {
            for(int cellZ = -12; cellZ <= 12; cellZ++) {
                final MineshaftLayoutGenerator.Layout layout = MineshaftLayoutGenerator.generate(seed, cellX, cellZ);
                if(layout == null)
                    continue;

                final long storageRooms = layout.getRooms().stream()
                        .filter(room -> room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE)
                        .count();
                if(storageRooms >= 2)
                    return new LocatedLayout(cellX, cellZ, layout);
            }
        }

        return null;
    }

    private List<String> describe(MineshaftLayoutGenerator.Layout layout) {
        final List<String> description = new ArrayList<>();
        for(MineshaftLayoutGenerator.RoomReservation room : layout.getRooms())
            description.add(room.getRoomX() + ":" + room.getLayer() + ":" + room.getRoomZ() + ":" + room.getType() + ":" + room.getConnectionMask());
        return description;
    }

    private static final class LocatedLayout {

        private final int cellX;
        private final int cellZ;
        private final MineshaftLayoutGenerator.Layout layout;

        private LocatedLayout(int cellX, int cellZ, MineshaftLayoutGenerator.Layout layout) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.layout = layout;
        }
    }
}
