package dev.rafadegolin.craftoffice.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.rafadegolin.craftoffice.CraftOffice;
import dev.rafadegolin.craftoffice.OfficeStatus;

/**
 * Estado do cliente. {@code active} é ter aceitado o consentimento: sem ele o
 * servidor não junta este player com ninguém. Microfone e câmera vão para os
 * vizinhos; o status vai para todos.
 */
public record StatePayload(boolean active, boolean mic, boolean camera, OfficeStatus status) implements CustomPacketPayload {
	public static final Type<StatePayload> TYPE = new Type<>(CraftOffice.id("state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, StatePayload::active,
			ByteBufCodecs.BOOL, StatePayload::mic,
			ByteBufCodecs.BOOL, StatePayload::camera,
			ByteBufCodecs.VAR_INT.map(OfficeStatus::byId, OfficeStatus::ordinal), StatePayload::status,
			StatePayload::new);

	@Override
	public Type<StatePayload> type() {
		return TYPE;
	}
}
