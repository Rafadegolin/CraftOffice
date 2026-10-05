package dev.rafadegolin.craftoffice.client.media;

/** Eventos por segundo, medidos em janelas de um segundo. */
public final class RateCounter {
	private long windowStart = System.nanoTime();
	private int count;
	private volatile double rate;

	public synchronized void tick() {
		count++;
		long now = System.nanoTime();
		long elapsed = now - windowStart;
		if (elapsed >= 1_000_000_000L) {
			rate = count * 1e9 / elapsed;
			count = 0;
			windowStart = now;
		}
	}

	public double rate() {
		// Sem eventos há mais de 2 segundos, a taxa é zero.
		synchronized (this) {
			if (System.nanoTime() - windowStart > 2_000_000_000L) {
				return 0;
			}
		}
		return rate;
	}
}
