package com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room;

import com.timvisee.dungeonmaze.generator.DungeonMazeLayout;
import com.timvisee.dungeonmaze.world.dungeon.chunk.DungeonChunk;
import org.msgpack.core.MessagePacker;
import org.msgpack.core.MessageUnpacker;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

public class DungeonChunkRoomGrid {

    /** Defines the chunk this set of rooms is in. */
    private final DungeonChunk chunk;
    /** The list of rooms in this dungeon chunk. */
    private final List<DungeonChunkRoom> rooms = new ArrayList<>();
    /** The room lookup grid in layer/x/z order. */
    private final DungeonChunkRoom[][][] roomGrid = new DungeonChunkRoom[DungeonMazeLayout.LAYER_COUNT][DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE][DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE];

    /**
     * Constructor.
     *
     * @param chunk The dungeon chunk this set of rooms is in.
     */
    public DungeonChunkRoomGrid(DungeonChunk chunk) {
        this.chunk = chunk;

        for(int layer = DungeonMazeLayout.MIN_LAYER; layer <= DungeonMazeLayout.MAX_LAYER; layer++) {
            for(int roomX = 0; roomX < DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE; roomX++) {
                for(int roomZ = 0; roomZ < DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE; roomZ++) {
                    final DungeonChunkRoom room = new DungeonChunkRoom(chunk, roomX, roomZ, layer);
                    this.roomGrid[layer - 1][roomX][roomZ] = room;
                    this.rooms.add(room);
                }
            }
        }
    }

    /**
     * Get the dungeon chunk this set of rooms is in.
     *
     * @return The dungeon chunk this set of rooms is in.
     */
    public DungeonChunk getChunk() {
        return this.chunk;
    }

    public List<DungeonChunkRoom> getRooms() {
        return new ArrayList<>(this.rooms);
    }

    public DungeonChunkRoom getRoom(int roomX, int layer, int roomZ) {
        if(layer < DungeonMazeLayout.MIN_LAYER || layer > DungeonMazeLayout.MAX_LAYER)
            return null;
        if(roomX < 0 || roomX >= DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE)
            return null;
        if(roomZ < 0 || roomZ >= DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE)
            return null;

        return this.roomGrid[layer - 1][roomX][roomZ];
    }

    public void load(MessageUnpacker unpacker) throws IOException {
        final int reservedRoomCount = unpacker.unpackInt();

        for(int i = 0; i < reservedRoomCount; i++) {
            final int roomX = unpacker.unpackInt();
            final int layer = unpacker.unpackInt();
            final int roomZ = unpacker.unpackInt();
            final DungeonChunkRoom room = getRoom(roomX, layer, roomZ);

            if(room == null) {
                unpacker.unpackInt();
                unpacker.unpackLong();
                unpacker.unpackInt();
                continue;
            }

            room.setReservation(
                    DungeonChunkRoomType.byId(unpacker.unpackInt()),
                    unpacker.unpackLong(),
                    unpacker.unpackInt()
            );
        }
    }

    public void save(MessagePacker packer) throws IOException {
        final List<DungeonChunkRoom> reservedRooms = new ArrayList<>();
        for(DungeonChunkRoom room : this.rooms)
            if(room.isReserved())
                reservedRooms.add(room);

        packer.packInt(reservedRooms.size());
        for(DungeonChunkRoom room : reservedRooms) {
            packer.packInt(room.getX());
            packer.packInt(room.getLayer());
            packer.packInt(room.getZ());
            packer.packInt(room.getType().getId());
            packer.packLong(room.getStructureId());
            packer.packInt(room.getConnectionMask());
        }
    }
}
