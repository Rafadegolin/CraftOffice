package dev.rafadegolin.craftoffice.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.rafadegolin.craftoffice.CraftOffice;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.media.PeerSession;

/**
 * Uma textura por pessoa: a própria câmera e cada vizinho. Criadas e
 * destruídas junto com as sessões. A HUD e o mundo desenham a partir daqui.
 * Thread do jogo.
 */
public final class VideoTextures {
	private static VideoTexture self;
	private static final Map<UUID, VideoTexture> remotes = new HashMap<>();

	private VideoTextures() {
	}

	/** Envia os frames novos. Pode ser chamado mais de uma vez por quadro: sem frame novo, não faz nada. */
	public static void update() {
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

	/** A própria câmera, ou {@code null} se nunca foi ligada. */
	public static VideoTexture self() {
		return self;
	}

	public static VideoTexture remote(UUID peer) {
		return remotes.get(peer);
	}

	public static void clear() {
		if (self != null) {
			self.close();
			self = null;
		}
		remotes.values().forEach(VideoTexture::close);
		remotes.clear();
	}
}
