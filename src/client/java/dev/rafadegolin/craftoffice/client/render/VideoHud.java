package dev.rafadegolin.craftoffice.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;

import dev.rafadegolin.craftoffice.CraftOffice;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.media.PeerSession;

/**
 * HUD da Fase 0: a própria câmera e o vídeo de cada sessão, com os números
 * que a prova de conceito precisa medir.
 */
public final class VideoHud {
	private static final int TILE_W = 160;
	private static final int TILE_H = 120;
	private static final int WHITE = 0xFFFFFFFF;

	private VideoTexture self;
	private final Map<UUID, VideoTexture> remotes = new HashMap<>();

	/** Envia frames novos para as texturas. Chamado no fim de cada tick do cliente e antes de desenhar. */
	public void upload() {
		MediaEngine engine = MediaEngine.getIfLoaded();
		if (engine == null) {
			return;
		}

		if (engine.videoOn()) {
			if (self == null) {
				self = new VideoTexture(CraftOffice.id("video/self"));
			}
			self.update(engine.selfSlot());
		}

		remotes.entrySet().removeIf(entry -> {
			if (!engine.sessions().containsKey(entry.getKey())) {
				entry.getValue().close();
				return true;
			}
			return false;
		});
		for (PeerSession session : engine.sessions().values()) {
			VideoTexture texture = remotes.computeIfAbsent(session.peer(),
					id -> new VideoTexture(CraftOffice.id("video/" + id)));
			texture.update(session.remoteSlot());
		}
	}

	public void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		MediaEngine engine = MediaEngine.getIfLoaded();
		if (engine == null) {
			return;
		}
		upload();

		Minecraft mc = Minecraft.getInstance();
		int x = graphics.guiWidth() - TILE_W - 8;
		int y = 8;

		if (engine.loadError() != null) {
			graphics.text(mc.font, "webrtc-java falhou: " + engine.loadError(), 8, 8, 0xFFFF5555);
			return;
		}

		if (engine.videoOn() && self != null) {
			drawTile(graphics, self, x, y);
			long[] upload = self.uploadMicros();
			graphics.text(mc.font, String.format("eu  %.0f fps  %s", engine.selfSlot().fps(), engine.selfSlot().sourceSize()), x, y + TILE_H + 2, WHITE);
			graphics.text(mc.font, "upload " + upload[0] + " us  p95 " + upload[1] + " us", x, y + TILE_H + 12, WHITE);
			graphics.text(mc.font, "jogo " + mc.getFps() + " fps", x, y + TILE_H + 22, WHITE);
			y += TILE_H + 36;
		}

		for (PeerSession session : engine.sessions().values()) {
			VideoTexture texture = remotes.get(session.peer());
			if (texture != null) {
				drawTile(graphics, texture, x, y);
			}
			PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(session.peer()) : null;
			String name = info != null ? info.getProfile().name() : session.peer().toString().substring(0, 8);
			graphics.text(mc.font, String.format("%s  %s  %.0f fps", name, session.state(), session.remoteSlot().fps()), x, y + TILE_H + 2, WHITE);
			graphics.text(mc.font, String.format("áudio %.0f/s  pico %d", session.remoteAudioCallbacksPerSecond(), session.remoteAudioPeak()), x, y + TILE_H + 12, WHITE);
			y += TILE_H + 26;
		}
	}

	private static void drawTile(GuiGraphicsExtractor graphics, VideoTexture texture, int x, int y) {
		graphics.fill(x - 1, y - 1, x + TILE_W + 1, y + TILE_H + 1, 0xFF000000);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x, y, 0, 0, TILE_W, TILE_H,
				320, 240, 320, 240);
	}

	public void clear() {
		if (self != null) {
			self.close();
			self = null;
		}
		remotes.values().forEach(VideoTexture::close);
		remotes.clear();
	}
}
