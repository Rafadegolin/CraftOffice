package dev.rafadegolin.craftoffice.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Estado do cliente. {@code active} é ter aceitado o consentimento: sem ele o
 * servidor não junta este player com ninguém. Microfone e câmera ficam para
 * os ícones dos outros players numa fase futura.
 */
public record StatePayload(boolean active, boolean mic, boolean camera) implements CustomPacketPayload {
	public static final Type<StatePayload> TYPE = new Type<>(CraftOffice.id("state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, StatePayload::active,
			ByteBufCodecs.BOOL, StatePayload::mic,
			ByteBufCodecs.BOOL, StatePayload::camera,
			StatePayload::new);

	@Override
	public Type<StatePayload> type() {
		return TYPE;
	}
}
