package dev.rafadegolin.craftoffice.client.ui;

import java.util.UUID;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import dev.rafadegolin.craftoffice.client.OfficeSession;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.media.audio.PeerAudio;

/**
 * Indicadores fixos de microfone, câmera e tela no canto superior esquerdo.
 * Vermelho é transmitindo. Pela regra de privacidade do estudo, quem
 * transmite sempre vê o aviso.
 */
public final class StatusHud {
	private static final int ON = 0xCCD32F2F;
	private static final int OFF = 0x88303030;
	private static final int TEXT_ON = 0xFFFFFFFF;
	private static final int TEXT_OFF = 0xFF9E9E9E;
	private static final int SPEAKING = 0xFF4CAF50;

	private StatusHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!OfficeSession.serverHasMod()) {
			return;
		}

		int x = 4;
		int y = 4;
		if (!OfficeSession.hasConsent()) {
			graphics.text(mc.font, Component.translatable("craftoffice.hud.off", Keys.PANEL.getTranslatedKeyMessage()), x, y, TEXT_OFF);
			return;
		}

		MediaEngine engine = MediaEngine.getIfLoaded();
		boolean micOn = engine != null && engine.micOn();
		boolean cameraOn = engine != null && engine.videoOn();

		boolean selfSpeaking = engine != null && engine.mic() != null && engine.mic().speaking();
		int micX = x;
		x = badge(graphics, mc, Component.translatable("craftoffice.hud.mic"), micOn, x, y);
		if (selfSpeaking) {
			graphics.fill(micX, y + 12, x - 3, y + 13, SPEAKING);
		}
		x = badge(graphics, mc, Component.translatable("craftoffice.hud.camera"), cameraOn, x, y);
		badge(graphics, mc, Component.translatable("craftoffice.hud.screen"), false, x, y);

		if (engine != null && engine.mixer() != null) {
			neighbors(graphics, mc, engine, 4, y + 18);
		}
	}

	/** Vizinhos com o volume da distância. Ponto verde enquanto a pessoa fala. */
	private static void neighbors(GuiGraphicsExtractor graphics, Minecraft mc, MediaEngine engine, int x, int y) {
		for (UUID peer : OfficeSession.neighbors().keySet()) {
			PeerAudio audio = engine.mixer().peerIfPresent(peer);
			PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(peer) : null;
			String name = info != null ? info.getProfile().name() : peer.toString().substring(0, 8);
			boolean speaking = audio != null && audio.speaking();
			int volume = audio != null ? Math.round(audio.gain() * 100) : 0;

			graphics.fill(x, y + 2, x + 5, y + 7, speaking ? SPEAKING : OFF);
			graphics.text(mc.font, name + "  " + volume + "%", x + 8, y, speaking ? TEXT_ON : TEXT_OFF, false);
			y += 11;
		}
	}

	private static int badge(GuiGraphicsExtractor graphics, Minecraft mc, Component label, boolean on, int x, int y) {
		int w = mc.font.width(label) + 8;
		graphics.fill(x, y, x + w, y + 12, on ? ON : OFF);
		graphics.text(mc.font, label, x + 4, y + 2, on ? TEXT_ON : TEXT_OFF, false);
		return x + w + 3;
	}
}
