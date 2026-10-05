package dev.rafadegolin.craftoffice.net;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Os avisos {@code peer_add}, {@code peer_remove} e mudança de vídeo do estudo,
 * num pacote só. Quem tem {@code initiator} manda a oferta.
 */
public record PeerPayload(Action action, UUID peer, boolean initiator, boolean video) implements CustomPacketPayload {
	public enum Action { ADD, REMOVE, UPDATE }

	public static final Type<PeerPayload> TYPE = new Type<>(CraftOffice.id("peer"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PeerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Action::ordinal), PeerPayload::action,
			UUIDUtil.STREAM_CODEC, PeerPayload::peer,
			ByteBufCodecs.BOOL, PeerPayload::initiator,
			ByteBufCodecs.BOOL, PeerPayload::video,
			PeerPayload::new);

	@Override
	public Type<PeerPayload> type() {
		return TYPE;
	}
}
