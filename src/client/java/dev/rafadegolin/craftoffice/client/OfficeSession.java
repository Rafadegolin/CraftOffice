package dev.rafadegolin.craftoffice.client;

import net.minecraft.client.Minecraft;

import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.ui.ConsentScreen;
import dev.rafadegolin.craftoffice.net.ConfigPayload;

/**
 * Estado do CraftOffice no servidor atual. O mod só funciona quando o servidor
 * mandou a configuração e o player aceitou o consentimento. Thread do jogo.
 */
public final class OfficeSession {
	private static ConfigPayload config;
	private static String serverKey;
	private static boolean consent;
	private static boolean consentScreenPending;

	private OfficeSession() {
	}

	/** Ao entrar num servidor: tudo desligado até chegar o {@code config}. */
	public static void reset() {
		config = null;
		serverKey = null;
		consent = false;
		consentScreenPending = false;
	}

	public static void onConfig(ConfigPayload payload) {
		config = payload;
		serverKey = ConsentStore.currentServerKey();
		consent = ConsentStore.hasConsent(serverKey);
		consentScreenPending = !consent;

		MediaEngine engine = MediaEngine.get();
		engine.run(() -> engine.setIceServers(payload.iceServers()));
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
			ConsentStore.setConsent(serverKey, accepted);
		}
		if (!accepted) {
			MediaEngine engine = MediaEngine.getIfLoaded();
			if (engine != null) {
				engine.run(engine::reset);
				engine.run(() -> engine.setIceServers(config != null ? config.iceServers() : java.util.List.of()));
			}
		}
	}

	/** O servidor tem o mod e respondeu ao {@code hello}. */
	public static boolean serverHasMod() {
		return config != null;
	}

	public static boolean hasConsent() {
		return consent;
	}

	/** Pode ligar câmera, microfone e fazer chamadas. */
	public static boolean active() {
		return config != null && consent;
	}

	public static ConfigPayload config() {
		return config;
	}
}
