package dev.rafadegolin.craftoffice.client.media.audio;

import dev.onvoid.webrtc.media.audio.AudioDevice;
import dev.onvoid.webrtc.media.audio.AudioProcessing;
import dev.onvoid.webrtc.media.audio.AudioProcessingStreamConfig;
import dev.onvoid.webrtc.media.audio.AudioRecorder;
import dev.onvoid.webrtc.media.audio.AudioSink;
import dev.onvoid.webrtc.media.audio.CustomAudioSource;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Microfone: captura do dispositivo, converte para 48 kHz mono, passa pelo
 * processamento de voz (eco, ruído, ganho) em blocos de 10 ms e empurra para a
 * faixa enviada aos vizinhos. Só captura com o microfone ligado, então o
 * sistema operacional não mostra o microfone em uso à toa.
 */
public final class MicCapture implements AudioSink {
	private static final AudioProcessingStreamConfig MONO_48K = new AudioProcessingStreamConfig(MonoConverter.RATE, 1);
	/** Estimativa do atraso da saída de som, somada ao da captura para o cancelamento de eco. */
	private static final int PLAYOUT_DELAY_MS = 40;
	private static final int SPEAKING_THRESHOLD = 1200;

	private final AudioProcessing processing;
	private final CustomAudioSource source;
	private final MonoConverter converter = new MonoConverter();

	private final byte[] frame = new byte[MonoConverter.FRAME * 2];
	private final byte[] processed = new byte[MonoConverter.FRAME * 2];
	private int fill;
	private int captureDelayMs;

	private AudioRecorder recorder;
	private volatile long lastLoudNanos;
	private volatile int peak;

	public MicCapture(AudioProcessing processing, CustomAudioSource source) {
		this.processing = processing;
		this.source = source;
	}

	/** Começa a capturar do dispositivo. Roda na thread de mídia. */
	public void start(AudioDevice device) {
		stop();
		recorder = new AudioRecorder();
		recorder.setAudioDevice(device);
		recorder.setAudioSink(this);
		recorder.start();
		CraftOffice.LOGGER.info("Capturando de {}", device != null ? device.getName() : "microfone padrão");
	}

	/** Para a captura e solta o dispositivo. Roda na thread de mídia. */
	public void stop() {
		if (recorder != null) {
			recorder.stop();
			recorder = null;
		}
		synchronized (this) {
			fill = 0;
		}
	}

	public boolean capturing() {
		return recorder != null;
	}

	@Override
	public synchronized void onRecordedData(byte[] audioSamples, int nSamples, int nBytesPerSample, int nChannels,
			int samplesPerSec, int totalDelayMS, int clockDrift) {
		captureDelayMs = totalDelayMS;
		converter.convert(audioSamples, nSamples, nChannels, samplesPerSec, this::append);
	}

	private void append(short sample) {
		frame[fill * 2] = (byte) sample;
		frame[fill * 2 + 1] = (byte) (sample >> 8);
		if (++fill < MonoConverter.FRAME) {
			return;
		}
		fill = 0;

		processing.setStreamDelayMs(captureDelayMs + PLAYOUT_DELAY_MS);
		processing.processStream(frame, MONO_48K, MONO_48K, processed);

		int blockPeak = 0;
		for (int i = 0; i < MonoConverter.FRAME; i++) {
			short value = (short) ((processed[i * 2] & 0xFF) | (processed[i * 2 + 1] << 8));
			blockPeak = Math.max(blockPeak, Math.abs(value));
		}
		peak = blockPeak;
		if (blockPeak > SPEAKING_THRESHOLD) {
			lastLoudNanos = System.nanoTime();
		}

		source.pushAudio(processed, 16, MonoConverter.RATE, 1, MonoConverter.FRAME);
	}

	public int peak() {
		return peak;
	}

	public boolean speaking() {
		return capturing() && System.nanoTime() - lastLoudNanos < 300_000_000L;
	}
}
