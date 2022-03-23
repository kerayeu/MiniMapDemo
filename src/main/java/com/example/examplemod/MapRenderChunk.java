package com.example.examplemod;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.MaterialColor;

public class MapRenderChunk {
	DynamicTexture tex;
	public NativeImage pixels;
	public LevelChunk chunk;
	public ChunkPos chunkPos;
	
	public volatile boolean dirtyChunk = false;
	public volatile boolean dirtyPixels = false;
	
	public boolean testBounds(int minX, int minZ, int maxX, int maxZ) {
		return chunkPos.x < minX || chunkPos.x > maxX || chunkPos.z < minZ || chunkPos.z > maxZ;
	}
	
	public void updatePixels() {
		if(pixels == null) {
			pixels = new NativeImage(16, 16, false);
		}

		dirtyChunk = false;
		
		for(int x=0; x<16; x++) {
			for(int z=0; z<16; z++) {
				int height = chunk.getHeight(Types.WORLD_SURFACE, x, z);
				
				BlockPos tpos = new BlockPos(x, height, z);
				BlockState state = chunk.getBlockState(tpos);
				MaterialColor mapColor = state.getMapColor(chunk, tpos);
				
				int r = mapColor.col & 0xFF;
				int g = (mapColor.col >> 8) & 0xFF;
				int b = (mapColor.col >> 16) & 0xFF;
				int bgr = (r << 16) | (g << 8) | b;
				
				pixels.setPixelRGBA(x, z, bgr | 0xFF000000);
			}
		}

		dirtyPixels = true;
	}
	
	public void updateTexture() {
		if(tex == null) {
			tex = new DynamicTexture(pixels);
			tex.setFilter(false, false);
		}
		dirtyPixels = false;
		tex.upload();
	}
}
