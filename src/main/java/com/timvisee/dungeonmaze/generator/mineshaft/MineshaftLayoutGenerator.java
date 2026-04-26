package com.timvisee.dungeonmaze.generator.mineshaft;

import com.timvisee.dungeonmaze.generator.DungeonMazeLayout;
import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room.DungeonChunkRoom;
import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room.DungeonChunkRoomType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class MineshaftLayoutGenerator {

    public static final int CELL_SIZE_ROOMS = 8;
    private static final int MIN_LAYER = 2;
    private static final int MAX_LAYER = 6;
    private static final float NETWORK_CHANCE = 0.12f;
    private static final int MIN_NETWORK_ROOMS = 9;
    private static final int MAX_NETWORK_ROOMS = 17;
    private static final float HUB_EXPANSION_CHANCE = 0.7f;
    private static final float MULTI_LEVEL_CHANCE = 0.45f;

    private MineshaftLayoutGenerator() { }

    public static Layout generate(long worldSeed, int cellX, int cellZ) {
        final Random random = new Random(mixSeed(worldSeed, cellX, cellZ));
        if(random.nextFloat() >= NETWORK_CHANCE)
            return null;

        final int layer = MIN_LAYER + random.nextInt((MAX_LAYER - MIN_LAYER) + 1);
        final int minRoom = cellX * CELL_SIZE_ROOMS;
        final int minColumn = cellZ * CELL_SIZE_ROOMS;
        final int startRoomX = minRoom + 1 + random.nextInt(CELL_SIZE_ROOMS - 2);
        final int startRoomZ = minColumn + 1 + random.nextInt(CELL_SIZE_ROOMS - 2);
        final int targetRoomCount = MIN_NETWORK_ROOMS + random.nextInt((MAX_NETWORK_ROOMS - MIN_NETWORK_ROOMS) + 1);
        final long structureId = mixSeed(worldSeed ^ 0x5DEECE66DL, cellX, cellZ) & Long.MAX_VALUE;

        final LinkedHashMap<RoomKey, Integer> rooms = new LinkedHashMap<>();
        final List<RoomKey> frontier = new ArrayList<>();
        final RoomKey start = new RoomKey(startRoomX, startRoomZ);
        rooms.put(start, 0);
        frontier.add(start);

        while(rooms.size() < targetRoomCount && !frontier.isEmpty()) {
            final RoomKey source = frontier.get(random.nextInt(frontier.size()));
            final List<Integer> directions = new ArrayList<>(List.of(
                    DungeonChunkRoom.CONNECTION_NORTH,
                    DungeonChunkRoom.CONNECTION_EAST,
                    DungeonChunkRoom.CONNECTION_SOUTH,
                    DungeonChunkRoom.CONNECTION_WEST
            ));
            Collections.shuffle(directions, random);

            boolean expanded = false;
            for(int direction : directions) {
                final RoomKey neighbor = source.move(direction);
                if(neighbor.x < minRoom || neighbor.x >= minRoom + CELL_SIZE_ROOMS)
                    continue;
                if(neighbor.z < minColumn || neighbor.z >= minColumn + CELL_SIZE_ROOMS)
                    continue;
                if(rooms.containsKey(neighbor))
                    continue;

                rooms.put(neighbor, 0);
                frontier.add(neighbor);
                expanded = true;

                if(random.nextFloat() < 0.5f && rooms.size() < targetRoomCount)
                    continue;
                break;
            }

            if(!expanded)
                frontier.remove(source);
        }

        if(rooms.size() < MIN_NETWORK_ROOMS)
            return null;

        final RoomKey hubCenter = selectHubCenter(random, rooms, start);
        if(hubCenter != null && random.nextFloat() < HUB_EXPANSION_CHANCE)
            expandHubRooms(rooms, hubCenter, minRoom, minColumn);

        final Map<RoomKey, Integer> connectionMasks = buildConnectionMasks(rooms);
        final List<RoomKey> leaves = new ArrayList<>();
        RoomKey farthestLeaf = null;
        int farthestDistance = -1;

        for(Map.Entry<RoomKey, Integer> entry : connectionMasks.entrySet()) {
            final int degree = Integer.bitCount(entry.getValue());
            if(degree != 1)
                continue;

            leaves.add(entry.getKey());
            final int distance = Math.abs(entry.getKey().x - start.x) + Math.abs(entry.getKey().z - start.z);
            if(distance > farthestDistance) {
                farthestDistance = distance;
                farthestLeaf = entry.getKey();
            }
        }

        final boolean multiLevel = hubCenter != null && shouldGenerateAdditionalLevel(random, layer);
        final int secondaryLayer = multiLevel ? pickSecondaryLayer(random, layer) : layer;
        final RoomKey shaftRoom = multiLevel && hubCenter != null ? hubCenter : pickShaftRoom(random, leaves, hubCenter, farthestLeaf);
        final RoomKey spiderDenRoom = pickSpecialRoom(random, connectionMasks, shaftRoom, farthestLeaf, true);
        final RoomKey caveInRoom = pickSpecialRoom(random, connectionMasks, shaftRoom, spiderDenRoom, false);
        final RoomKey secondaryStorageRoom = pickAdditionalStorageRoom(random, connectionMasks, farthestLeaf, shaftRoom, spiderDenRoom, caveInRoom);

        final List<RoomReservation> reservations = new ArrayList<>();
        for(Map.Entry<RoomKey, Integer> entry : connectionMasks.entrySet()) {
            final RoomKey room = entry.getKey();
            final int connectionMask = entry.getValue();
            final DungeonChunkRoomType type;
            if(room.equals(farthestLeaf) || room.equals(secondaryStorageRoom))
                type = DungeonChunkRoomType.MINESHAFT_STORAGE;
            else if(room.equals(shaftRoom))
                type = DungeonChunkRoomType.MINESHAFT_SHAFT;
            else if(room.equals(spiderDenRoom))
                type = DungeonChunkRoomType.MINESHAFT_SPIDER_DEN;
            else if(room.equals(caveInRoom))
                type = DungeonChunkRoomType.MINESHAFT_CAVE_IN;
            else
                type = classifyRoomType(connectionMask);

            reservations.add(new RoomReservation(room.x, layer, room.z, type, structureId, connectionMask));
        }

        if(multiLevel && shaftRoom != null) {
            final LinkedHashMap<RoomKey, Integer> upperRooms = createSecondaryLevelRooms(random, rooms, hubCenter, shaftRoom, minRoom, minColumn);
            final Map<RoomKey, Integer> upperConnectionMasks = buildConnectionMasks(upperRooms);
            for(Map.Entry<RoomKey, Integer> entry : upperConnectionMasks.entrySet()) {
                final RoomKey room = entry.getKey();
                final DungeonChunkRoomType type = room.equals(shaftRoom)
                        ? DungeonChunkRoomType.MINESHAFT_SHAFT
                        : classifyRoomType(entry.getValue());
                reservations.add(new RoomReservation(room.x, secondaryLayer, room.z, type, structureId, entry.getValue()));
            }
        }

        return new Layout(cellX, cellZ, layer, structureId, reservations);
    }

    public static int getCellXForRoom(int roomX) {
        return Math.floorDiv(roomX, CELL_SIZE_ROOMS);
    }

    public static int getCellZForRoom(int roomZ) {
        return Math.floorDiv(roomZ, CELL_SIZE_ROOMS);
    }

    private static Map<RoomKey, Integer> buildConnectionMasks(Map<RoomKey, Integer> rooms) {
        final Map<RoomKey, Integer> result = new HashMap<>();

        for(RoomKey room : rooms.keySet()) {
            int mask = 0;
            if(rooms.containsKey(room.move(DungeonChunkRoom.CONNECTION_NORTH)))
                mask |= DungeonChunkRoom.CONNECTION_NORTH;
            if(rooms.containsKey(room.move(DungeonChunkRoom.CONNECTION_EAST)))
                mask |= DungeonChunkRoom.CONNECTION_EAST;
            if(rooms.containsKey(room.move(DungeonChunkRoom.CONNECTION_SOUTH)))
                mask |= DungeonChunkRoom.CONNECTION_SOUTH;
            if(rooms.containsKey(room.move(DungeonChunkRoom.CONNECTION_WEST)))
                mask |= DungeonChunkRoom.CONNECTION_WEST;
            result.put(room, mask);
        }

        return result;
    }

    private static RoomKey selectHubCenter(Random random, Map<RoomKey, Integer> rooms, RoomKey fallback) {
        final List<RoomKey> candidates = new ArrayList<>();
        final Map<RoomKey, Integer> connectionMasks = buildConnectionMasks(rooms);
        for(Map.Entry<RoomKey, Integer> entry : connectionMasks.entrySet()) {
            if(Integer.bitCount(entry.getValue()) >= 2)
                candidates.add(entry.getKey());
        }

        if(candidates.isEmpty())
            return fallback;

        return candidates.get(random.nextInt(candidates.size()));
    }

    private static void expandHubRooms(Map<RoomKey, Integer> rooms, RoomKey hubCenter, int minRoom, int minColumn) {
        for(int direction : List.of(
                DungeonChunkRoom.CONNECTION_NORTH,
                DungeonChunkRoom.CONNECTION_EAST,
                DungeonChunkRoom.CONNECTION_SOUTH,
                DungeonChunkRoom.CONNECTION_WEST
        )) {
            final RoomKey neighbor = hubCenter.move(direction);
            if(neighbor.x < minRoom || neighbor.x >= minRoom + CELL_SIZE_ROOMS)
                continue;
            if(neighbor.z < minColumn || neighbor.z >= minColumn + CELL_SIZE_ROOMS)
                continue;

            rooms.putIfAbsent(neighbor, 0);
        }
    }

    private static boolean shouldGenerateAdditionalLevel(Random random, int layer) {
        return random.nextFloat() < MULTI_LEVEL_CHANCE && (layer > MIN_LAYER || layer < MAX_LAYER);
    }

    private static int pickSecondaryLayer(Random random, int layer) {
        if(layer <= MIN_LAYER)
            return layer + 1;
        if(layer >= MAX_LAYER)
            return layer - 1;

        return random.nextBoolean() ? layer + 1 : layer - 1;
    }

    private static RoomKey pickShaftRoom(Random random, List<RoomKey> leaves, RoomKey hubCenter, RoomKey farthestLeaf) {
        if(hubCenter != null)
            return hubCenter;

        if(leaves.size() > 1) {
            final List<RoomKey> remainingLeaves = new ArrayList<>(leaves);
            remainingLeaves.remove(farthestLeaf);
            if(!remainingLeaves.isEmpty())
                return remainingLeaves.get(random.nextInt(remainingLeaves.size()));
        }

        return farthestLeaf;
    }

    private static RoomKey pickAdditionalStorageRoom(Random random, Map<RoomKey, Integer> connectionMasks, RoomKey excludedA, RoomKey excludedB,
                                                     RoomKey excludedC, RoomKey excludedD) {
        final List<RoomKey> candidates = new ArrayList<>();
        for(Map.Entry<RoomKey, Integer> entry : connectionMasks.entrySet()) {
            final RoomKey room = entry.getKey();
            if(room.equals(excludedA) || room.equals(excludedB) || room.equals(excludedC) || room.equals(excludedD))
                continue;
            if(Integer.bitCount(entry.getValue()) == 1)
                candidates.add(room);
        }

        if(candidates.isEmpty())
            return null;

        return candidates.get(random.nextInt(candidates.size()));
    }

    private static LinkedHashMap<RoomKey, Integer> createSecondaryLevelRooms(Random random, Map<RoomKey, Integer> baseRooms, RoomKey hubCenter,
                                                                              RoomKey shaftRoom, int minRoom, int minColumn) {
        final LinkedHashMap<RoomKey, Integer> upperRooms = new LinkedHashMap<>();
        upperRooms.put(shaftRoom, 0);

        final RoomKey reference = hubCenter == null ? shaftRoom : hubCenter;
        final List<RoomKey> preferredNeighbors = new ArrayList<>();
        for(int direction : List.of(
                DungeonChunkRoom.CONNECTION_NORTH,
                DungeonChunkRoom.CONNECTION_EAST,
                DungeonChunkRoom.CONNECTION_SOUTH,
                DungeonChunkRoom.CONNECTION_WEST
        )) {
            final RoomKey neighbor = reference.move(direction);
            if(neighbor.x < minRoom || neighbor.x >= minRoom + CELL_SIZE_ROOMS)
                continue;
            if(neighbor.z < minColumn || neighbor.z >= minColumn + CELL_SIZE_ROOMS)
                continue;
            if(!baseRooms.containsKey(neighbor))
                continue;
            preferredNeighbors.add(neighbor);
        }

        Collections.shuffle(preferredNeighbors, random);
        final int targetRoomCount = Math.max(2, Math.min(preferredNeighbors.size() + 1, 3 + random.nextInt(3)));
        for(RoomKey neighbor : preferredNeighbors) {
            upperRooms.put(neighbor, 0);
            if(upperRooms.size() >= targetRoomCount)
                break;
        }

        return upperRooms;
    }

    private static RoomKey pickSpecialRoom(Random random, Map<RoomKey, Integer> connectionMasks, RoomKey excludedA, RoomKey excludedB,
                                           boolean preferJunction) {
        final List<RoomKey> candidates = new ArrayList<>();
        for(Map.Entry<RoomKey, Integer> entry : connectionMasks.entrySet()) {
            final RoomKey room = entry.getKey();
            if(room.equals(excludedA) || room.equals(excludedB))
                continue;

            final int degree = Integer.bitCount(entry.getValue());
            if(preferJunction) {
                if(degree >= 2)
                    candidates.add(room);
            } else if(degree <= 2) {
                candidates.add(room);
            }
        }

        if(candidates.isEmpty())
            return null;

        return candidates.get(random.nextInt(candidates.size()));
    }

    private static DungeonChunkRoomType classifyRoomType(int connectionMask) {
        final int degree = Integer.bitCount(connectionMask);
        if(degree >= 4)
            return DungeonChunkRoomType.MINESHAFT_CROSSROAD;
        if(degree == 3)
            return DungeonChunkRoomType.MINESHAFT_T_JUNCTION;
        if(degree == 2) {
            final boolean opposite = connectionMask == (DungeonChunkRoom.CONNECTION_NORTH | DungeonChunkRoom.CONNECTION_SOUTH) ||
                    connectionMask == (DungeonChunkRoom.CONNECTION_EAST | DungeonChunkRoom.CONNECTION_WEST);
            return opposite ? DungeonChunkRoomType.MINESHAFT_CORRIDOR : DungeonChunkRoomType.MINESHAFT_TURN;
        }

        return DungeonChunkRoomType.MINESHAFT_CORRIDOR;
    }

    private static long mixSeed(long worldSeed, int x, int z) {
        long seed = worldSeed;
        seed ^= ((long) x * 341873128712L);
        seed ^= ((long) z * 132897987541L);
        seed ^= seed >>> 33;
        seed *= 0xff51afd7ed558ccdL;
        seed ^= seed >>> 33;
        seed *= 0xc4ceb9fe1a85ec53L;
        seed ^= seed >>> 33;
        return seed;
    }

    public static final class Layout {

        private final int cellX;
        private final int cellZ;
        private final int layer;
        private final long structureId;
        private final List<RoomReservation> rooms;

        private Layout(int cellX, int cellZ, int layer, long structureId, List<RoomReservation> rooms) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.layer = layer;
            this.structureId = structureId;
            this.rooms = List.copyOf(rooms);
        }

        public int getCellX() {
            return this.cellX;
        }

        public int getCellZ() {
            return this.cellZ;
        }

        public int getLayer() {
            return this.layer;
        }

        public long getStructureId() {
            return this.structureId;
        }

        public List<RoomReservation> getRooms() {
            return this.rooms;
        }
    }

    public static final class RoomReservation {

        private final int roomX;
        private final int layer;
        private final int roomZ;
        private final DungeonChunkRoomType type;
        private final long structureId;
        private final int connectionMask;

        private RoomReservation(int roomX, int layer, int roomZ, DungeonChunkRoomType type, long structureId, int connectionMask) {
            this.roomX = roomX;
            this.layer = layer;
            this.roomZ = roomZ;
            this.type = type;
            this.structureId = structureId;
            this.connectionMask = connectionMask;
        }

        public int getRoomX() {
            return this.roomX;
        }

        public int getLayer() {
            return this.layer;
        }

        public int getRoomZ() {
            return this.roomZ;
        }

        public DungeonChunkRoomType getType() {
            return this.type;
        }

        public long getStructureId() {
            return this.structureId;
        }

        public int getConnectionMask() {
            return this.connectionMask;
        }

        public int getChunkX() {
            return Math.floorDiv(this.roomX, DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE);
        }

        public int getChunkZ() {
            return Math.floorDiv(this.roomZ, DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE);
        }

        public int getChunkRoomX() {
            return Math.floorMod(this.roomX, DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE);
        }

        public int getChunkRoomZ() {
            return Math.floorMod(this.roomZ, DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE);
        }
    }

    private static final class RoomKey {

        private final int x;
        private final int z;

        private RoomKey(int x, int z) {
            this.x = x;
            this.z = z;
        }

        private RoomKey move(int connection) {
            if(connection == DungeonChunkRoom.CONNECTION_NORTH)
                return new RoomKey(this.x, this.z - 1);
            if(connection == DungeonChunkRoom.CONNECTION_EAST)
                return new RoomKey(this.x + 1, this.z);
            if(connection == DungeonChunkRoom.CONNECTION_SOUTH)
                return new RoomKey(this.x, this.z + 1);
            if(connection == DungeonChunkRoom.CONNECTION_WEST)
                return new RoomKey(this.x - 1, this.z);

            return this;
        }

        @Override
        public boolean equals(Object obj) {
            if(this == obj)
                return true;
            if(!(obj instanceof RoomKey))
                return false;
            final RoomKey other = (RoomKey) obj;
            return this.x == other.x && this.z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.x, this.z);
        }
    }
}
