package dev.rafadegolin.craftoffice.client.media;

import java.nio.ByteBuffer;

import dev.onvoid.webrtc.media.video.CustomVideoSource;
import dev.onvoid.webrtc.media.video.NativeI420Buffer;
import dev.onvoid.webrtc.media.video.VideoFrame;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Vídeo sintético a 20 quadros por segundo: fundo que muda de cor e uma faixa
 * que anda. Substitui a webcam no segundo cliente da mesma máquina.
 */
final class TestPatternSource {
	private static final int W = FrameSlot.WIDTH;
	private static final int H = FrameSlot.HEIGHT;

	private final CustomVideoSource source = new CustomVideoSource();
	private volatile boolean running;
	private Thread thread;

	CustomVideoSource source() {
		return source;
	}

	void start() {
		running = true;
		thread = new Thread(this::loop, "craftoffice-pattern");
		thread.setDaemon(true);
		thread.start();
	}

	void stop() {
		running = false;
		if (thread != null) {
			try {
				thread.join(500);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void loop() {
		int n = 0;
		while (running) {
			long start = System.nanoTime();
			NativeI420Buffer buffer = NativeI420Buffer.allocate(W, H);
			fill(buffer, n++);
			VideoFrame frame = new VideoFrame(buffer, start);
			try {
				source.pushFrame(frame);
			}
			catch (Throwable t) {
				CraftOffice.LOGGER.warn("Falha no padrão de teste", t);
				running = false;
			}
			finally {
				frame.release();
			}

			long sleepMs = 50 - (System.nanoTime() - start) / 1_000_000;
			if (sleepMs > 0) {
				try {
					Thread.sleep(sleepMs);
				}
				catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	private static void fill(NativeI420Buffer buffer, int n) {
		ByteBuffer y = buffer.getDataY();
		ByteBuffer u = buffer.getDataU();
		ByteBuffer v = buffer.getDataV();
		int strideY = buffer.getStrideY();
		int strideU = buffer.getStrideU();
		int strideV = buffer.getStrideV();
		int bar = (n * 4) % W;

		for (int row = 0; row < H; row++) {
			for (int col = 0; col < W; col++) {
				boolean onBar = col >= bar && col < bar + 24;
				y.put(row * strideY + col, (byte) (onBar ? 235 : 60 + row / 4));
			}
		}
		byte uValue = (byte) (128 + 80 * Math.sin(n / 20.0));
		byte vValue = (byte) (128 + 80 * Math.cos(n / 20.0));
		for (int row = 0; row < H / 2; row++) {
			for (int col = 0; col < W / 2; col++) {
				u.put(row * strideU + col, uValue);
				v.put(row * strideV + col, vValue);
			}
		}
	}
}
