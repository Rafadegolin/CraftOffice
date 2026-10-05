package dev.rafadegolin.craftoffice.client.media.audio;

/**
 * Converte PCM 16 bits de qualquer taxa e número de canais para o formato
 * interno: 48 kHz, mono. Reamostragem linear com estado, para não estalar
 * entre um bloco e o próximo. Voz não pede mais que isso.
 */
public final class MonoConverter {
	public static final int RATE = 48_000;
	/** Amostras em 10 ms no formato interno, o bloco que a WebRTC usa. */
	public static final int FRAME = RATE / 100;

	private double position;
	private short last;

	/**
	 * Converte {@code frames} quadros de {@code src} e entrega cada amostra
	 * resultante a {@code out}. Amostras em little-endian, como a webrtc-java usa.
	 */
	public void convert(byte[] src, int frames, int channels, int sampleRate, ShortConsumer out) {
		if (sampleRate == RATE) {
			for (int i = 0; i < frames; i++) {
				out.accept(downmix(src, i, channels));
			}
			return;
		}

		double step = (double) sampleRate / RATE;
		while (position < frames) {
			int index = (int) position;
			double fraction = position - index;
			short a = index == 0 ? last : downmix(src, index - 1, channels);
			short b = downmix(src, index, channels);
			out.accept((short) Math.round(a + (b - a) * fraction));
			position += step;
		}
		position -= frames;
		last = downmix(src, frames - 1, channels);
	}

	private static short downmix(byte[] src, int frame, int channels) {
		int sum = 0;
		for (int c = 0; c < channels; c++) {
			int offset = (frame * channels + c) * 2;
			sum += (short) ((src[offset] & 0xFF) | (src[offset + 1] << 8));
		}
		return (short) (sum / channels);
	}

	@FunctionalInterface
	public interface ShortConsumer {
		void accept(short sample);
	}
}
