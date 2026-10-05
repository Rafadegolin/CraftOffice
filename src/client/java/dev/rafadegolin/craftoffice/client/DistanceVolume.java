package dev.rafadegolin.craftoffice.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.media.audio.AudioMixer;
import dev.rafadegolin.craftoffice.client.media.audio.PeerAudio;

/**
 * Volume pela distância, como no Gather: cheio até {@link #FULL_VOLUME_RADIUS}
 * blocos, cai em linha reta até zero no raio de desconexão. Na mesma zona ou
 * com alguém no palco, volume cheio. Thread do jogo,
 * a cada tick.
 */
public final class DistanceVolume {
	public static final double FULL_VOLUME_RADIUS = 3;

	private DistanceVolume() {
	}

	public static void tick(Minecraft mc) {
		MediaEngine engine = MediaEngine.getIfLoaded();
		if (engine == null || !engine.ready() || mc.player == null || mc.level == null || !OfficeSession.active()) {
			return;
		}

		AudioMixer mixer = engine.mixer();
		mixer.setMaster(mc.options.getFinalSoundSourceVolume(SoundSource.VOICE));

		double silentAt = OfficeSession.config().disconnectRadius();
		for (UUID peer : engine.sessions().keySet()) {
			PeerAudio audio = mixer.peerIfPresent(peer);
			if (audio == null) {
				continue;
			}
			if (OfficeSession.fullVolume(peer)) {
				audio.setGain(1f);
				continue;
			}
			Player other = mc.level.getPlayerByUUID(peer);
			audio.setGain(other == null ? 0f : (float) gainFor(other.distanceTo(mc.player), silentAt));
		}
	}

	/** Ganho de 0 a 1 para uma distância. Exposto para testes e para a HUD. */
	public static double gainFor(double distance, double silentAt) {
		if (distance <= FULL_VOLUME_RADIUS) {
			return 1;
		}
		if (distance >= silentAt) {
			return 0;
		}
		return 1 - (distance - FULL_VOLUME_RADIUS) / (silentAt - FULL_VOLUME_RADIUS);
	}
}
