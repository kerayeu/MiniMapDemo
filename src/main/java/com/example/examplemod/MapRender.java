package com.example.examplemod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.lwjgl.opengl.GL32C;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.client.gui.IIngameOverlay;

@SuppressWarnings("resource")
public class MapRender implements IIngameOverlay {
	
	DynamicTexture stencilTexture;

	private ArrayDeque<MapRenderChunk> reusedStack = new ArrayDeque<>();
	public Map<ChunkPos, MapRenderChunk> chunks = new HashMap<>();
	ReadWriteLock lock = new ReentrantReadWriteLock();
	
	ChunkPos lastChunkPos;
	
	boolean shouldRefreshChunkList = false;
	
	public void onTick() {
		var pcp = Minecraft.getInstance().player.chunkPosition();
		
		if(shouldRefreshChunkList || !pcp.equals(lastChunkPos)) {
			lastChunkPos = pcp;
			findChunks(pcp);
		}
		
//		lock.readLock().lock();
		for(var chk : chunks.values()) {
			if(chk.dirtyChunk) {
				// could use a queue instead so we don't update all pixels on single frame
				chk.updatePixels();
			}
		}
//		lock.readLock().unlock();
	}
	
	private void findChunks(ChunkPos center) {
		int minX = center.x - 6;
		int minZ = center.z - 6;
		int maxX = center.x + 6;
		int maxZ = center.z + 6;
		
		
		lock.writeLock().lock();
		shouldRefreshChunkList = false;
		
		chunks.values().removeIf(c -> {
			if(c.testBounds(minX, minZ, maxX, maxZ)) {
				reusedStack.add(c);
				return true;
			} else {
				return false;
			}
		});
		
		for(int x = minX; x<=maxX; x++) {
			for(int z = minZ; z<=maxZ; z++) {
				ChunkPos cp = new ChunkPos(x, z);
				if(chunks.containsKey(cp))
					continue;
				
				LevelChunk c = (LevelChunk) Minecraft.getInstance().player.level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
				
				if(c == null)
					continue;
				
				var chunk = obtainChunkRender();
				
				chunk.chunk = c;
				chunk.chunkPos = cp;
				chunk.dirtyChunk = true;
				chunk.dirtyPixels = false;
				
				chunks.put(cp, chunk);
			}
		}
		
		lock.writeLock().unlock();
	}

	private MapRenderChunk obtainChunkRender() {
		MapRenderChunk chunk = reusedStack.pollLast();
		if(chunk == null) {
			chunk = new MapRenderChunk();
		}
		
		return new MapRenderChunk();
	}

	int stencilRadius = 48;
	int stencilWidth = stencilRadius * 2;

	private AbstractTexture indicatorTexture;
	
	@Override
	public void render(ForgeIngameGui gui, PoseStack poseStack, float partialTick, int width, int height) {
		if(stencilTexture == null) {
			int stencilRadiusSq = stencilRadius * stencilRadius;
			stencilTexture = new DynamicTexture(stencilWidth, stencilWidth, false);
			
			var pixels = stencilTexture.getPixels();
			
			for(int x=0; x<stencilWidth; x++) {
				for(int z=0; z<stencilWidth; z++) {
					float dx = x - stencilRadius;
					float dz = z - stencilRadius;
					float distSq = dx*dx + dz*dz;
					
					pixels.setPixelRGBA(x, z, distSq < stencilRadiusSq ? 0xFFFFFFFF : 0x00000000);
				}
			}
			stencilTexture.setFilter(false, false);
			stencilTexture.upload();
		}
		
		if(indicatorTexture == null) {
			indicatorTexture = Minecraft.getInstance().getTextureManager().getTexture(new ResourceLocation("examplemod", "textures/overlays/indicator.png"));
		}
		
        Vec3 playerPos = Minecraft.getInstance().player.getPosition(partialTick);
        float playerRot = Minecraft.getInstance().player.getViewYRot(partialTick);
        
        poseStack.pushPose();
        poseStack.setIdentity();
        poseStack.translate(width - stencilRadius, height/3f, 0);

        GL32C.glEnable(GL32C.GL_STENCIL_TEST);
        
        RenderSystem.stencilOp(GL32C.GL_REPLACE, GL32C.GL_REPLACE, GL32C.GL_REPLACE);
        RenderSystem.stencilFunc(GL32C.GL_ALWAYS, 1, 0xFF);
        RenderSystem.stencilMask(0xFF);

        RenderSystem.colorMask(false, false, false, false);
        GL32C.glClear(GL32C.GL_STENCIL_BUFFER_BIT);
        
        RenderSystem.setShaderTexture(0, stencilTexture.getId());
        // should potentially ignore GUI scaling here somehow, but I couldn't find anything useful for that now
		GuiComponent.blit(poseStack, -stencilRadius, -stencilRadius, stencilWidth, stencilWidth, 0, 0, 1, 1, 1, 1);
        
//		RenderSystem.enableBlend();
		
        poseStack.pushPose();
        poseStack.mulPose(new Quaternion(new Vector3f(0, 0, 1), -playerRot-180, true));
        poseStack.translate((float)-playerPos.x, (float)-playerPos.z, 0);
        
        RenderSystem.stencilOp(GL32C.GL_KEEP, GL32C.GL_KEEP, GL32C.GL_KEEP);
        RenderSystem.stencilFunc(GL32C.GL_EQUAL, 1, 0xFF);
        RenderSystem.stencilMask(0x00);
        RenderSystem.colorMask(true, true, true, true);
        
		lock.readLock().lock();
		for(var chk : chunks.values()) {
			// could use a queue instead so we don't upload all textures on single frame
			if(chk.dirtyPixels) {
				chk.updateTexture();
			}
			
			if(chk.tex != null) {
		        poseStack.pushPose();
		        poseStack.translate(chk.chunkPos.getMinBlockX(), chk.chunkPos.getMinBlockZ(), 0);

		        RenderSystem.setShaderTexture(0, chk.tex.getId());
				GuiComponent.blit(poseStack, 0, 0, 16, 16, 0, 0, 16, 16, 16, 16);
		        poseStack.popPose();
			}
		}
		lock.readLock().unlock();
		
        poseStack.popPose();

        GL32C.glDisable(GL32C.GL_STENCIL_TEST);
        RenderSystem.setShaderTexture(0, indicatorTexture.getId());
		GuiComponent.blit(poseStack, -3, -4, 8, 8, 0, 0, 8, 8, 8, 8);
        
        poseStack.popPose();

	}

	public void updateChunk(ChunkPos cp) {
//		lock.readLock().lock();
		var chunk = chunks.get(cp);
		if(chunk != null) {
			chunk.dirtyChunk = true;
		} else {
			shouldRefreshChunkList = true;
		}
//		lock.readLock().unlock();
	}

}
