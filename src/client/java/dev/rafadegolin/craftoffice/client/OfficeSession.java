package dev.rafadegolin.craftoffice.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.media.PeerSession;
import dev.rafadegolin.craftoffice.client.ui.ConsentScreen;
import dev.rafadegolin.craftoffice.net.ConfigPayload;
import dev.rafadegolin.craftoffice.net.PeerPayload;
import dev.rafadegolin.craftoffice.net.StatePayload;

/**
 * Estado do CraftOffice no servidor atual. O mod só funciona quando o servidor
 * mandou a configuração e o player aceitou o consentimento. Quem conversa com
 * quem é o servidor que decide, pelos avisos {@code peer}. Thread do jogo.
 */
public final class OfficeSession {
	private static ConfigPayload config;
	private static String serverKey;
	private static boolean consent;
	private static boolean consentScreenPending;

	/** Vizinhos atuais segundo o servidor, com a permissão de vídeo de cada um. */
	private static final Map<UUID, Boolean> neighbors = new HashMap<>();

	private OfficeSession() {
	}

	/** Ao entrar ou sair de um servidor: tudo desligado até chegar o {@code config}. */
	public static void reset() {
		config = null;
		serverKey = null;
		consent = false;
		consentScreenPending = false;
		neighbors.clear();
	}

	public static void onConfig(ConfigPayload payload) {
		config = payload;
		serverKey = ClientSettings.currentServerKey();
		consent = ClientSettings.hasConsent(serverKey);
		consentScreenPending = !consent;

		MediaEngine engine = MediaEngine.get();
		engine.run(() -> engine.setIceServers(payload.iceServers()));
		sendState();
	}

	/** Abre a tela de consentimento assim que nenhuma outra tela estiver aberta. Chamado a cada tick. */
	public static void tick(Minecraft mc) {
		if (consentScreenPending && mc.gui.screen() == null && mc.player != null) {
			consentScreenPending = false;
			mc.gui.setScreen(new ConsentScreen(config));
		}
	}

	public static void setConsent(boolean accepted) {
		consent = accepted;
		if (serverKey != null) {
			ClientSettings.setConsent(serverKey, accepted);
		}
		if (!accepted) {
			neighbors.clear();
			MediaEngine engine = MediaEngine.getIfLoaded();
			if (engine != null) {
				List<ConfigPayload.IceServer> servers = config != null ? config.iceServers() : List.of();
				engine.run(() -> {
					engine.reset();
					engine.setIceServers(servers);
				});
			}
		}
		sendState();
	}

	/** Conta ao servidor se este player participa e o que está transmitindo. */
	public static void sendState(boolean mic, boolean camera) {
		if (config != null && ClientPlayNetworking.canSend(StatePayload.TYPE)) {
			ClientPlayNetworking.send(new StatePayload(consent, consent && mic, consent && camera));
		}
	}

	public static void sendState() {
		MediaEngine engine = MediaEngine.getIfLoaded();
		sendState(engine != null && engine.micOn(), engine != null && engine.videoOn());
	}

	/** Aviso {@code peer} do servidor: abre, fecha ou ajusta a conversa com um vizinho. */
	public static void onPeer(PeerPayload payload) {
		if (!active()) {
			return;
		}
		UUID peer = payload.peer();
		MediaEngine engine = MediaEngine.get();

		switch (payload.action()) {
			case ADD -> {
				neighbors.put(peer, payload.video());
				boolean video = payload.video();
				// Quem não inicia espera a oferta, que só é aceita de um vizinho.
				if (payload.initiator()) {
					engine.run(() -> {
						if (engine.ready()) {
							engine.session(peer, true, video);
						}
					});
				}
			}
			case REMOVE -> {
				neighbors.remove(peer);
				engine.run(() -> engine.closeSession(peer));
			}
			case UPDATE -> {
				neighbors.put(peer, payload.video());
				boolean video = payload.video();
				engine.run(() -> {
					PeerSession session = engine.sessions().get(peer);
					if (session != null) {
						session.setVideoAllowed(video);
					}
				});
			}
		}
	}

	public static boolean isNeighbor(UUID peer) {
		return neighbors.containsKey(peer);
	}

	public static boolean videoAllowed(UUID peer) {
		return neighbors.getOrDefault(peer, false);
	}

	public static Map<UUID, Boolean> neighbors() {
		return Map.copyOf(neighbors);
	}

	/** O servidor tem o mod e respondeu ao {@code hello}. */
	public static boolean serverHasMod() {
		return config != null;
	}

	public static boolean hasConsent() {
		return consent;
	}

	/** Pode ligar câmera, microfone e conversar. */
	public static boolean active() {
		return config != null && consent;
	}

	public static ConfigPayload config() {
		return config;
	}
}
