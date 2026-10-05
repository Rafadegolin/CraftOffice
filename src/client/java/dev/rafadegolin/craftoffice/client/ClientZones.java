package dev.rafadegolin.craftoffice.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import dev.rafadegolin.craftoffice.net.ZonesPayload;
import dev.rafadegolin.craftoffice.net.ZonesPayload.ZoneInfo;

/** Zonas recebidas do servidor e a zona atual do player, com aviso ao entrar e sair. Thread do jogo. */
public final class ClientZones {
	private static List<ZoneInfo> zones = List.of();
	private static ZoneInfo current;

	private ClientZones() {
	}

	public static void onZones(ZonesPayload payload) {
		zones = List.copyOf(payload.zones());
	}

	public static void reset() {
		zones = List.of();
		current = null;
	}

	public static List<ZoneInfo> zones() {
		return zones;
	}

	public static ZoneInfo current() {
		return current;
	}

	public static String dimension(Minecraft mc) {
		return mc.level.dimension().toString();
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		String dimension = dimension(mc);
		ZoneInfo found = null;
		for (ZoneInfo zone : zones) {
			if (zone.contains(dimension, mc.player.getX(), mc.player.getY(), mc.player.getZ())) {
				found = zone;
				break;
			}
		}

		String before = current != null ? current.name() : null;
		String after = found != null ? found.name() : null;
		current = found;
		if (before == null ? after != null : !before.equals(after)) {
			Component message = after != null
					? Component.translatable("craftoffice.zone.entered", after)
					: Component.translatable("craftoffice.zone.left", before);
			mc.player.sendOverlayMessage(message);
		}
	}
}
