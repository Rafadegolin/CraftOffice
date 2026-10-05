package dev.rafadegolin.craftoffice.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.rafadegolin.craftoffice.client.OfficeSession;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;

/** Painel do mod: estado, microfone, câmera e consentimento. */
public final class PanelScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;

	private Button mic;
	private Button camera;
	private Button audio;

	public PanelScreen() {
		super(Component.translatable("craftoffice.panel.title"));
	}

	@Override
	protected void init() {
		int x = width / 2 - 100;
		int y = height / 2 - 52;

		mic = addRenderableWidget(Button.builder(Component.empty(), button -> OfficeActions.toggleMic())
				.bounds(x, y, 200, 20).build());
		camera = addRenderableWidget(Button.builder(Component.empty(), button -> OfficeActions.toggleCamera())
				.bounds(x, y + 24, 200, 20).build());
		audio = addRenderableWidget(Button.builder(Component.empty(), button -> OfficeActions.toggleAudio())
				.bounds(x, y + 48, 200, 20).build());

		boolean consent = OfficeSession.hasConsent();
		addRenderableWidget(Button.builder(Component.translatable(consent ? "craftoffice.panel.revoke" : "craftoffice.panel.consent"), button -> {
			if (OfficeSession.hasConsent()) {
				OfficeSession.setConsent(false);
				rebuildWidgets();
			}
			else {
				minecraft.gui.setScreen(new ConsentScreen(OfficeSession.config()));
			}
		}).bounds(x, y + 72, 200, 20).build()).active = OfficeSession.serverHasMod();

		addRenderableWidget(Button.builder(Component.translatable("craftoffice.panel.panic"), button -> OfficeActions.stopAll())
				.bounds(x, y + 96, 98, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(x + 102, y + 96, 98, 20).build());

		updateLabels();
	}

	@Override
	public void tick() {
		updateLabels();
	}

	/** As ações rodam na thread de mídia, então os rótulos acompanham o estado a cada tick. */
	private void updateLabels() {
		MediaEngine engine = MediaEngine.getIfLoaded();
		boolean active = OfficeSession.active() && engine != null && engine.ready();
		boolean micOn = engine != null && engine.micOn();
		boolean cameraOn = engine != null && engine.videoOn();

		mic.setMessage(Component.translatable(micOn ? "craftoffice.panel.mic_on" : "craftoffice.panel.mic_off"));
		camera.setMessage(Component.translatable(cameraOn ? "craftoffice.panel.camera_on" : "craftoffice.panel.camera_off"));
		boolean audioOn = engine != null && engine.audioEnabled();
		audio.setMessage(Component.translatable(audioOn ? "craftoffice.panel.audio_on" : "craftoffice.panel.audio_off"));
		audio.active = engine != null && engine.ready();
		mic.active = active && audioOn;
		camera.active = active;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, height / 2 - 88, WHITE);
		graphics.centeredText(font, statusLine(), width / 2, height / 2 - 72, GRAY);
	}

	private Component statusLine() {
		MediaEngine engine = MediaEngine.getIfLoaded();
		if (engine != null && engine.loadError() != null) {
			return Component.translatable("craftoffice.status.media_failed");
		}
		if (!OfficeSession.serverHasMod()) {
			return Component.translatable("craftoffice.status.no_server_mod");
		}
		if (!OfficeSession.hasConsent()) {
			return Component.translatable("craftoffice.status.no_consent");
		}
		int peers = engine != null ? engine.sessions().size() : 0;
		return Component.translatable("craftoffice.panel.status", peers);
	}
}
