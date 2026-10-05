package dev.rafadegolin.craftoffice.net;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.rafadegolin.craftoffice.CraftOffice;

/** O aviso {@code zone_update} do estudo: todas as zonas, com quais estão trancadas. */
public record ZonesPayload(List<ZoneInfo> zones) implements CustomPacketPayload {
	public static final Type<ZonesPayload> TYPE = new Type<>(CraftOffice.id("zones"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ZonesPayload> CODEC = StreamCodec.composite(
			ZoneInfo.CODEC.apply(ByteBufCodecs.list(256)), ZonesPayload::zones,
			ZonesPayload::new);

	@Override
	public Type<ZonesPayload> type() {
		return TYPE;
	}

	public record ZoneInfo(String name, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
			boolean locked) {
		private static final StreamCodec<ByteBuf, int[]> BOX = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(6))
				.map(list -> list.stream().mapToInt(Integer::intValue).toArray(),
						array -> java.util.Arrays.stream(array).boxed().toList());

		public static final StreamCodec<ByteBuf, ZoneInfo> CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(64), ZoneInfo::name,
				ByteBufCodecs.stringUtf8(128), ZoneInfo::dimension,
				BOX, info -> new int[] {info.minX, info.minY, info.minZ, info.maxX, info.maxY, info.maxZ},
				ByteBufCodecs.BOOL, ZoneInfo::locked,
				(name, dimension, box, locked) -> new ZoneInfo(name, dimension, box[0], box[1], box[2], box[3], box[4], box[5], locked));

		public boolean contains(String dimension, double x, double y, double z) {
			return this.dimension.equals(dimension)
					&& x >= minX && x < maxX + 1
					&& y >= minY && y < maxY + 1
					&& z >= minZ && z < maxZ + 1;
		}
	}
}
