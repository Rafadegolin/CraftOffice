package dev.rafadegolin.craftoffice.client.media.audio;

import dev.onvoid.webrtc.media.audio.AudioTrackSink;

/**
 * Áudio de um vizinho: recebe da faixa remota, guarda num buffer circular e
 * entrega ao mixer com o ganho da distância. Os relógios da WebRTC e da placa
 * de som não batem, então o buffer descarta o excesso e completa com silêncio.
 */
public final class PeerAudio implements AudioTrackSink {
	/** Começa a tocar com 40 ms guardados, para absorver a variação de chegada. */
	private static final int PREBUFFER = MonoConverter.FRAME * 4;
	/** Acima de 120 ms guardados, descarta o mais velho para a latência não crescer. */
	private static final int MAX_BUFFERED = MonoConverter.FRAME * 12;
	/** Pico acima disso, antes do ganho, conta como fala. */
	private static final int SPEAKING_THRESHOLD = 1200;

	private final short[] ring = new short[MonoConverter.FRAME * 32];
	private int readPos;
	private int size;
	private boolean playing;

	private final MonoConverter converter = new MonoConverter();

	/** Ganho pedido pela distância, de 0 a 1. Escrito pela thread do jogo. */
	private volatile float targetGain;
	/** Ganho aplicado, que persegue o pedido aos poucos para não estalar. */
	private float gain;

	private volatile int peak;
	private volatile long lastLoudNanos;

	@Override
	public void onData(byte[] data, int bitsPerSample, int sampleRate, int channels, int frames) {
		if (bitsPerSample != 16 || channels < 1) {
			return;
		}
		int[] blockPeak = {0};
		synchronized (this) {
			converter.convert(data, frames, channels, sampleRate, sample -> {
				blockPeak[0] = Math.max(blockPeak[0], Math.abs(sample));
				write(sample);
			});
			while (size > MAX_BUFFERED) {
				readPos = (readPos + 1) % ring.length;
				size--;
			}
		}
		peak = blockPeak[0];
		if (blockPeak[0] > SPEAKING_THRESHOLD) {
			lastLoudNanos = System.nanoTime();
		}
	}

	private void write(short sample) {
		if (size == ring.length) {
			readPos = (readPos + 1) % ring.length;
			size--;
		}
		ring[(readPos + size) % ring.length] = sample;
		size++;
	}

	/** Soma {@code count} amostras com ganho em {@code mix}. Thread do áudio. */
	synchronized void mixInto(int[] mix, int count) {
		if (!playing) {
			if (size < PREBUFFER) {
				return;
			}
			playing = true;
		}

		float target = targetGain;
		for (int i = 0; i < count; i++) {
			// Rampa de uns 20 ms para mudanças de volume não estalarem.
			gain += (target - gain) * 0.002f;
			if (size == 0) {
				playing = false;
				return;
			}
			short sample = ring[readPos];
			readPos = (readPos + 1) % ring.length;
			size--;
			mix[i] += Math.round(sample * gain);
		}
	}

	public void setGain(float value) {
		targetGain = Math.clamp(value, 0f, 1f);
	}

	public float gain() {
		return targetGain;
	}

	public int peak() {
		return peak;
	}

	/** Falou alto nos últimos 300 ms. */
	public boolean speaking() {
		return System.nanoTime() - lastLoudNanos < 300_000_000L;
	}

	public synchronized int bufferedMillis() {
		return size * 1000 / MonoConverter.RATE;
	}
}
