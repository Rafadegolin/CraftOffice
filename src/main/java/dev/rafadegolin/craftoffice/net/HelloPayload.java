package dev.rafadegolin.craftoffice.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.rafadegolin.craftoffice.CraftOffice;

/** Primeiro aviso do cliente: diz que tem o mod e qual versão do protocolo fala. */
public record HelloPayload(int protocol) implements CustomPacketPayload {
	public static final Type<HelloPayload> TYPE = new Type<>(CraftOffice.id("hello"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HelloPayload::protocol,
			HelloPayload::new);

	@Override
	public Type<HelloPayload> type() {
		return TYPE;
	}
}
