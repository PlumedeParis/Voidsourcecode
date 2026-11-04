package com.mytestclient.mixin;

import com.mytestclient.module.Modules;
import com.mytestclient.module.world.NewChunksModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import com.mytestclient.module.world.EventLocatorModule;
import com.mytestclient.util.TickRate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * New Chunks: watches block updates the server sends. Injected at TAIL so it only runs on the
 * client thread (the handlers re-schedule themselves from the network thread first).
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    /** Player Finder (activity radar): HEAD on the client thread, so the old block is still in the world. */
    @Inject(method = "onBlockUpdate", at = @At("HEAD"), require = 0)
    private void mytestclient$blockUpdateBefore(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        var finder = finderOnClientThread();
        if (finder != null) finder.onBlockChange(packet.getPos(), net.minecraft.client.MinecraftClient.getInstance().world.getBlockState(packet.getPos()), packet.getState());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"), require = 0)
    private void mytestclient$deltaUpdateBefore(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        var finder = finderOnClientThread();
        if (finder == null) return;
        var world = net.minecraft.client.MinecraftClient.getInstance().world;
        packet.visitUpdates((pos, state) -> finder.onBlockChange(pos.toImmutable(), world.getBlockState(pos), state));
    }

    private static com.mytestclient.module.utility.PlayerFinderModule finderOnClientThread() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        // the handlers run twice: first on the network thread (which reschedules), then on the client thread
        if (!mc.isOnThread() || mc.world == null) return null;
        var finder = Modules.get(com.mytestclient.module.utility.PlayerFinderModule.class);
        return finder != null && finder.isEnabled() ? finder : null;
    }

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void mytestclient$blockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        com.mytestclient.flight.VoxelWorld.onBlockChange(packet.getPos());
        NewChunksModule newChunks = Modules.get(NewChunksModule.class);
        if (newChunks != null && newChunks.isEnabled()) newChunks.onBlockUpdate(packet.getPos(), packet.getState());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void mytestclient$deltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        packet.visitUpdates((pos, state) -> com.mytestclient.flight.VoxelWorld.onBlockChange(pos));
        NewChunksModule newChunks = Modules.get(NewChunksModule.class);
        if (newChunks != null && newChunks.isEnabled()) packet.visitUpdates(newChunks::onBlockUpdate);
    }

    /** The server sends the world time once per second (20 ticks): used to estimate TPS. */
    @Inject(method = "onWorldTimeUpdate", at = @At("TAIL"))
    private void mytestclient$tickRate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        TickRate.onTimeUpdate();
    }

    /** Event Locator: global world events (wither spawn, dragon death, end portal) carry exact coordinates. */
    @Inject(method = "onWorldEvent", at = @At("TAIL"))
    private void mytestclient$worldEvent(WorldEventS2CPacket packet, CallbackInfo ci) {
        EventLocatorModule locator = Modules.get(EventLocatorModule.class);
        if (locator != null && locator.isEnabled()) locator.onWorldEvent(packet.getEventId(), packet.getPos(), packet.isGlobal());
    }

    /** Event Locator: positioned sounds (lightning, explosions, raids). */
    @Inject(method = "onPlaySound", at = @At("TAIL"))
    private void mytestclient$sound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        EventLocatorModule locator = Modules.get(EventLocatorModule.class);
        if (locator != null && locator.isEnabled()) {
            locator.onSound(packet.getSound().value().id().toString(), packet.getX(), packet.getY(), packet.getZ());
        }
    }
}
