package dev.rafadegolin.craftoffice.client.render;

import java.util.Arrays;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import dev.rafadegolin.craftoffice.client.media.FrameSlot;

/**
 * Textura dinâmica alimentada por um {@link FrameSlot}. Só envia para a GPU
 * quando há frame novo. Mede o custo de cópia e envio. Thread do jogo.
 */
public final class VideoTexture implements AutoCloseable {
	private final Identifier id;
	private final DynamicTexture texture;
	private final NativeImage image;

	private final long[] samples = new long[200];
	private int sampleCount;
	private int sampleIndex;

	public VideoTexture(Identifier id) {
		this.id = id;
		this.image = new NativeImage(FrameSlot.WIDTH, FrameSlot.HEIGHT, false);
		this.texture = new DynamicTexture(id::toString, image);
		Minecraft.getInstance().getTextureManager().register(id, texture);
	}

	public Identifier id() {
		return id;
	}

	/** Copia o frame mais novo e envia. Devolve se houve envio. */
	public boolean update(FrameSlot slot) {
		long start = System.nanoTime();
		if (!slot.copyIfFresh(image.getPointer())) {
			return false;
		}
		texture.upload();
		samples[sampleIndex] = System.nanoTime() - start;
		sampleIndex = (sampleIndex + 1) % samples.length;
		sampleCount = Math.min(sampleCount + 1, samples.length);
		return true;
	}

	/** Média e p95 do tempo de cópia e envio, em microssegundos. */
	public long[] uploadMicros() {
		if (sampleCount == 0) {
			return new long[] {0, 0};
		}
		long[] sorted = Arrays.copyOf(samples, sampleCount);
		Arrays.sort(sorted);
		long sum = 0;
		for (long s : sorted) {
			sum += s;
		}
		int p95 = Math.min(sampleCount - 1, (int) (sampleCount * 0.95));
		return new long[] {sum / sampleCount / 1000, sorted[p95] / 1000};
	}

	@Override
	public void close() {
		Minecraft.getInstance().getTextureManager().release(id);
	}
}
