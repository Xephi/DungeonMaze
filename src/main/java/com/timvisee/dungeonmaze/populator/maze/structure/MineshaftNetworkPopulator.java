package com.timvisee.dungeonmaze.populator.maze.structure;

import com.timvisee.dungeonmaze.Core;
import com.timvisee.dungeonmaze.DungeonMaze;
import com.timvisee.dungeonmaze.event.generation.GenerationChestEvent;
import com.timvisee.dungeonmaze.event.generation.GenerationSpawnerEvent;
import com.timvisee.dungeonmaze.generator.DungeonMazeLayout;
import com.timvisee.dungeonmaze.generator.mineshaft.MineshaftLayoutGenerator;
import com.timvisee.dungeonmaze.populator.ChunkBlockPopulator;
import com.timvisee.dungeonmaze.populator.ChunkBlockPopulatorArgs;
import com.timvisee.dungeonmaze.populator.maze.MazeStructureType;
import com.timvisee.dungeonmaze.util.ChestUtils;
import com.timvisee.dungeonmaze.util.MaterialUtils;
import com.timvisee.dungeonmaze.world.dungeon.chunk.DungeonChunk;
import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.DungeonRegionGrid;
import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room.DungeonChunkRoom;
import com.timvisee.dungeonmaze.world.dungeon.chunk.grid.room.DungeonChunkRoomType;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MineshaftNetworkPopulator extends ChunkBlockPopulator {

    private static final int MIN_SPAWN_DISTANCE_CHUNKS = 4;

    @Override
    public void populateChunk(ChunkBlockPopulatorArgs args) {
        final Chunk chunk = args.getSourceChunk();
        if(Math.max(Math.abs(chunk.getX()), Math.abs(chunk.getZ())) < MIN_SPAWN_DISTANCE_CHUNKS)
            return;

        final DungeonRegionGrid regionGrid = args.getDungeonChunk().getRegion().getGrid();
        reserveNearbyLayouts(args.getWorld().getSeed(), regionGrid, chunk.getX(), chunk.getZ());
        generateReservedRooms(chunk, args.getRandom(), args.getDungeonChunk());
    }

    private void reserveNearbyLayouts(long worldSeed, DungeonRegionGrid regionGrid, int chunkX, int chunkZ) {
        final Set<Long> candidateCells = new LinkedHashSet<>();
        for(int roomX = 0; roomX < DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE; roomX++) {
            for(int roomZ = 0; roomZ < DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE; roomZ++) {
                final int worldRoomX = (chunkX * DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE) + roomX;
                final int worldRoomZ = (chunkZ * DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE) + roomZ;
                final int cellX = MineshaftLayoutGenerator.getCellXForRoom(worldRoomX);
                final int cellZ = MineshaftLayoutGenerator.getCellZForRoom(worldRoomZ);
                candidateCells.add(encodeCell(cellX, cellZ));
            }
        }

        for(long encodedCell : candidateCells) {
            final int cellX = (int) (encodedCell >> 32);
            final int cellZ = (int) encodedCell;
            final MineshaftLayoutGenerator.Layout layout = MineshaftLayoutGenerator.generate(worldSeed, cellX, cellZ);
            if(layout == null)
                continue;

            for(MineshaftLayoutGenerator.RoomReservation reservation : layout.getRooms()) {
                final DungeonChunk targetChunk = regionGrid.getOrCreateChunk(reservation.getChunkX(), reservation.getChunkZ());
                if(targetChunk == null)
                    continue;

                final DungeonChunkRoom targetRoom = targetChunk.getRoom(reservation.getChunkRoomX(), reservation.getLayer(), reservation.getChunkRoomZ());
                if(targetRoom == null)
                    continue;
                if(targetRoom.isReserved() && targetRoom.getStructureId() != reservation.getStructureId())
                    continue;

                targetRoom.setReservation(reservation.getType(), reservation.getStructureId(), reservation.getConnectionMask());
            }
        }
    }

    private void generateReservedRooms(Chunk chunk, Random random, DungeonChunk dungeonChunk) {
        for(int layer = DungeonMazeLayout.MIN_LAYER; layer <= DungeonMazeLayout.MAX_LAYER; layer++) {
            for(int roomX = 0; roomX < DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE; roomX++) {
                for(int roomZ = 0; roomZ < DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE; roomZ++) {
                    final DungeonChunkRoom room = dungeonChunk.getRoom(roomX, layer, roomZ);
                    if(room == null || !room.isMineshaftRoom())
                        continue;

                    generateRoom(chunk, random, room);
                }
            }
        }
    }

    private void generateRoom(Chunk chunk, Random random, DungeonChunkRoom room) {
        final int roomX = room.getChunkBlockX();
        final int roomZ = room.getChunkBlockZ();
        final RoomProfile roomProfile = analyzeRoomProfile(chunk, room);
        final StructureContext structureContext = analyzeStructureContext(room);
        final int openingMask = room.getConnectionMask() | roomProfile.getDungeonOpeningMask();

        carveRoom(chunk, roomX, roomProfile.getFloorY(), roomZ, room, openingMask, roomProfile.getCeilingY(), structureContext);
        openConnections(chunk, roomX, roomProfile.getFloorY(), roomZ, roomProfile.getDungeonOpeningMask(), structureContext, roomProfile.getCeilingY());
        addLoweredCeiling(chunk, roomX, roomProfile.getFloorY(), roomZ, openingMask, room, roomProfile.getCeilingY(), structureContext);
        addSupports(chunk, roomX, roomProfile.getFloorY(), roomZ, openingMask, roomProfile.getCeilingY(), room, structureContext);
        addFloorTimbers(chunk, roomX, roomProfile.getFloorY(), roomZ, openingMask, room, structureContext);
        addRails(chunk, random, roomX, roomProfile.getFloorY(), roomZ, openingMask, room, structureContext);
        addClutter(chunk, random, room, roomX, roomProfile.getFloorY(), roomZ, openingMask, roomProfile.getCeilingY(), structureContext);

        if(room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE)
            addStorage(chunk, random, roomX, roomProfile.getFloorY(), roomZ, openingMask, structureContext);
        else if(room.getType() == DungeonChunkRoomType.MINESHAFT_SHAFT)
            addShaft(chunk, room, roomX, roomProfile, structureContext);
        else if(room.getType() == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN)
            addSpiderDen(chunk, random, roomX, roomProfile.getFloorY(), roomZ, structureContext);
        else if(room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN)
            addCaveIn(chunk, random, roomX, roomProfile.getFloorY(), roomZ, structureContext, roomProfile.getCeilingY());
    }

    private RoomProfile analyzeRoomProfile(Chunk chunk, DungeonChunkRoom room) {
        final int roomX = room.getChunkBlockX();
        final int roomZ = room.getChunkBlockZ();
        final int layerBaseY = DungeonMazeLayout.getLayerBaseY(room.getLayer());
        final int floorOffset = resolveFloorOffset(chunk.getBlock(roomX + 3, layerBaseY, roomZ + 3).getType());
        final int ceilingOffset = resolveCeilingOffset(chunk.getBlock(roomX + 3, layerBaseY + 6, roomZ + 3).getType());
        final int floorY = layerBaseY + floorOffset;
        final int ceilingY = layerBaseY + 6 + ceilingOffset;
        final int dungeonOpeningMask = detectDungeonOpeningMask(chunk, roomX, floorY, roomZ, ceilingY);
        return new RoomProfile(floorY, ceilingY, dungeonOpeningMask);
    }

    private int resolveFloorOffset(Material material) {
        return isBaseRoomFloorMaterial(material) ? 0 : 1;
    }

    private int resolveCeilingOffset(Material material) {
        return isBaseRoomFloorMaterial(material) ? 0 : 1;
    }

    private boolean isBaseRoomFloorMaterial(Material material) {
        return material == Material.COBBLESTONE ||
                material == Material.MOSSY_COBBLESTONE ||
                material == Material.NETHERRACK ||
                material == Material.SOUL_SAND;
    }

    private int detectDungeonOpeningMask(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY) {
        int mask = 0;
        if(isNorthBoundaryOpen(chunk, roomX, floorY, roomZ, ceilingY))
            mask |= DungeonChunkRoom.CONNECTION_NORTH;
        if(isEastBoundaryOpen(chunk, roomX, floorY, roomZ, ceilingY))
            mask |= DungeonChunkRoom.CONNECTION_EAST;
        if(isSouthBoundaryOpen(chunk, roomX, floorY, roomZ, ceilingY))
            mask |= DungeonChunkRoom.CONNECTION_SOUTH;
        if(isWestBoundaryOpen(chunk, roomX, floorY, roomZ, ceilingY))
            mask |= DungeonChunkRoom.CONNECTION_WEST;
        return mask;
    }

    private boolean isNorthBoundaryOpen(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY) {
        return isOpeningClear(chunk, roomX + 2, roomX + 5, floorY + 1, Math.min(floorY + 2, ceilingY - 1), roomZ, true);
    }

    private boolean isEastBoundaryOpen(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY) {
        return isOpeningClear(chunk, roomZ + 2, roomZ + 5, floorY + 1, Math.min(floorY + 2, ceilingY - 1), roomX + 7, false);
    }

    private boolean isSouthBoundaryOpen(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY) {
        return isOpeningClear(chunk, roomX + 2, roomX + 5, floorY + 1, Math.min(floorY + 2, ceilingY - 1), roomZ + 7, true);
    }

    private boolean isWestBoundaryOpen(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY) {
        return isOpeningClear(chunk, roomZ + 2, roomZ + 5, floorY + 1, Math.min(floorY + 2, ceilingY - 1), roomX, false);
    }

    private boolean isOpeningClear(Chunk chunk, int horizontalStart, int horizontalEnd, int minY, int maxY, int fixedAxis, boolean fixedZ) {
        if(maxY < minY)
            return false;

        for(int horizontal = horizontalStart; horizontal <= horizontalEnd; horizontal++) {
            for(int yy = minY; yy <= maxY; yy++) {
                final Block block = fixedZ ? chunk.getBlock(horizontal, yy, fixedAxis) : chunk.getBlock(fixedAxis, yy, horizontal);
                if(block.getType() != Material.AIR)
                    return false;
            }
        }

        return true;
    }

    private StructureContext analyzeStructureContext(DungeonChunkRoom room) {
        final DungeonChunkRoom northRoom = getStructureRoom(room, 0, 0, -1);
        final DungeonChunkRoom eastRoom = getStructureRoom(room, 1, 0, 0);
        final DungeonChunkRoom southRoom = getStructureRoom(room, 0, 0, 1);
        final DungeonChunkRoom westRoom = getStructureRoom(room, -1, 0, 0);
        final DungeonChunkRoom aboveRoom = getStructureRoom(room, 0, 1, 0);
        final DungeonChunkRoom belowRoom = getStructureRoom(room, 0, -1, 0);
        return new StructureContext(
                room,
                isSameStructureRoom(room, northRoom),
                isSameStructureRoom(room, eastRoom),
                isSameStructureRoom(room, southRoom),
                isSameStructureRoom(room, westRoom),
                aboveRoom,
                belowRoom
        );
    }

    private DungeonChunkRoom getStructureRoom(DungeonChunkRoom room, int roomOffsetX, int layerOffset, int roomOffsetZ) {
        final int targetLayer = room.getLayer() + layerOffset;
        if(targetLayer < DungeonMazeLayout.MIN_LAYER || targetLayer > DungeonMazeLayout.MAX_LAYER)
            return null;

        final int targetWorldRoomX = (room.getChunk().getChunkX() * DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE) + room.getX() + roomOffsetX;
        final int targetWorldRoomZ = (room.getChunk().getChunkZ() * DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE) + room.getZ() + roomOffsetZ;
        final int targetChunkX = Math.floorDiv(targetWorldRoomX, DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE);
        final int targetChunkZ = Math.floorDiv(targetWorldRoomZ, DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE);
        final DungeonChunk targetChunk = room.getChunk().getRegion().getGrid().getOrCreateChunk(targetChunkX, targetChunkZ);
        if(targetChunk == null)
            return null;

        return targetChunk.getRoom(
                Math.floorMod(targetWorldRoomX, DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE),
                targetLayer,
                Math.floorMod(targetWorldRoomZ, DungeonMazeLayout.ROOMS_PER_CHUNK_SIDE)
        );
    }

    private boolean isSameStructureRoom(DungeonChunkRoom room, DungeonChunkRoom otherRoom) {
        return otherRoom != null &&
                otherRoom.isMineshaftRoom() &&
                otherRoom.getStructureId() == room.getStructureId();
    }

    private void carveRoom(Chunk chunk, int roomX, int floorY, int roomZ, DungeonChunkRoom room, int openingMask, int ceilingY,
                           StructureContext structureContext) {
        final boolean broadRoom = structureContext.isBroadRoom();
        for(int x = roomX; x < roomX + DungeonMazeLayout.ROOM_SIZE; x++) {
            for(int z = roomZ; z < roomZ + DungeonMazeLayout.ROOM_SIZE; z++) {
                final int localX = x - roomX;
                final int localZ = z - roomZ;
                if(!shouldCarveTunnelCell(localX, localZ, openingMask, room.getType(), broadRoom))
                    continue;

                setGeneratedBlock(chunk.getBlock(x, floorY, z), pickFloorMaterial(x, z, room));
                for(int yy = floorY + 1; yy <= ceilingY - 1; yy++)
                    setGeneratedBlock(chunk.getBlock(x, yy, z), Material.AIR);
            }
        }

        if(room.getType() == DungeonChunkRoomType.MINESHAFT_SHAFT || structureContext.hasAboveRoom() || structureContext.hasBelowRoom())
            carveOpenCeilingBand(chunk, roomX, floorY, roomZ, ceilingY);
        else
            carveDecorativeCeilingGaps(chunk, roomX, floorY, roomZ, ceilingY);
    }

    private void carveDecorativeCeilingGaps(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY) {
        for(int x = roomX + 1; x <= roomX + 6; x++) {
            for(int z = roomZ + 1; z <= roomZ + 6; z++) {
                if((x == roomX + 1 || x == roomX + 6 || z == roomZ + 1 || z == roomZ + 6) && ((x + z) & 1) == 0 && ceilingY - 1 > floorY + 1)
                    setGeneratedBlock(chunk.getBlock(x, ceilingY - 1, z), Material.AIR);
            }
        }
    }

    private void carveOpenCeilingBand(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY) {
        if(ceilingY - 1 <= floorY + 2)
            return;

        for(int x = roomX + 1; x <= roomX + 6; x++)
            for(int z = roomZ + 1; z <= roomZ + 6; z++)
                if(x == roomX + 1 || x == roomX + 6 || z == roomZ + 1 || z == roomZ + 6)
                    setGeneratedBlock(chunk.getBlock(x, ceilingY - 1, z), Material.AIR);
    }

    private void addLoweredCeiling(Chunk chunk, int roomX, int floorY, int roomZ, int openingMask, DungeonChunkRoom room, int ceilingY,
                                   StructureContext structureContext) {
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_SHAFT || structureContext.hasAboveRoom() || structureContext.hasBelowRoom())
            return;

        if(!shouldLowerCeiling(room, structureContext, ceilingY - floorY))
            return;

        final int loweredCeilingY = ceilingY - 2;
        if(loweredCeilingY <= floorY + 2)
            return;

        for(int x = roomX + 1; x <= roomX + 6; x++) {
            for(int z = roomZ + 1; z <= roomZ + 6; z++) {
                final int localX = x - roomX;
                final int localZ = z - roomZ;
                if(!shouldCarveTunnelCell(localX, localZ, openingMask, room.getType(), structureContext.isBroadRoom()))
                    continue;
                if(!shouldLowerCeilingCell(localX, localZ, openingMask, room.getType(), structureContext.isBroadRoom()))
                    continue;

                setGeneratedBlock(chunk.getBlock(x, loweredCeilingY, z), pickLoweredCeilingMaterial(room, localX, localZ));
                for(int yy = loweredCeilingY + 1; yy <= ceilingY - 1; yy++)
                    setGeneratedBlock(chunk.getBlock(x, yy, z), Material.STONE);
            }
        }
    }

    private boolean shouldLowerCeiling(DungeonChunkRoom room, StructureContext structureContext, int roomHeight) {
        if(roomHeight < 6)
            return false;
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE ||
                room.getType() == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN ||
                room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN)
            return true;
        return structureContext.isBroadRoom();
    }

    static boolean shouldLowerCeilingCell(int localX, int localZ, int openingMask, DungeonChunkRoomType roomType, boolean broadRoom) {
        if(localX <= 1 || localX >= 6 || localZ <= 1 || localZ >= 6)
            return false;

        final boolean preserveNorthExit = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_NORTH) && localZ <= 2 && (localX == 3 || localX == 4);
        final boolean preserveEastExit = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_EAST) && localX >= 5 && (localZ == 3 || localZ == 4);
        final boolean preserveSouthExit = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_SOUTH) && localZ >= 5 && (localX == 3 || localX == 4);
        final boolean preserveWestExit = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_WEST) && localX <= 2 && (localZ == 3 || localZ == 4);
        if(preserveNorthExit || preserveEastExit || preserveSouthExit || preserveWestExit)
            return false;

        if(roomType == DungeonChunkRoomType.MINESHAFT_STORAGE || roomType == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN)
            return localX >= 2 && localX <= 5 && localZ >= 2 && localZ <= 5;
        if(roomType == DungeonChunkRoomType.MINESHAFT_CAVE_IN)
            return localX >= 2 && localX <= 5 && localZ >= 2 && localZ <= 5 && !(localX == 3 && localZ == 3);

        return broadRoom && (localX == 2 || localX == 5 || localZ == 2 || localZ == 5);
    }

    private Material pickLoweredCeilingMaterial(DungeonChunkRoom room, int localX, int localZ) {
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE)
            return ((localX + localZ) & 1) == 0 ? Material.OAK_PLANKS : Material.DARK_OAK_PLANKS;
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN)
            return (localX == 2 || localX == 5 || localZ == 2 || localZ == 5) ? Material.OAK_PLANKS : Material.COBBLESTONE;
        return Material.OAK_PLANKS;
    }

    static boolean shouldCarveTunnelCell(int localX, int localZ, int openingMask, DungeonChunkRoomType roomType, boolean broadRoom) {
        final boolean northBranch = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_NORTH) &&
                localX >= 2 && localX <= 5 && localZ <= 4;
        final boolean eastBranch = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_EAST) &&
                localZ >= 2 && localZ <= 5 && localX >= 3;
        final boolean southBranch = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_SOUTH) &&
                localX >= 2 && localX <= 5 && localZ >= 3;
        final boolean westBranch = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_WEST) &&
                localZ >= 2 && localZ <= 5 && localX <= 4;
        final boolean centralRoom = localX >= 2 && localX <= 5 && localZ >= 2 && localZ <= 5;

        if(roomType == DungeonChunkRoomType.MINESHAFT_SHAFT)
            return localX >= 1 && localX <= 6 && localZ >= 1 && localZ <= 6;

        if(roomType == DungeonChunkRoomType.MINESHAFT_STORAGE ||
                roomType == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN ||
                roomType == DungeonChunkRoomType.MINESHAFT_CAVE_IN ||
                roomType == DungeonChunkRoomType.MINESHAFT_CROSSROAD ||
                broadRoom)
            return centralRoom || northBranch || eastBranch || southBranch || westBranch;

        return northBranch || eastBranch || southBranch || westBranch;
    }

    private Material pickFloorMaterial(int x, int z, DungeonChunkRoom room) {
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE && x >= room.getChunkBlockX() + 2 && x <= room.getChunkBlockX() + 5 &&
                z >= room.getChunkBlockZ() + 2 && z <= room.getChunkBlockZ() + 5)
            return Material.OAK_PLANKS;
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_SHAFT && x >= room.getChunkBlockX() + 3 && x <= room.getChunkBlockX() + 4 &&
                z >= room.getChunkBlockZ() + 3 && z <= room.getChunkBlockZ() + 4)
            return Material.OAK_PLANKS;
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN && (x == room.getChunkBlockX() + 1 || x == room.getChunkBlockX() + 6 ||
                z == room.getChunkBlockZ() + 1 || z == room.getChunkBlockZ() + 6))
            return Material.GRAVEL;

        final int floorPattern = Math.floorMod((x * 31) ^ (z * 17) ^ (room.getLayer() * 13) ^ room.getType().getId(), 12);
        if(floorPattern <= 1)
            return Material.GRAVEL;
        if(floorPattern <= 3)
            return Material.COBBLESTONE;
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN && floorPattern == 4)
            return Material.MOSSY_COBBLESTONE;
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN && floorPattern == 5)
            return Material.COARSE_DIRT;
        if(floorPattern == 6)
            return Material.COBBLESTONE;
        return Material.STONE;
    }

    private void openConnections(Chunk chunk, int roomX, int floorY, int roomZ, int dungeonOpeningMask, StructureContext structureContext, int ceilingY) {
        clearNorthOpening(chunk, roomX, floorY, roomZ, ceilingY, structureContext.hasNorthRoom(), hasConnection(dungeonOpeningMask, DungeonChunkRoom.CONNECTION_NORTH));
        clearEastOpening(chunk, roomX, floorY, roomZ, ceilingY, structureContext.hasEastRoom(), hasConnection(dungeonOpeningMask, DungeonChunkRoom.CONNECTION_EAST));
        clearSouthOpening(chunk, roomX, floorY, roomZ, ceilingY, structureContext.hasSouthRoom(), hasConnection(dungeonOpeningMask, DungeonChunkRoom.CONNECTION_SOUTH));
        clearWestOpening(chunk, roomX, floorY, roomZ, ceilingY, structureContext.hasWestRoom(), hasConnection(dungeonOpeningMask, DungeonChunkRoom.CONNECTION_WEST));
    }

    private static boolean hasConnection(int connectionMask, int connection) {
        return (connectionMask & connection) == connection;
    }

    private void clearNorthOpening(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY, boolean structureOpening, boolean dungeonOpening) {
        clearSideOpening(chunk, roomX + (structureOpening ? 1 : 2), roomX + (structureOpening ? 6 : 5), floorY + 1, ceilingY - 1, roomZ, true, structureOpening || dungeonOpening);
    }

    private void clearEastOpening(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY, boolean structureOpening, boolean dungeonOpening) {
        clearSideOpening(chunk, roomZ + (structureOpening ? 1 : 2), roomZ + (structureOpening ? 6 : 5), floorY + 1, ceilingY - 1, roomX + 7, false, structureOpening || dungeonOpening);
    }

    private void clearSouthOpening(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY, boolean structureOpening, boolean dungeonOpening) {
        clearSideOpening(chunk, roomX + (structureOpening ? 1 : 2), roomX + (structureOpening ? 6 : 5), floorY + 1, ceilingY - 1, roomZ + 7, true, structureOpening || dungeonOpening);
    }

    private void clearWestOpening(Chunk chunk, int roomX, int floorY, int roomZ, int ceilingY, boolean structureOpening, boolean dungeonOpening) {
        clearSideOpening(chunk, roomZ + (structureOpening ? 1 : 2), roomZ + (structureOpening ? 6 : 5), floorY + 1, ceilingY - 1, roomX, false, structureOpening || dungeonOpening);
    }

    private void clearSideOpening(Chunk chunk, int horizontalStart, int horizontalEnd, int minY, int maxY, int fixedAxis, boolean fixedZ, boolean shouldClear) {
        if(!shouldClear)
            return;

        for(int horizontal = horizontalStart; horizontal <= horizontalEnd; horizontal++) {
            for(int yy = minY; yy <= maxY; yy++) {
                if(fixedZ)
                    setGeneratedBlock(chunk.getBlock(horizontal, yy, fixedAxis), Material.AIR);
                else
                    setGeneratedBlock(chunk.getBlock(fixedAxis, yy, horizontal), Material.AIR);
            }
        }
    }

    private void addSupports(Chunk chunk, int roomX, int floorY, int roomZ, int openingMask, int ceilingY, DungeonChunkRoom room,
                             StructureContext structureContext) {
        final int beamY = ceilingY - 1;
        final boolean denseSupportGrid = shouldUseDenseSupportGrid(room, structureContext);

        if(hasConnection(openingMask, DungeonChunkRoom.CONNECTION_NORTH))
            addNorthSouthSupportFrame(chunk, roomX, floorY, roomZ + 1, beamY);
        if(hasConnection(openingMask, DungeonChunkRoom.CONNECTION_SOUTH))
            addNorthSouthSupportFrame(chunk, roomX, floorY, roomZ + 6, beamY);
        if(hasConnection(openingMask, DungeonChunkRoom.CONNECTION_WEST))
            addEastWestSupportFrame(chunk, roomX + 1, roomZ, floorY, beamY);
        if(hasConnection(openingMask, DungeonChunkRoom.CONNECTION_EAST))
            addEastWestSupportFrame(chunk, roomX + 6, roomZ, floorY, beamY);

        if(denseSupportGrid) {
            addNorthSouthSupportFrame(chunk, roomX, floorY, roomZ + 3, beamY);
            addEastWestSupportFrame(chunk, roomX + 3, roomZ, floorY, beamY);
        }
    }

    private boolean shouldUseDenseSupportGrid(DungeonChunkRoom room, StructureContext structureContext) {
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_SHAFT)
            return false;

        return room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE ||
                room.getType() == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN ||
                room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN ||
                structureContext.isBroadRoom();
    }

    private void addNorthSouthSupportFrame(Chunk chunk, int roomX, int floorY, int frameZ, int beamY) {
        placeSupportFooting(chunk, roomX + 2, floorY, frameZ);
        placeSupportFooting(chunk, roomX + 5, floorY, frameZ);
        for(int yy = floorY + 1; yy <= beamY; yy++) {
            setGeneratedBlock(chunk.getBlock(roomX + 2, yy, frameZ), Material.OAK_FENCE);
            setGeneratedBlock(chunk.getBlock(roomX + 5, yy, frameZ), Material.OAK_FENCE);
        }

        for(int x = roomX + 1; x <= roomX + 6; x++)
            setGeneratedBlock(chunk.getBlock(x, beamY, frameZ), Material.OAK_PLANKS);
    }

    private void addEastWestSupportFrame(Chunk chunk, int frameX, int roomZ, int floorY, int beamY) {
        placeSupportFooting(chunk, frameX, floorY, roomZ + 2);
        placeSupportFooting(chunk, frameX, floorY, roomZ + 5);
        for(int yy = floorY + 1; yy <= beamY; yy++) {
            setGeneratedBlock(chunk.getBlock(frameX, yy, roomZ + 2), Material.OAK_FENCE);
            setGeneratedBlock(chunk.getBlock(frameX, yy, roomZ + 5), Material.OAK_FENCE);
        }

        for(int z = roomZ + 1; z <= roomZ + 6; z++)
            setGeneratedBlock(chunk.getBlock(frameX, beamY, z), Material.OAK_PLANKS);
    }

    private void placeSupportFooting(Chunk chunk, int x, int floorY, int z) {
        final Material footingMaterial = pickSupportFootingMaterial(x, floorY, z);
        setGeneratedBlock(chunk.getBlock(x, floorY, z), footingMaterial);
        if(chunk.getBlock(x, floorY - 1, z).getType() == Material.AIR)
            setGeneratedBlock(chunk.getBlock(x, floorY - 1, z), footingMaterial == Material.GRAVEL ? Material.GRAVEL : Material.OAK_LOG);
    }

    private Material pickSupportFootingMaterial(int x, int floorY, int z) {
        return Math.floorMod((x * 37) ^ (z * 19) ^ floorY, 5) == 0 ? Material.GRAVEL : Material.OAK_LOG;
    }

    private void addFloorTimbers(Chunk chunk, int roomX, int floorY, int roomZ, int openingMask, DungeonChunkRoom room, StructureContext structureContext) {
        for(int x = roomX + 1; x <= roomX + 6; x++) {
            for(int z = roomZ + 1; z <= roomZ + 6; z++) {
                final int localX = x - roomX;
                final int localZ = z - roomZ;
                if(shouldPlaceRailBed(localX, localZ, openingMask) ||
                        isSupportFooting(localX, localZ, openingMask) ||
                        room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE && localX >= 2 && localX <= 5 && localZ >= 2 && localZ <= 5 ||
                        room.getType() == DungeonChunkRoomType.MINESHAFT_SHAFT && isFrameThreshold(localX, localZ) ||
                        room.getType() != DungeonChunkRoomType.MINESHAFT_SHAFT &&
                                structureContext.isBroadRoom() &&
                                isFrameThreshold(localX, localZ) &&
                                (localX == 3 || localX == 4 || localZ == 3 || localZ == 4))
                    setGeneratedBlock(chunk.getBlock(x, floorY, z), Material.OAK_PLANKS);
            }
        }

        if(structureContext.hasNorthRoom())
            addThresholdPlanks(chunk, roomX + 1, roomX + 6, roomZ + 1, floorY, true);
        if(structureContext.hasSouthRoom())
            addThresholdPlanks(chunk, roomX + 1, roomX + 6, roomZ + 6, floorY, true);
        if(structureContext.hasWestRoom())
            addThresholdPlanks(chunk, roomZ + 1, roomZ + 6, roomX + 1, floorY, false);
        if(structureContext.hasEastRoom())
            addThresholdPlanks(chunk, roomZ + 1, roomZ + 6, roomX + 6, floorY, false);
    }

    static boolean shouldPlaceRailBed(int localX, int localZ, int openingMask) {
        final boolean northSouth = (openingMask & (DungeonChunkRoom.CONNECTION_NORTH | DungeonChunkRoom.CONNECTION_SOUTH)) != 0;
        final boolean eastWest = (openingMask & (DungeonChunkRoom.CONNECTION_EAST | DungeonChunkRoom.CONNECTION_WEST)) != 0;
        return northSouth && (localX == 3 || localX == 4) ||
                eastWest && (localZ == 3 || localZ == 4);
    }

    private boolean isFrameThreshold(int localX, int localZ) {
        return localX == 1 || localX == 6 || localZ == 1 || localZ == 6;
    }

    private boolean isSupportFooting(int localX, int localZ, int openingMask) {
        return hasConnection(openingMask, DungeonChunkRoom.CONNECTION_NORTH) && (localZ == 1 && (localX == 2 || localX == 5)) ||
                hasConnection(openingMask, DungeonChunkRoom.CONNECTION_SOUTH) && (localZ == 6 && (localX == 2 || localX == 5)) ||
                hasConnection(openingMask, DungeonChunkRoom.CONNECTION_WEST) && (localX == 1 && (localZ == 2 || localZ == 5)) ||
                hasConnection(openingMask, DungeonChunkRoom.CONNECTION_EAST) && (localX == 6 && (localZ == 2 || localZ == 5));
    }

    private void addThresholdPlanks(Chunk chunk, int horizontalStart, int horizontalEnd, int fixedAxis, int floorY, boolean fixedZ) {
        for(int horizontal = horizontalStart; horizontal <= horizontalEnd; horizontal++) {
            if(fixedZ)
                setGeneratedBlock(chunk.getBlock(horizontal, floorY, fixedAxis), Material.OAK_PLANKS);
            else
                setGeneratedBlock(chunk.getBlock(fixedAxis, floorY, horizontal), Material.OAK_PLANKS);
        }
    }

    private void addRails(Chunk chunk, Random random, int roomX, int floorY, int roomZ, int openingMask, DungeonChunkRoom room,
                          StructureContext structureContext) {
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE || room.getType() == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN)
            return;

        if(hasConnection(openingMask, DungeonChunkRoom.CONNECTION_NORTH) || hasConnection(openingMask, DungeonChunkRoom.CONNECTION_SOUTH)) {
            final int railStartZ = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_NORTH) ? roomZ : roomZ + 1;
            final int railEndZ = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_SOUTH) ? roomZ + 7 : roomZ + 6;
            for(int z = railStartZ; z <= railEndZ; z++) {
                if(shouldSkipRailSegment(random, room, structureContext))
                    continue;
                placeRail(chunk.getBlock(roomX + 3, floorY + 1, z), Rail.Shape.NORTH_SOUTH);
                if(room.getType() != DungeonChunkRoomType.MINESHAFT_SHAFT &&
                        structureContext.isBroadRoom() &&
                        !shouldSkipRailSegment(random, room, structureContext))
                    placeRail(chunk.getBlock(roomX + 4, floorY + 1, z), Rail.Shape.NORTH_SOUTH);
            }
        }

        if(hasConnection(openingMask, DungeonChunkRoom.CONNECTION_EAST) || hasConnection(openingMask, DungeonChunkRoom.CONNECTION_WEST)) {
            final int railStartX = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_WEST) ? roomX : roomX + 1;
            final int railEndX = hasConnection(openingMask, DungeonChunkRoom.CONNECTION_EAST) ? roomX + 7 : roomX + 6;
            for(int x = railStartX; x <= railEndX; x++) {
                if(shouldSkipRailSegment(random, room, structureContext))
                    continue;
                placeRail(chunk.getBlock(x, floorY + 1, roomZ + 4), Rail.Shape.EAST_WEST);
                if(room.getType() != DungeonChunkRoomType.MINESHAFT_SHAFT &&
                        structureContext.isBroadRoom() &&
                        !shouldSkipRailSegment(random, room, structureContext))
                    placeRail(chunk.getBlock(x, floorY + 1, roomZ + 3), Rail.Shape.EAST_WEST);
            }
        }
    }

    private boolean shouldSkipRailSegment(Random random, DungeonChunkRoom room, StructureContext structureContext) {
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN)
            return random.nextInt(3) != 0;
        if(structureContext.isBroadRoom())
            return random.nextInt(4) == 0;
        return random.nextInt(5) == 0;
    }

    private void placeRail(Block block, Rail.Shape shape) {
        setGeneratedBlock(block, Material.RAIL);
        if(!(block.getBlockData() instanceof Rail))
            return;

        final Rail rail = (Rail) block.getBlockData();
        rail.setShape(shape);
        setGeneratedBlockData(block, rail);
    }

    private void addClutter(Chunk chunk, Random random, DungeonChunkRoom room, int roomX, int floorY, int roomZ, int openingMask, int ceilingY, StructureContext structureContext) {
        final int webY = Math.max(floorY + 1, ceilingY - 1);
        for(int x = roomX + 1; x <= roomX + 6; x++) {
            for(int z = roomZ + 1; z <= roomZ + 6; z++) {
                if(isPrimaryRailLane(roomX, roomZ, x, z, openingMask, structureContext))
                    continue;
                if(random.nextInt(room.getType() == DungeonChunkRoomType.MINESHAFT_SPIDER_DEN ? 2 : structureContext.isBroadRoom() ? 10 : 6) == 0)
                    setGeneratedBlock(chunk.getBlock(x, webY, z), Material.COBWEB);
                if(random.nextInt(structureContext.isBroadRoom() ? 16 : 12) == 0)
                    setGeneratedBlock(chunk.getBlock(x, floorY + 1, z), Material.COBWEB);
            }
        }

        final int torchY = Math.min(floorY + 3, ceilingY - 1);
        if(room.getType() != DungeonChunkRoomType.MINESHAFT_SPIDER_DEN &&
                !hasConnection(openingMask, DungeonChunkRoom.CONNECTION_NORTH) &&
                random.nextInt(getTorchChance(room, structureContext)) == 0)
            MaterialUtils.setWallTorch(chunk.getBlock(roomX + 3, torchY, roomZ + 1), BlockFace.SOUTH);
        if(room.getType() != DungeonChunkRoomType.MINESHAFT_SPIDER_DEN &&
                !hasConnection(openingMask, DungeonChunkRoom.CONNECTION_EAST) &&
                random.nextInt(getTorchChance(room, structureContext)) == 0)
            MaterialUtils.setWallTorch(chunk.getBlock(roomX + 6, torchY, roomZ + 3), BlockFace.WEST);
        if(room.getType() != DungeonChunkRoomType.MINESHAFT_SPIDER_DEN &&
                !hasConnection(openingMask, DungeonChunkRoom.CONNECTION_SOUTH) &&
                random.nextInt(getTorchChance(room, structureContext)) == 0)
            MaterialUtils.setWallTorch(chunk.getBlock(roomX + 4, torchY, roomZ + 6), BlockFace.NORTH);
        if(room.getType() != DungeonChunkRoomType.MINESHAFT_SPIDER_DEN &&
                !hasConnection(openingMask, DungeonChunkRoom.CONNECTION_WEST) &&
                random.nextInt(getTorchChance(room, structureContext)) == 0)
            MaterialUtils.setWallTorch(chunk.getBlock(roomX + 1, torchY, roomZ + 4), BlockFace.EAST);
    }

    private int getTorchChance(DungeonChunkRoom room, StructureContext structureContext) {
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_STORAGE)
            return 2;
        if(room.getType() == DungeonChunkRoomType.MINESHAFT_CAVE_IN)
            return 7;
        return structureContext.isBroadRoom() ? 8 : 5;
    }

    private void addStorage(Chunk chunk, Random random, int roomX, int floorY, int roomZ, int openingMask, StructureContext structureContext) {
        final BlockFace chestFacing;
        final int chestX;
        final int chestZ;

        if(hasConnection(openingMask, DungeonChunkRoom.CONNECTION_NORTH)) {
            chestFacing = BlockFace.SOUTH;
            chestX = roomX + 3;
            chestZ = roomZ + 6;
        } else if(hasConnection(openingMask, DungeonChunkRoom.CONNECTION_EAST)) {
            chestFacing = BlockFace.WEST;
            chestX = roomX + 1;
            chestZ = roomZ + 3;
        } else if(hasConnection(openingMask, DungeonChunkRoom.CONNECTION_SOUTH)) {
            chestFacing = BlockFace.NORTH;
            chestX = roomX + 4;
            chestZ = roomZ + 1;
        } else {
            chestFacing = BlockFace.EAST;
            chestX = roomX + 6;
            chestZ = roomZ + 4;
        }

        final int storageInset = structureContext.isBroadRoom() ? 1 : 2;
        for(int x = roomX + storageInset; x <= roomX + (7 - storageInset); x++)
            for(int z = roomZ + storageInset; z <= roomZ + (7 - storageInset); z++)
                setGeneratedBlock(chunk.getBlock(x, floorY, z), Material.OAK_PLANKS);

        MaterialUtils.setChestFacing(chunk.getBlock(chestX, floorY + 1, chestZ), chestFacing);
        setGeneratedBlock(chunk.getBlock(roomX + 2, floorY + 1, roomZ + 2), Material.BARREL);
        setGeneratedBlock(chunk.getBlock(roomX + 5, floorY + 1, roomZ + 5), Material.BARREL);
        if(random.nextBoolean())
            setGeneratedBlock(chunk.getBlock(roomX + 2, floorY + 2, roomZ + 2), Material.OAK_PLANKS);
        if(random.nextBoolean())
            setGeneratedBlock(chunk.getBlock(roomX + 5, floorY + 2, roomZ + 5), Material.OAK_PLANKS);
        if(random.nextBoolean())
            setGeneratedBlock(chunk.getBlock(roomX + 5, floorY + 1, roomZ + 2), Material.BARREL);
        populateChest(chunk.getBlock(chestX, floorY + 1, chestZ), random);
    }

    private void addShaft(Chunk chunk, DungeonChunkRoom room, int roomX, RoomProfile roomProfile, StructureContext structureContext) {
        final DungeonChunkRoom directBelowRoom = getAdjacentRoom(room, 0, -1, 0);
        final RoomProfile aboveProfile = structureContext.hasAboveRoom() ? analyzeRoomProfile(chunk, structureContext.getAboveRoom()) : null;
        final RoomProfile belowProfile = directBelowRoom != null ? analyzeRoomProfile(chunk, directBelowRoom) : null;
        final int minY = belowProfile != null ? belowProfile.getFloorY() + 1 : roomProfile.getFloorY() - 3;
        final int maxY = aboveProfile != null ? aboveProfile.getFloorY() + 1 : roomProfile.getCeilingY() - 1;

        for(int yy = minY; yy <= maxY; yy++) {
            for(int x = roomX + 2; x <= roomX + 5; x++)
                for(int z = room.getChunkBlockZ() + 2; z <= room.getChunkBlockZ() + 5; z++)
                    setGeneratedBlock(chunk.getBlock(x, yy, z), Material.AIR);
        }

        for(int x = roomX + 1; x <= roomX + 6; x++) {
            for(int z = room.getChunkBlockZ() + 1; z <= room.getChunkBlockZ() + 6; z++) {
                final boolean insideShaft = x >= roomX + 2 && x <= roomX + 5 && z >= room.getChunkBlockZ() + 2 && z <= room.getChunkBlockZ() + 5;
                if(!insideShaft)
                    setGeneratedBlock(chunk.getBlock(x, roomProfile.getFloorY(), z), Material.OAK_PLANKS);
            }
        }

        final int ladderX = roomX + 1;
        final int ladderZStart = room.getChunkBlockZ() + 2;
        for(int yy = minY; yy <= maxY; yy++) {
            MaterialUtils.setLadderFacing(chunk.getBlock(ladderX, yy, ladderZStart), BlockFace.EAST);
            MaterialUtils.setLadderFacing(chunk.getBlock(ladderX, yy, ladderZStart + 1), BlockFace.EAST);
        }

        if(belowProfile != null) {
            setGeneratedBlock(chunk.getBlock(roomX + 1, belowProfile.getFloorY(), room.getChunkBlockZ() + 2), Material.OAK_PLANKS);
            setGeneratedBlock(chunk.getBlock(roomX + 1, belowProfile.getFloorY(), room.getChunkBlockZ() + 3), Material.OAK_PLANKS);
        }
    }

    private void addSpiderDen(Chunk chunk, Random random, int roomX, int floorY, int roomZ, StructureContext structureContext) {
        for(int x = roomX + 1; x <= roomX + 6; x++) {
            for(int z = roomZ + 1; z <= roomZ + 6; z++) {
                final boolean centralLane = isPrimaryRailLane(roomX, roomZ, x, z,
                        DungeonChunkRoom.CONNECTION_NORTH | DungeonChunkRoom.CONNECTION_SOUTH |
                                DungeonChunkRoom.CONNECTION_EAST | DungeonChunkRoom.CONNECTION_WEST,
                        structureContext);
                if(centralLane)
                    continue;
                if(random.nextInt(2) == 0)
                    setGeneratedBlock(chunk.getBlock(x, floorY + 1, z), Material.COBWEB);
                if(random.nextInt(2) == 0)
                    setGeneratedBlock(chunk.getBlock(x, floorY + 3, z), Material.COBWEB);
                if(random.nextBoolean())
                    setGeneratedBlock(chunk.getBlock(x, floorY + 4, z), Material.COBWEB);
            }
        }

        final Block spawnerBlock = chunk.getBlock(roomX + 4, floorY + 1, roomZ + 4);
        if(spawnerBlock.getType() == Material.AIR) {
            final EntityType type = Core.getConfigHandler().isMobSpawnerAllowed("CAVE_SPIDER") ? EntityType.CAVE_SPIDER : EntityType.SPIDER;
            final GenerationSpawnerEvent event = new GenerationSpawnerEvent(
                    spawnerBlock,
                    type,
                    GenerationSpawnerEvent.GenerationSpawnerCause.OTHER,
                    random
            );
            Bukkit.getServer().getPluginManager().callEvent(event);
            event._apply();
        }
    }

    private void addCaveIn(Chunk chunk, Random random, int roomX, int floorY, int roomZ, StructureContext structureContext, int ceilingY) {
        final int collapseY = Math.max(floorY + 2, ceilingY - 1);
        for(int x = roomX + 1; x <= roomX + 6; x++) {
            for(int z = roomZ + 1; z <= roomZ + 6; z++) {
                if(isPrimaryRailLane(roomX, roomZ, x, z,
                        DungeonChunkRoom.CONNECTION_NORTH | DungeonChunkRoom.CONNECTION_SOUTH |
                                DungeonChunkRoom.CONNECTION_EAST | DungeonChunkRoom.CONNECTION_WEST,
                        structureContext))
                    continue;

                if(random.nextInt(3) != 0) {
                    setGeneratedBlock(chunk.getBlock(x, collapseY, z), Material.GRAVEL);
                    if(collapseY - 1 > floorY + 1 && random.nextBoolean())
                        setGeneratedBlock(chunk.getBlock(x, collapseY - 1, z), Material.GRAVEL);
                }
            }
        }

        setGeneratedBlock(chunk.getBlock(roomX + 2, floorY + 1, roomZ + 2), Material.OAK_LOG);
        setGeneratedBlock(chunk.getBlock(roomX + 5, floorY + 1, roomZ + 5), Material.OAK_LOG);
        if(random.nextBoolean())
            setGeneratedBlock(chunk.getBlock(roomX + 3, floorY + 1, roomZ + 2), Material.OAK_FENCE);
        if(random.nextBoolean())
            setGeneratedBlock(chunk.getBlock(roomX + 4, floorY + 1, roomZ + 5), Material.OAK_FENCE);
    }

    private boolean isPrimaryRailLane(int roomX, int roomZ, int x, int z, int openingMask, StructureContext structureContext) {
        final boolean broadRoom = structureContext.isBroadRoom();
        final boolean northSouthLane = (hasConnection(openingMask, DungeonChunkRoom.CONNECTION_NORTH) ||
                hasConnection(openingMask, DungeonChunkRoom.CONNECTION_SOUTH)) &&
                (x == roomX + 3 || (broadRoom && x == roomX + 4));
        final boolean eastWestLane = (hasConnection(openingMask, DungeonChunkRoom.CONNECTION_EAST) ||
                hasConnection(openingMask, DungeonChunkRoom.CONNECTION_WEST)) &&
                (z == roomZ + 4 || (broadRoom && z == roomZ + 3));
        return northSouthLane || eastWestLane;
    }

    private DungeonChunkRoom getAdjacentRoom(DungeonChunkRoom room, int roomOffsetX, int layerOffset, int roomOffsetZ) {
        final int targetLayer = room.getLayer() + layerOffset;
        if(targetLayer < DungeonMazeLayout.MIN_LAYER || targetLayer > DungeonMazeLayout.MAX_LAYER)
            return null;

        return room.getChunk().getRoom(room.getX() + roomOffsetX, targetLayer, room.getZ() + roomOffsetZ);
    }

    private void populateChest(Block chestBlock, Random random) {
        final GenerationChestEvent event = new GenerationChestEvent(chestBlock, random, createLoot(random), MazeStructureType.MINESHAFT_ROOM);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if(!event.isCancelled() && event.getBlock().getType() == Material.CHEST)
            ChestUtils.addItemsToChest(event.getBlock(), event.getContents(), !event.getAddContentsInOrder(), random);
    }

    private List<ItemStack> createLoot(Random random) {
        final List<ItemStack> loot = new ArrayList<>();
        if(random.nextBoolean())
            loot.add(MaterialUtils.createItemStack(Material.RAIL, 16));
        if(random.nextInt(100) < 60)
            loot.add(MaterialUtils.createItemStack(Material.TORCH, 12));
        if(random.nextInt(100) < 55)
            loot.add(MaterialUtils.createItemStack(Material.COAL, 8));
        if(random.nextInt(100) < 45)
            loot.add(MaterialUtils.createItemStack(Material.IRON_INGOT, 3));
        if(random.nextInt(100) < 30)
            loot.add(MaterialUtils.createItemStack(Material.GOLD_INGOT, 2));
        if(random.nextInt(100) < 25)
            loot.add(MaterialUtils.createItemStack(Material.BREAD, 2));
        if(random.nextInt(100) < 15)
            loot.add(MaterialUtils.createItemStack(Material.REDSTONE, 6));
        if(random.nextInt(100) < 10)
            loot.add(MaterialUtils.createItemStack(Material.DIAMOND, 1));
        if(loot.isEmpty())
            loot.add(MaterialUtils.createItemStack(Material.COAL, 4));
        return loot;
    }

    private long encodeCell(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
    }

    private static final class RoomProfile {

        private final int floorY;
        private final int ceilingY;
        private final int dungeonOpeningMask;

        private RoomProfile(int floorY, int ceilingY, int dungeonOpeningMask) {
            this.floorY = floorY;
            this.ceilingY = ceilingY;
            this.dungeonOpeningMask = dungeonOpeningMask;
        }

        private int getFloorY() {
            return this.floorY;
        }

        private int getCeilingY() {
            return this.ceilingY;
        }

        private int getDungeonOpeningMask() {
            return this.dungeonOpeningMask;
        }
    }

    private static final class StructureContext {

        private final boolean northRoom;
        private final boolean eastRoom;
        private final boolean southRoom;
        private final boolean westRoom;
        private final DungeonChunkRoom aboveRoom;
        private final DungeonChunkRoom belowRoom;

        private StructureContext(DungeonChunkRoom room, boolean northRoom, boolean eastRoom, boolean southRoom, boolean westRoom,
                                 DungeonChunkRoom aboveRoom, DungeonChunkRoom belowRoom) {
            this.northRoom = northRoom;
            this.eastRoom = eastRoom;
            this.southRoom = southRoom;
            this.westRoom = westRoom;
            this.aboveRoom = aboveRoom != null && aboveRoom.getStructureId() == room.getStructureId() ? aboveRoom : null;
            this.belowRoom = belowRoom != null && belowRoom.getStructureId() == room.getStructureId() ? belowRoom : null;
        }

        private boolean hasNorthRoom() {
            return this.northRoom;
        }

        private boolean hasEastRoom() {
            return this.eastRoom;
        }

        private boolean hasSouthRoom() {
            return this.southRoom;
        }

        private boolean hasWestRoom() {
            return this.westRoom;
        }

        private boolean hasAboveRoom() {
            return this.aboveRoom != null;
        }

        private boolean hasBelowRoom() {
            return this.belowRoom != null;
        }

        private DungeonChunkRoom getAboveRoom() {
            return this.aboveRoom;
        }

        private DungeonChunkRoom getBelowRoom() {
            return this.belowRoom;
        }

        private int getHorizontalConnectionCount() {
            int connections = 0;
            if(this.northRoom)
                connections++;
            if(this.eastRoom)
                connections++;
            if(this.southRoom)
                connections++;
            if(this.westRoom)
                connections++;

            return connections;
        }

        private boolean isBroadRoom() {
            return getHorizontalConnectionCount() >= 3;
        }
    }
}
