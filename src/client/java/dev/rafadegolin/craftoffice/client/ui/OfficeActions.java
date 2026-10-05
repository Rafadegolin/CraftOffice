package dev.rafadegolin.craftoffice.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import dev.rafadegolin.craftoffice.client.OfficeSession;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;

/** Ações das teclas e do painel, com as checagens de servidor e consentimento. Thread do jogo. */
public final class OfficeActions {
	private OfficeActions() {
	}

	public static void toggleMic() {
		MediaEngine engine = checkActive();
		if (engine != null) {
			boolean on = !engine.micOn();
			engine.run(() -> engine.setMic(on));
			OfficeSession.sendState(on, engine.videoOn());
			actionBar(on ? "craftoffice.status.mic_on" : "craftoffice.status.mic_off");
		}
	}

	public static void toggleCamera() {
		MediaEngine engine = checkActive();
		if (engine != null) {
			boolean on = !engine.videoOn();
			engine.run(() -> engine.setVideo(on));
			OfficeSession.sendState(engine.micOn(), on);
			actionBar(on ? "craftoffice.status.camera_on" : "craftoffice.status.camera_off");
		}
	}

	public static void toggleScreenShare() {
		if (checkActive() != null) {
			actionBar("craftoffice.status.screen_soon");
		}
	}

	/** Tecla de pânico do estudo: corta câmera e microfone na hora. */
	public static void stopAll() {
		MediaEngine engine = MediaEngine.getIfLoaded();
		if (engine != null) {
			engine.run(() -> {
				engine.setVideo(false);
				engine.setMic(false);
			});
			OfficeSession.sendState(false, false);
		}
	}

	public static void openPanel() {
		Minecraft.getInstance().gui.setScreen(new PanelScreen());
	}

	/** O motor de mídia, se dá para usar o mod agora. Senão, avisa o player e devolve {@code null}. */
	private static MediaEngine checkActive() {
		if (!OfficeSession.serverHasMod()) {
			actionBar("craftoffice.status.no_server_mod");
			return null;
		}
		if (!OfficeSession.hasConsent()) {
			actionBar("craftoffice.status.no_consent");
			return null;
		}
		MediaEngine engine = MediaEngine.get();
		if (!engine.ready()) {
			actionBar("craftoffice.status.media_failed");
			return null;
		}
		return engine;
	}

	private static void actionBar(String key) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.translatable(key));
		}
	}
}
