package com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room;

public enum DungeonChunkRoomType {

    NONE(0),
    MINESHAFT_CORRIDOR(1),
    MINESHAFT_TURN(2),
    MINESHAFT_T_JUNCTION(3),
    MINESHAFT_CROSSROAD(4),
    MINESHAFT_STORAGE(5),
    MINESHAFT_SHAFT(6),
    MINESHAFT_SPIDER_DEN(7),
    MINESHAFT_CAVE_IN(8);

    private final int id;

    DungeonChunkRoomType(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public boolean isMineshaftRoom() {
        return this != NONE;
    }

    public static DungeonChunkRoomType byId(int id) {
        for(DungeonChunkRoomType type : values())
            if(type.id == id)
                return type;

        return NONE;
    }
}
