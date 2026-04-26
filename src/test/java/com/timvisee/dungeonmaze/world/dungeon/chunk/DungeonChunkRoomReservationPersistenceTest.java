package com.timvisee.dungeonmaze.world.dungeon.chunk;

import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.DungeonRegionGrid;
import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room.DungeonChunkRoom;
import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room.DungeonChunkRoomType;
import org.junit.jupiter.api.Test;
import org.msgpack.core.MessagePack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonChunkRoomReservationPersistenceTest {

    @Test
    void reservedRoomsRoundTripThroughChunkSerialization() throws Exception {
        final DungeonRegion region = new DungeonRegion(new DungeonRegionGrid(null), 0, 0);
        final DungeonChunk originalChunk = new DungeonChunk(region, 1, 2);
        originalChunk.setCustomChunk(true);
        originalChunk.reserveRoom(1, 4, 0, DungeonChunkRoomType.MINESHAFT_STORAGE, 73L,
                DungeonChunkRoom.CONNECTION_NORTH | DungeonChunkRoom.CONNECTION_SOUTH);

        final var packer = MessagePack.newDefaultBufferPacker();
        originalChunk.save(packer);
        packer.close();

        final DungeonChunk loadedChunk = DungeonChunk.load(region, MessagePack.newDefaultUnpacker(packer.toByteArray()));
        assertTrue(loadedChunk.isCustomChunk());

        final var reservedRoom = loadedChunk.getRoom(1, 4, 0);
        assertNotNull(reservedRoom);
        assertTrue(reservedRoom.isReserved());
        assertEquals(DungeonChunkRoomType.MINESHAFT_STORAGE, reservedRoom.getType());
        assertEquals(73L, reservedRoom.getStructureId());
        assertEquals(DungeonChunkRoom.CONNECTION_NORTH | DungeonChunkRoom.CONNECTION_SOUTH, reservedRoom.getConnectionMask());
    }
}
