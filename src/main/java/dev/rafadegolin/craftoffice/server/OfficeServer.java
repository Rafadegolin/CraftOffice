package dev.rafadegolin.craftoffice.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import dev.rafadegolin.craftoffice.CraftOffice;
import dev.rafadegolin.craftoffice.config.ServerConfig;
import dev.rafadegolin.craftoffice.net.HelloPayload;
import dev.rafadegolin.craftoffice.net.PeerPayload;
import dev.rafadegolin.craftoffice.net.PeerStatePayload;
import dev.rafadegolin.craftoffice.net.SignalPayload;
import dev.rafadegolin.craftoffice.net.StatePayload;
import dev.rafadegolin.craftoffice.proximity.ProximityEngine;
import dev.rafadegolin.craftoffice.zone.Zone;
import dev.rafadegolin.craftoffice.zone.ZoneCommands;
import dev.rafadegolin.craftoffice.zone.ZoneManager;

/**
 * Lado servidor: sabe quem tem o mod, entrega a configuração, roda o motor de
 * proximidade e repassa a negociação só entre vizinhos. Sem o repasse a
 * conexão não existe, então um cliente adulterado não escuta quem está longe.
 */
public final class OfficeServer {
	/** Avisos {@code signal} por player a cada segundo. Uma negociação usa uns 20. */
	private static final int MAX_SIGNALS_PER_SECOND = 40;
	/** O estudo pede a cada 5 ticks. */
	private static final int TICK_INTERVAL = 5;
	private static final long ENTER_DELAY_MS = 500;
	private static final long EXIT_DELAY_MS = 1000;

	private static ServerConfig config = new ServerConfig();
	private static ProximityEngine engine = newEngine(config);
	private static int ticks;
	private static ZoneManager zones;

	private static final Map<UUID, Integer> protocols = new ConcurrentHashMap<>();
	private static final Map<UUID, StatePayload> states = new ConcurrentHashMap<>();
	private static final Map<UUID, RateWindow> signalRates = new ConcurrentHashMap<>();

	private OfficeServer() {
	}

	private static ProximityEngine newEngine(ServerConfig config) {
		return new ProximityEngine(new ProximityEngine.Settings(
				config.connectRadius, config.disconnectRadius, ENTER_DELAY_MS, EXIT_DELAY_MS, config.maxVideos));
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			config = ServerConfig.load();
			engine = newEngine(config);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> zones = new ZoneManager(server));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			protocols.clear();
			states.clear();
			signalRates.clear();
			engine.clear();
			zones = null;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				ZoneCommands.register(dispatcher, () -> zones));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++ticks >= TICK_INTERVAL) {
				ticks = 0;
				updateProximity(server);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			protocols.remove(id);
			states.remove(id);
			signalRates.remove(id);
			if (zones != null) {
				zones.forget(id);
			}
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
			if (zones != null) {
				ServerPlayNetworking.send(player, zones.payload());
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!enabled(player)) {
				return;
			}
			states.put(player.getUUID(), payload);
			// Os vizinhos veem na hora quem ligou câmera ou microfone.
			for (UUID neighbor : engine.neighbors(player.getUUID())) {
				ServerPlayer target = context.server().getPlayerList().getPlayer(neighbor);
				if (target != null && enabled(target)) {
					ServerPlayNetworking.send(target, peerState(player.getUUID()));
				}
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(SignalPayload.TYPE, (payload, context) -> {
			ServerPlayer from = context.player();
			if (!enabled(from) || !allowSignal(from.getUUID())) {
				return;
			}

			ServerPlayer to = context.server().getPlayerList().getPlayer(payload.peer());
			if (to == null || to == from || !enabled(to) || !engine.areNeighbors(from.getUUID(), to.getUUID())) {
				return;
			}

			ServerPlayNetworking.send(to, new SignalPayload(from.getUUID(), payload.kind(), payload.data()));
		});
	}

	private static void updateProximity(MinecraftServer server) {
		if (zones == null) {
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		zones.tick(players);
		if (zones.consumeDirty()) {
			for (ServerPlayer player : players) {
				if (enabled(player)) {
					ServerPlayNetworking.send(player, zones.payload());
				}
			}
		}

		List<ProximityEngine.Position> positions = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (participates(player)) {
				Zone zone = zones.zoneOf(player);
				positions.add(new ProximityEngine.Position(player.getUUID(), ZoneManager.dimensionOf(player),
						player.getX(), player.getY(), player.getZ(), zone != null ? zone.name().toLowerCase() : null, false, 1));
			}
		}

		for (ProximityEngine.Change change : engine.update(positions, System.currentTimeMillis())) {
			ServerPlayer target = server.getPlayerList().getPlayer(change.player());
			if (target != null && enabled(target)) {
				PeerPayload.Action action = PeerPayload.Action.valueOf(change.action().name());
				ServerPlayNetworking.send(target, new PeerPayload(action, change.peer(), change.initiator(), change.video(), change.fullVolume()));
				if (action == PeerPayload.Action.ADD) {
					ServerPlayNetworking.send(target, peerState(change.peer()));
				}
			}
		}
	}

	private static PeerStatePayload peerState(UUID player) {
		StatePayload state = states.get(player);
		return new PeerStatePayload(player, state != null && state.mic(), state != null && state.camera());
	}

	/** Tem o mod, aceitou o consentimento e está vivo. Espectadores ficam de fora. */
	private static boolean participates(ServerPlayer player) {
		StatePayload state = states.get(player.getUUID());
		return enabled(player) && state != null && state.active() && player.isAlive() && !player.isSpectator();
	}

	public static boolean enabled(ServerPlayer player) {
		return protocols.containsKey(player.getUUID());
	}

	public static Set<UUID> neighbors(UUID player) {
		return engine.neighbors(player);
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
