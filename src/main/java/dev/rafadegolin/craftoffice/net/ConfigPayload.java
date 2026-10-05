package dev.rafadegolin.craftoffice.net;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Resposta do servidor ao {@code hello}: raios, limite de vídeos e por onde a
 * mídia pode passar. A lista de servidores ICE também vai para a tela de
 * consentimento, porque o player precisa saber para onde os dados vão.
 */
public record ConfigPayload(int protocol, double connectRadius, double disconnectRadius, int maxVideos,
		List<IceServer> iceServers) implements CustomPacketPayload {
	public static final Type<ConfigPayload> TYPE = new Type<>(CraftOffice.id("config"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ConfigPayload::protocol,
			ByteBufCodecs.DOUBLE, ConfigPayload::connectRadius,
			ByteBufCodecs.DOUBLE, ConfigPayload::disconnectRadius,
			ByteBufCodecs.VAR_INT, ConfigPayload::maxVideos,
			IceServer.CODEC.apply(ByteBufCodecs.list(16)), ConfigPayload::iceServers,
			ConfigPayload::new);

	@Override
	public Type<ConfigPayload> type() {
		return TYPE;
	}

	/** Servidor STUN ou TURN. Usuário e senha vazios para STUN. */
	public record IceServer(String url, String username, String credential) {
		public static final StreamCodec<ByteBuf, IceServer> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(256), IceServer::url,
				ByteBufCodecs.stringUtf8(256), IceServer::username,
				ByteBufCodecs.stringUtf8(256), IceServer::credential,
				IceServer::new);
	}
}
