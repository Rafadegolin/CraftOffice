package dev.rafadegolin.craftoffice.client.ui;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;

import dev.rafadegolin.craftoffice.CraftOffice;

/** Teclas do mod, todas remapeáveis em Controles. */
public final class Keys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CraftOffice.id("main"));

	public static final KeyMapping MIC = key("mic", InputConstants.KEY_B);
	public static final KeyMapping CAMERA = key("camera", InputConstants.KEY_V);
	public static final KeyMapping SCREEN = key("screen", InputConstants.KEY_J);
	public static final KeyMapping PANEL = key("panel", InputConstants.KEY_O);
	/** Pânico: corta toda a captura. Sem tecla padrão, para ninguém apertar sem querer. */
	public static final KeyMapping PANIC = key("panic", InputConstants.UNKNOWN.getValue());

	private Keys() {
	}

	private static KeyMapping key(String name, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftoffice." + name, defaultKey, CATEGORY));
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (MIC.consumeClick()) {
				OfficeActions.toggleMic();
			}
			while (CAMERA.consumeClick()) {
				OfficeActions.toggleCamera();
			}
			while (SCREEN.consumeClick()) {
				OfficeActions.toggleScreenShare();
			}
			while (PANEL.consumeClick()) {
				OfficeActions.openPanel();
			}
			while (PANIC.consumeClick()) {
				OfficeActions.stopAll();
			}
		});
	}
}
