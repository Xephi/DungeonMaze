package com.timvisee.dungeonmaze.populator.maze;

import java.util.Random;

import com.timvisee.dungeonmaze.world.dungeon.chunk.DungeonChunk;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.timvisee.dungeonmaze.DungeonMaze;

public abstract class MazeRoomBlockPopulator extends MazeLayerBlockPopulator {
	
	/**
	 * Population method.
     *
	 * @param args Populator arguments.
	 */
	@Override
	public void populateLayer(MazeLayerBlockPopulatorArgs args) {
		World world = args.getWorld();
		Chunk chunk = args.getSourceChunk();
		Random rand = args.getRandom();
        DungeonChunk dungeonChunk = args.getDungeonChunk();
		int layer = args.getLayer();
		int y = args.getY();
		
		// The 4 rooms on each layer
		for(int chunkX = 0; chunkX < 16; chunkX += 8) {
			for(int chunkZ = 0; chunkZ < 16; chunkZ += 8) {

                // Iterate through this room
                final int iterations = getRoomPopulationIterations();
                for(int i = 0; i < iterations; i++) {

                    // Check whether this this room should be populated based on it's chance
                    if(rand.nextFloat() >= getRoomPopulationChance())
                        continue;

                    // Make sure this room isn't constant
                    if(DungeonMaze.instance.isConstantRoom(world.getName(), chunk, chunkX, y, chunkZ))
                        continue;

                    // Calculate the global X and Y coordinates
                    int x = (chunk.getX() * 16) + chunkX;
                    int z = (chunk.getZ() * 16) + chunkZ;

                    // Get the floor and ceiling offset
                    int floorOffset = getFloorOffset(chunkX, y, chunkZ, chunk);
                    int ceilingOffset = getCeilingOffset(chunkX, y, chunkZ, chunk);

                    // Construct the DMMazePopulatorArgs to use the the populateMaze method
                    MazeRoomBlockPopulatorArgs newArgs = new MazeRoomBlockPopulatorArgs(world, rand, chunk, dungeonChunk, layer, x, y, z, floorOffset, ceilingOffset);

                    // Populate the maze
                    populateRoom(newArgs);
                }
			}
		}
	}
	
	/**
	 * Population method.
     *
	 * @param args Populator arguments.
	 */
	public abstract void populateRoom(MazeRoomBlockPopulatorArgs args);
	
	/**
	 * Get the floor offset in a specific room.
     *
	 * @param x X coordinate.
	 * @param y Y coordinate.
	 * @param z Z coordinate.
	 * @param c Chunk.
     *
	 * @return Floor offset.
	 */
	private int getFloorOffset(int x, int y, int z, Chunk c) {
		Block testBlock = c.getBlock(x + 3, y, z + 3);
		Material typeId = testBlock.getType();
		
		// x and z +2 so that you aren't inside a wall!
		if(!(typeId == Material.COBBLESTONE || typeId == Material.MOSSY_COBBLESTONE ||
				typeId == Material.NETHERRACK || typeId == Material.SOUL_SAND))
			return 1;
		
		return 0;
	}
	
	/**
	 * Get the ceiling offset in a specific room.
     *
	 * @param x X coordinate.
	 * @param y Y coordinate.
	 * @param z Z coordinate.
	 * @param c The chunk.
     *
	 * @return Ceiling offset.
	 */
	private int getCeilingOffset(int x, int y, int z, Chunk c) {
		Block testBlock = c.getBlock(x + 3, y + 6, z + 3);
		Material typeId = testBlock.getType();
		
		// x and z +2 so that you aren't inside a wall!
		if(!(typeId == Material.COBBLESTONE || typeId == Material.MOSSY_COBBLESTONE ||
				typeId == Material.NETHERRACK || typeId == Material.SOUL_SAND))
			return 1;
		
		return 0;
	}

    /**
     * Get the room population chance. This value is between 0.0 and 1.0.
     *
     * @return The population chance of the room.
     */
    public abstract float getRoomPopulationChance();

    /**
     * Get the number of times to iterate through each room.
     *
     * @return The number of iterations.
     */
    public int getRoomPopulationIterations() {
        return 1;
    }
}
