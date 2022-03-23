package com.example.examplemod;


import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@Mod.EventBusSubscriber(modid = "examplemod", bus = Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {
	
	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onTickEvent(TickEvent.ServerTickEvent event) {
		if(Minecraft.getInstance().player == null)
			return;
		if(ExampleMod.map == null)
			return;
		
		ExampleMod.map.onTick();
	}
	
	@SubscribeEvent
	public static void onBlockEvents(BlockEvent event) {
		if(Minecraft.getInstance().player == null)
			return;
		if(ExampleMod.map == null)
			return;
		
		ExampleMod.map.updateChunk(new ChunkPos(event.getPos()));
	}
}