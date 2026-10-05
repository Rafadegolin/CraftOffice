package dev.rafadegolin.craftoffice.client.media.audio;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.onvoid.webrtc.media.audio.AudioProcessing;
import dev.onvoid.webrtc.media.audio.AudioProcessingStreamConfig;
import dev.onvoid.webrtc.media.audio.AudioSource;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Mistura o áudio dos vizinhos, cada um com o ganho da sua distância, e
 * entrega à placa de som. O mesmo mix vai para o cancelamento de eco como
 * referência do que está tocando, já com os ganhos aplicados.
 */
public final class AudioMixer implements AudioSource {
	private static final AudioProcessingStreamConfig MONO_48K = new AudioProcessingStreamConfig(MonoConverter.RATE, 1);

	private final AudioProcessing processing;
	private final Map<UUID, PeerAudio> peers = new ConcurrentHashMap<>();

	/** Volume de "Voz" das opções de som do jogo, já com o volume geral. */
	private volatile float master = 1f;

	private int[] mix = new int[0];
	private short[] mono = new short[0];
	private final byte[] reverseFrame = new byte[MonoConverter.FRAME * 2];
	private final byte[] reverseOut = new byte[MonoConverter.FRAME * 2];
	private int reverseFill;
	private boolean warnedRate;

	public AudioMixer(AudioProcessing processing) {
		this.processing = processing;
	}

	public PeerAudio peer(UUID id) {
		return peers.computeIfAbsent(id, key -> new PeerAudio());
	}

	public PeerAudio peerIfPresent(UUID id) {
		return peers.get(id);
	}

	public void remove(UUID id) {
		peers.remove(id);
	}

	public void clear() {
		peers.clear();
	}

	public void setMaster(float value) {
		master = Math.clamp(value, 0f, 1f);
	}

	@Override
	public int onPlaybackData(byte[] audioSamples, int nSamples, int nBytesPerSample, int nChannels, int samplesPerSec) {
		if (samplesPerSec != MonoConverter.RATE && !warnedRate) {
			warnedRate = true;
			CraftOffice.LOGGER.info("Saída de som a {} Hz, convertendo de 48 kHz", samplesPerSec);
		}

		// Quantas amostras internas, a 48 kHz, cobrem este pedido da placa.
		int needed = (int) Math.ceil((double) nSamples * MonoConverter.RATE / samplesPerSec);
		if (mix.length < needed) {
			mix = new int[needed];
			mono = new short[needed];
		}
		java.util.Arrays.fill(mix, 0, needed, 0);
		for (PeerAudio peer : peers.values()) {
			peer.mixInto(mix, needed);
		}

		float volume = master;
		for (int i = 0; i < needed; i++) {
			mono[i] = (short) Math.clamp(Math.round(mix[i] * volume), Short.MIN_VALUE, Short.MAX_VALUE);
			feedReverse(mono[i]);
		}

		// Espalha o mono por todos os canais, reamostrando se a placa não roda a 48 kHz.
		int bytesPerFrame = nBytesPerSample > 0 ? nBytesPerSample : nChannels * 2;
		for (int frame = 0; frame < nSamples; frame++) {
			double position = (double) frame * MonoConverter.RATE / samplesPerSec;
			int index = Math.min((int) position, needed - 1);
			int next = Math.min(index + 1, needed - 1);
			short value = (short) Math.round(mono[index] + (mono[next] - mono[index]) * (position - (int) position));
			for (int c = 0; c < nChannels; c++) {
				int offset = frame * bytesPerFrame + c * 2;
				audioSamples[offset] = (byte) value;
				audioSamples[offset + 1] = (byte) (value >> 8);
			}
		}
		return nSamples;
	}

	/** Junta blocos de 10 ms do que está tocando para o cancelamento de eco. */
	private void feedReverse(short sample) {
		reverseFrame[reverseFill * 2] = (byte) sample;
		reverseFrame[reverseFill * 2 + 1] = (byte) (sample >> 8);
		if (++reverseFill == MonoConverter.FRAME) {
			reverseFill = 0;
			processing.processReverseStream(reverseFrame, MONO_48K, MONO_48K, reverseOut);
		}
	}
}
