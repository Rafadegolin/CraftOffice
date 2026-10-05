package dev.rafadegolin.craftoffice.client.ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import dev.rafadegolin.craftoffice.client.OfficeSession;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;

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

		x = badge(graphics, mc, Component.translatable("craftoffice.hud.mic"), micOn, x, y);
		x = badge(graphics, mc, Component.translatable("craftoffice.hud.camera"), cameraOn, x, y);
		badge(graphics, mc, Component.translatable("craftoffice.hud.screen"), false, x, y);
	}

	private static int badge(GuiGraphicsExtractor graphics, Minecraft mc, Component label, boolean on, int x, int y) {
		int w = mc.font.width(label) + 8;
		graphics.fill(x, y, x + w, y + 12, on ? ON : OFF);
		graphics.text(mc.font, label, x + 4, y + 2, on ? TEXT_ON : TEXT_OFF, false);
		return x + w + 3;
	}
}
