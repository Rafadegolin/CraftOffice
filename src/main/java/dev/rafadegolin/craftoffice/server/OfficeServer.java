package dev.rafadegolin.craftoffice.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;

import dev.rafadegolin.craftoffice.CraftOffice;
import dev.rafadegolin.craftoffice.config.ServerConfig;
import dev.rafadegolin.craftoffice.net.ConfigPayload;
import dev.rafadegolin.craftoffice.net.HelloPayload;
import dev.rafadegolin.craftoffice.net.SignalPayload;

/**
 * Lado servidor: sabe quem tem o mod, entrega a configuração e repassa a
 * negociação entre players. A regra de vizinhança entra na Fase 2.
 */
public final class OfficeServer {
	/** Avisos {@code signal} por player a cada segundo. Uma negociação usa uns 20. */
	private static final int MAX_SIGNALS_PER_SECOND = 40;

	private static ServerConfig config = new ServerConfig();
	private static final Map<UUID, Integer> protocols = new ConcurrentHashMap<>();
	private static final Map<UUID, RateWindow> signalRates = new ConcurrentHashMap<>();

	private OfficeServer() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> config = ServerConfig.load());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			protocols.clear();
			signalRates.clear();
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			protocols.remove(handler.player.getUUID());
			signalRates.remove(handler.player.getUUID());
		});

		ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (payload.protocol() != CraftOffice.PROTOCOL) {
				CraftOffice.LOGGER.info("{} usa o protocolo {} e o servidor o {}. Mod desligado para ele",
						player.getScoreboardName(), payload.protocol(), CraftOffice.PROTOCOL);
				return;
			}
			protocols.put(player.getUUID(), payload.protocol());
			ServerPlayNetworking.send(player, config.toPayload());
		});

		ServerPlayNetworking.registerGlobalReceiver(SignalPayload.TYPE, (payload, context) -> {
			ServerPlayer from = context.player();
			if (!enabled(from) || !allowSignal(from.getUUID())) {
				return;
			}

			ServerPlayer to = context.server().getPlayerList().getPlayer(payload.peer());
			if (to == null || to == from || !enabled(to)) {
				return;
			}

			ServerPlayNetworking.send(to, new SignalPayload(from.getUUID(), payload.kind(), payload.data()));
		});
	}

	public static boolean enabled(ServerPlayer player) {
		return protocols.containsKey(player.getUUID());
	}

	private static boolean allowSignal(UUID player) {
		boolean allowed = signalRates.computeIfAbsent(player, id -> new RateWindow()).tryAcquire(MAX_SIGNALS_PER_SECOND);
		if (!allowed) {
			CraftOffice.LOGGER.debug("Sinalização acima do limite para {}", player);
		}
		return allowed;
	}

	private static final class RateWindow {
		private long windowStart;
		private int count;

		synchronized boolean tryAcquire(int max) {
			long now = System.currentTimeMillis();
			if (now - windowStart >= 1000) {
				windowStart = now;
				count = 0;
			}
			return ++count <= max;
		}
	}
}
