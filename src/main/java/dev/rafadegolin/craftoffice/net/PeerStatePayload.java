package dev.rafadegolin.craftoffice.net;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.rafadegolin.craftoffice.CraftOffice;

/** Estado de um vizinho, repassado pelo servidor: microfone e câmera ligados. */
public record PeerStatePayload(UUID peer, boolean mic, boolean camera) implements CustomPacketPayload {
	public static final Type<PeerStatePayload> TYPE = new Type<>(CraftOffice.id("peer_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PeerStatePayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, PeerStatePayload::peer,
			ByteBufCodecs.BOOL, PeerStatePayload::mic,
			ByteBufCodecs.BOOL, PeerStatePayload::camera,
			PeerStatePayload::new);

	@Override
	public Type<PeerStatePayload> type() {
		return TYPE;
	}
}
