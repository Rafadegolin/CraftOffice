package dev.rafadegolin.craftoffice.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import dev.rafadegolin.craftoffice.client.OfficeSession;
import dev.rafadegolin.craftoffice.client.media.FrameSlot;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.media.PeerSession;
import dev.rafadegolin.craftoffice.client.media.audio.PeerAudio;

/**
 * Grade de vídeos no canto superior direito: a própria câmera e cada vizinho
 * com câmera ligada. Nome embaixo e borda verde em quem está falando.
 */
public final class VideoHud {
	private static final int TILE_W = 96;
	private static final int TILE_H = TILE_W * FrameSlot.HEIGHT / FrameSlot.WIDTH;
	private static final int GAP = 4;
	private static final int LABEL_H = 10;
	private static final int BORDER = 0xFF000000;
	private static final int SPEAKING = 0xFF4CAF50;
	private static final int TEXT = 0xFFFFFFFF;

	private record Tile(VideoTexture texture, String name, boolean speaking) {
	}

	public void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		MediaEngine engine = MediaEngine.getIfLoaded();
		if (engine == null || !OfficeSession.active()) {
			return;
		}
		VideoTextures.update();

		Minecraft mc = Minecraft.getInstance();
		List<Tile> tiles = new ArrayList<>();
		VideoTexture self = VideoTextures.self();
		if (engine.videoOn() && self != null) {
			boolean speaking = engine.mic() != null && engine.mic().speaking();
			tiles.add(new Tile(self, Component.translatable("craftoffice.hud.you").getString(), speaking));
		}
		for (PeerSession session : engine.sessions().values()) {
			VideoTexture texture = VideoTextures.remote(session.peer());
			if (texture == null || !VideoBillboards.showsVideo(session)) {
				continue;
			}
			PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(session.peer()) : null;
			String name = info != null ? info.getProfile().name() : session.peer().toString().substring(0, 8);
			PeerAudio audio = engine.mixer() != null ? engine.mixer().peerIfPresent(session.peer()) : null;
			tiles.add(new Tile(texture, name, audio != null && audio.speaking()));
		}

		// Uma coluna à direita; se não couber na altura, abre outra à esquerda.
		int perColumn = Math.max(1, (graphics.guiHeight() - GAP) / (TILE_H + LABEL_H + GAP));
		for (int i = 0; i < tiles.size(); i++) {
			int column = i / perColumn;
			int row = i % perColumn;
			int x = graphics.guiWidth() - (column + 1) * (TILE_W + GAP);
			int y = GAP + row * (TILE_H + LABEL_H + GAP);
			drawTile(graphics, mc, tiles.get(i), x, y);
		}
	}

	private static void drawTile(GuiGraphicsExtractor graphics, Minecraft mc, Tile tile, int x, int y) {
		int border = tile.speaking() ? SPEAKING : BORDER;
		int thickness = tile.speaking() ? 2 : 1;
		graphics.fill(x - thickness, y - thickness, x + TILE_W + thickness, y + TILE_H + thickness, border);
		graphics.blit(RenderPipelines.GUI_TEXTURED, tile.texture().id(), x, y, 0, 0, TILE_W, TILE_H,
				FrameSlot.WIDTH, FrameSlot.HEIGHT, FrameSlot.WIDTH, FrameSlot.HEIGHT);
		String name = mc.font.plainSubstrByWidth(tile.name(), TILE_W);
		graphics.text(mc.font, name, x + (TILE_W - mc.font.width(name)) / 2, y + TILE_H + 2, TEXT);
	}

	public void clear() {
		VideoTextures.clear();
	}
}
