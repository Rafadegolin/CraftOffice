package dev.rafadegolin.craftoffice.client.ui;

import java.net.URI;
import java.util.stream.Collectors;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.rafadegolin.craftoffice.client.OfficeSession;
import dev.rafadegolin.craftoffice.net.ConfigPayload;

/**
 * Aparece uma vez por servidor. Diz para onde a mídia vai, inclusive os
 * servidores STUN e TURN, como pede a regra 1.11 do Modrinth.
 */
public final class ConsentScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;

	private final ConfigPayload config;

	public ConsentScreen(ConfigPayload config) {
		super(Component.translatable("craftoffice.consent.title"));
		this.config = config;
	}

	@Override
	protected void init() {
		int textWidth = Math.min(340, width - 40);
		MultiLineTextWidget text = new MultiLineTextWidget(body(), font).setMaxWidth(textWidth);
		text.setX((width - text.getWidth()) / 2);
		text.setY(40);
		addRenderableWidget(text);

		int buttonsY = Math.min(height - 28, 40 + text.getHeight() + 16);
		addRenderableWidget(Button.builder(Component.translatable("craftoffice.consent.accept"), button -> {
			OfficeSession.setConsent(true);
			onClose();
		}).bounds(width / 2 - 154, buttonsY, 150, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("craftoffice.consent.decline"), button -> {
			OfficeSession.setConsent(false);
			onClose();
		}).bounds(width / 2 + 4, buttonsY, 150, 20).build());
	}

	private Component body() {
		String hosts = config == null || config.iceServers().isEmpty()
				? Component.translatable("craftoffice.consent.no_ice").getString()
				: config.iceServers().stream().map(server -> host(server.url())).distinct().collect(Collectors.joining(", "));
		return Component.translatable("craftoffice.consent.body",
				Keys.CAMERA.getTranslatedKeyMessage(), Keys.MIC.getTranslatedKeyMessage(),
				hosts, Keys.PANIC.getTranslatedKeyMessage());
	}

	/** {@code stun:stun.l.google.com:19302} vira {@code stun.l.google.com}. */
	private static String host(String url) {
		try {
			String rest = url.substring(url.indexOf(':') + 1);
			String host = URI.create("x://" + rest).getHost();
			return host != null ? host : url;
		}
		catch (RuntimeException e) {
			return url;
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 20, WHITE);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
