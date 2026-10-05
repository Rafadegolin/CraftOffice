package dev.rafadegolin.craftoffice.net;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Negociação da conexão entre dois players, repassada pelo servidor.
 * Indo para o servidor, {@code peer} é o destino. Voltando, é quem enviou.
 */
public record SignalPayload(UUID peer, String kind, String data) implements CustomPacketPayload {
	/** Abaixo do limite de 32 KiB dos pacotes enviados ao servidor. */
	public static final int MAX_DATA = 24_000;

	public static final Type<SignalPayload> TYPE = new Type<>(CraftOffice.id("signal"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SignalPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, SignalPayload::peer,
			ByteBufCodecs.stringUtf8(16), SignalPayload::kind,
			ByteBufCodecs.stringUtf8(MAX_DATA), SignalPayload::data,
			SignalPayload::new);

	@Override
	public Type<SignalPayload> type() {
		return TYPE;
	}
}
