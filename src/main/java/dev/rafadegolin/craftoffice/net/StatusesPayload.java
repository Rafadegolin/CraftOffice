package dev.rafadegolin.craftoffice.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.rafadegolin.craftoffice.CraftOffice;
import dev.rafadegolin.craftoffice.OfficeStatus;

/** Status de todos os players com o mod, para o aviso acima do nome. Só quem não está disponível. */
public record StatusesPayload(Map<UUID, OfficeStatus> statuses) implements CustomPacketPayload {
	public static final Type<StatusesPayload> TYPE = new Type<>(CraftOffice.id("statuses"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StatusesPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC,
					ByteBufCodecs.VAR_INT.map(OfficeStatus::byId, OfficeStatus::ordinal), 1024),
			StatusesPayload::statuses,
			StatusesPayload::new);

	@Override
	public Type<StatusesPayload> type() {
		return TYPE;
	}
}
