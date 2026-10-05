package dev.rafadegolin.craftoffice.client.media;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryUtil;

import dev.onvoid.webrtc.media.FourCC;
import dev.onvoid.webrtc.media.video.VideoBufferConverter;
import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoFrameBuffer;
import dev.onvoid.webrtc.media.video.VideoTrackSink;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Recebe frames na thread da WebRTC, converte para RGBA no tamanho fixo e
 * guarda só o mais novo. A thread do jogo copia quando há frame novo.
 */
public final class FrameSlot implements VideoTrackSink {
	public static final int WIDTH = 320;
	public static final int HEIGHT = 240;
	private static final int BYTES = WIDTH * HEIGHT * 4;

	private ByteBuffer back = MemoryUtil.memAlloc(BYTES);
	private ByteBuffer front = MemoryUtil.memAlloc(BYTES);
	private boolean fresh;
	private boolean closed;

	private final RateCounter fps = new RateCounter();
	private volatile String sourceSize = "-";

	@Override
	public void onVideoFrame(VideoFrame frame) {
		VideoFrameBuffer src = frame.buffer;
		int w = src.getWidth();
		int h = src.getHeight();
		sourceSize = w + "x" + h;

		try {
			if (w == WIDTH && h == HEIGHT) {
				convert(src);
			}
			else {
				// Corta para 4:3 no centro antes de reduzir, sem distorcer o rosto.
				int cropW = Math.min(w, h * 4 / 3);
				int cropH = Math.min(h, w * 3 / 4);
				VideoFrameBuffer scaled = src.cropAndScale((w - cropW) / 2, (h - cropH) / 2, cropW, cropH, WIDTH, HEIGHT);
				try {
					convert(scaled);
				}
				finally {
					scaled.release();
				}
			}
		}
		catch (Exception e) {
			CraftOffice.LOGGER.warn("Falha ao converter frame {}", sourceSize, e);
			return;
		}

		fps.tick();
	}

	private void convert(VideoFrameBuffer buffer) throws Exception {
		ByteBuffer target;
		synchronized (this) {
			if (closed) {
				return;
			}
			target = back;
		}

		// No libyuv, ABGR é a ordem de bytes R, G, B, A: a mesma da NativeImage.
		VideoBufferConverter.convertFromI420(buffer, target.clear(), FourCC.ABGR);

		synchronized (this) {
			if (closed) {
				return;
			}
			back = front;
			front = target;
			fresh = true;
		}
	}

	/** Copia o frame mais novo para {@code address}, se houver. Thread do jogo. */
	public synchronized boolean copyIfFresh(long address) {
		if (!fresh || closed) {
			return false;
		}
		MemoryUtil.memCopy(MemoryUtil.memAddress(front), address, BYTES);
		fresh = false;
		return true;
	}

	public double fps() {
		return fps.rate();
	}

	public String sourceSize() {
		return sourceSize;
	}

	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		MemoryUtil.memFree(back);
		MemoryUtil.memFree(front);
	}
}
