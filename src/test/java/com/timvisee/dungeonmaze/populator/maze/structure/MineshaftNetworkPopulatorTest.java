package com.timvisee.dungeonmaze.populator.maze.structure;

import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room.DungeonChunkRoom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room.DungeonChunkRoomType;

class MineshaftNetworkPopulatorTest {

    @Test
    void railBedFollowsNorthSouthCorridors() {
        final int openingMask = DungeonChunkRoom.CONNECTION_NORTH | DungeonChunkRoom.CONNECTION_SOUTH;

        assertTrue(MineshaftNetworkPopulator.shouldPlaceRailBed(3, 2, openingMask));
        assertTrue(MineshaftNetworkPopulator.shouldPlaceRailBed(4, 5, openingMask));
        assertFalse(MineshaftNetworkPopulator.shouldPlaceRailBed(2, 2, openingMask));
    }

    @Test
    void railBedFollowsEastWestCorridors() {
        final int openingMask = DungeonChunkRoom.CONNECTION_EAST | DungeonChunkRoom.CONNECTION_WEST;

        assertTrue(MineshaftNetworkPopulator.shouldPlaceRailBed(2, 3, openingMask));
        assertTrue(MineshaftNetworkPopulator.shouldPlaceRailBed(5, 4, openingMask));
        assertFalse(MineshaftNetworkPopulator.shouldPlaceRailBed(2, 2, openingMask));
    }

    @Test
    void railBedCreatesCrossingsInJunctions() {
        final int openingMask = DungeonChunkRoom.CONNECTION_NORTH |
                DungeonChunkRoom.CONNECTION_EAST |
                DungeonChunkRoom.CONNECTION_SOUTH |
                DungeonChunkRoom.CONNECTION_WEST;

        assertTrue(MineshaftNetworkPopulator.shouldPlaceRailBed(3, 2, openingMask));
        assertTrue(MineshaftNetworkPopulator.shouldPlaceRailBed(2, 4, openingMask));
        assertTrue(MineshaftNetworkPopulator.shouldPlaceRailBed(4, 4, openingMask));
        assertFalse(MineshaftNetworkPopulator.shouldPlaceRailBed(2, 2, openingMask));
    }

    @Test
    void tunnelFootprintKeepsDeadEndSidesClosed() {
        final int openingMask = DungeonChunkRoom.CONNECTION_NORTH;

        assertTrue(MineshaftNetworkPopulator.shouldCarveTunnelCell(3, 1, openingMask, DungeonChunkRoomType.MINESHAFT_CORRIDOR, false));
        assertTrue(MineshaftNetworkPopulator.shouldCarveTunnelCell(4, 4, openingMask, DungeonChunkRoomType.MINESHAFT_CORRIDOR, false));
        assertFalse(MineshaftNetworkPopulator.shouldCarveTunnelCell(3, 6, openingMask, DungeonChunkRoomType.MINESHAFT_CORRIDOR, false));
        assertFalse(MineshaftNetworkPopulator.shouldCarveTunnelCell(1, 3, openingMask, DungeonChunkRoomType.MINESHAFT_CORRIDOR, false));
    }

    @Test
    void specialRoomsKeepCentralChamberOpen() {
        final int openingMask = DungeonChunkRoom.CONNECTION_EAST | DungeonChunkRoom.CONNECTION_WEST;

        assertTrue(MineshaftNetworkPopulator.shouldCarveTunnelCell(3, 3, openingMask, DungeonChunkRoomType.MINESHAFT_STORAGE, true));
        assertTrue(MineshaftNetworkPopulator.shouldCarveTunnelCell(6, 3, openingMask, DungeonChunkRoomType.MINESHAFT_STORAGE, true));
        assertFalse(MineshaftNetworkPopulator.shouldCarveTunnelCell(1, 1, openingMask, DungeonChunkRoomType.MINESHAFT_STORAGE, true));
    }

    @Test
    void loweredCeilingKeepsBranchExitsOpen() {
        final int openingMask = DungeonChunkRoom.CONNECTION_NORTH | DungeonChunkRoom.CONNECTION_SOUTH;

        assertFalse(MineshaftNetworkPopulator.shouldLowerCeilingCell(3, 2, openingMask, DungeonChunkRoomType.MINESHAFT_STORAGE, true));
        assertFalse(MineshaftNetworkPopulator.shouldLowerCeilingCell(4, 5, openingMask, DungeonChunkRoomType.MINESHAFT_STORAGE, true));
        assertTrue(MineshaftNetworkPopulator.shouldLowerCeilingCell(2, 3, openingMask, DungeonChunkRoomType.MINESHAFT_STORAGE, true));
    }

    @Test
    void broadRoomsCanUseLoweredRingCeilings() {
        final int openingMask = DungeonChunkRoom.CONNECTION_EAST | DungeonChunkRoom.CONNECTION_WEST;

        assertTrue(MineshaftNetworkPopulator.shouldLowerCeilingCell(3, 2, openingMask, DungeonChunkRoomType.MINESHAFT_CROSSROAD, true));
        assertFalse(MineshaftNetworkPopulator.shouldLowerCeilingCell(5, 3, openingMask, DungeonChunkRoomType.MINESHAFT_CROSSROAD, true));
        assertFalse(MineshaftNetworkPopulator.shouldLowerCeilingCell(3, 3, openingMask, DungeonChunkRoomType.MINESHAFT_CROSSROAD, true));
    }
}
