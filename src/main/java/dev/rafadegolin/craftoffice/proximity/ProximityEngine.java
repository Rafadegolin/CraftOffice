package dev.rafadegolin.craftoffice.proximity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Decide quem conversa com quem a partir das posições. Não conhece o
 * Minecraft: recebe posições e o relógio, devolve as mudanças.
 * <p>
 * Regras do estudo:
 * <ul>
 * <li>Conecta a {@code connectRadius} depois de {@code enterDelayMs} perto: quem só passa não conecta.</li>
 * <li>Desconecta depois de {@code exitDelayMs} além de {@code disconnectRadius}: a folga entre os raios evita piscar na borda.</li>
 * <li>Dimensões diferentes nunca conectam e desconectam na hora.</li>
 * <li>Zona vence distância: na mesma zona todos conversam com volume cheio; zonas diferentes nunca conectam.</li>
 * <li>Quem está no palco alcança todos da mesma zona, ou num raio de {@code stageRadius} fora delas.</li>
 * <li>{@code radiusFactor} encolhe os raios de quem está em foco.</li>
 * <li>Vídeo só entre quem está entre os {@code maxVideos} vizinhos mais próximos um do outro.</li>
 * </ul>
 * Não é thread-safe: o servidor chama sempre da mesma thread.
 */
public final class ProximityEngine {
	public record Settings(double connectRadius, double disconnectRadius, long enterDelayMs, long exitDelayMs,
			int maxVideos, double stageRadius) {
		public Settings {
			if (connectRadius <= 0 || disconnectRadius < connectRadius) {
				throw new IllegalArgumentException("Raios inválidos: " + connectRadius + " / " + disconnectRadius);
			}
		}

		public Settings(double connectRadius, double disconnectRadius, long enterDelayMs, long exitDelayMs, int maxVideos) {
			this(connectRadius, disconnectRadius, enterDelayMs, exitDelayMs, maxVideos, 48);
		}
	}

	/**
	 * Um participante. {@code zone} é o nome da zona em que está, ou nulo fora
	 * delas. {@code radiusFactor} multiplica os raios: 1 normal, menor em foco.
	 */
	public record Position(UUID id, String dimension, double x, double y, double z, String zone, boolean stage,
			double radiusFactor) {
		public Position(UUID id, String dimension, double x, double y, double z) {
			this(id, dimension, x, y, z, null, false, 1);
		}

		double distanceSquared(Position other) {
			double dx = x - other.x;
			double dy = y - other.y;
			double dz = z - other.z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	public enum Action { ADD, REMOVE, UPDATE }

	/**
	 * Uma mudança endereçada a {@code player}, sobre a conversa com {@code peer}.
	 * {@code fullVolume}: mesma zona ou palco, então a distância não abaixa o som.
	 */
	public record Change(Action action, UUID player, UUID peer, boolean initiator, boolean video, boolean fullVolume) {
	}

	/** Até onde um par se alcança agora. */
	private enum Reach { NONE, PROXIMITY, FULL }

	/** Par sem ordem: {@code low} é sempre o menor UUID, que também inicia a conexão. */
	private record Pair(UUID low, UUID high) {
		static Pair of(UUID a, UUID b) {
			return a.compareTo(b) < 0 ? new Pair(a, b) : new Pair(b, a);
		}

		UUID other(UUID id) {
			return id.equals(low) ? high : low;
		}
	}

	private static final class Link {
		final long candidateSince;
		boolean connected;
		long outsideSince = -1;
		boolean video;
		boolean fullVolume;

		Link(long candidateSince) {
			this.candidateSince = candidateSince;
		}
	}

	private final Settings settings;
	private final Map<Pair, Link> links = new HashMap<>();

	public ProximityEngine(Settings settings) {
		this.settings = settings;
	}

	public Settings settings() {
		return settings;
	}

	private Reach reach(Position a, Position b) {
		if (!a.dimension().equals(b.dimension()) || !Objects.equals(a.zone(), b.zone())) {
			return Reach.NONE;
		}
		if (a.zone() != null) {
			return Reach.FULL;
		}
		if ((a.stage() || b.stage()) && a.distanceSquared(b) <= settings.stageRadius() * settings.stageRadius()) {
			return Reach.FULL;
		}
		return Reach.PROXIMITY;
	}

	private double factor(Position a, Position b) {
		return Math.min(a.radiusFactor(), b.radiusFactor());
	}

	/** Atualiza com as posições de todos os players participantes. Quem não está na lista sai de tudo. */
	public List<Change> update(Collection<Position> players, long nowMs) {
		Map<UUID, Position> byId = new HashMap<>();
		for (Position p : players) {
			byId.put(p.id(), p);
		}

		List<Change> changes = new ArrayList<>();
		List<Pair> added = new ArrayList<>();

		// Pares que já existem: avançam, ficam ou caem.
		for (Iterator<Map.Entry<Pair, Link>> it = links.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Pair, Link> entry = it.next();
			Pair pair = entry.getKey();
			Link link = entry.getValue();
			Position a = byId.get(pair.low());
			Position b = byId.get(pair.high());
			Reach reach = a == null || b == null ? Reach.NONE : reach(a, b);

			if (reach == Reach.NONE) {
				if (link.connected) {
					emit(changes, Action.REMOVE, pair, false, false);
				}
				it.remove();
				continue;
			}

			double distSq = a.distanceSquared(b);
			double connect = settings.connectRadius() * factor(a, b);
			double disconnect = settings.disconnectRadius() * factor(a, b);
			boolean inside = reach == Reach.FULL || distSq <= connect * connect;
			boolean beyond = reach == Reach.PROXIMITY && distSq > disconnect * disconnect;

			if (!link.connected) {
				if (!inside) {
					it.remove();
				}
				else if (nowMs - link.candidateSince >= settings.enterDelayMs()) {
					link.connected = true;
					added.add(pair);
				}
			}
			else if (beyond) {
				if (link.outsideSince < 0) {
					link.outsideSince = nowMs;
				}
				else if (nowMs - link.outsideSince >= settings.exitDelayMs()) {
					emit(changes, Action.REMOVE, pair, false, false);
					it.remove();
				}
			}
			else {
				link.outsideSince = -1;
			}
		}

		// Pares novos ao alcance viram candidatos.
		List<Position> list = new ArrayList<>(byId.values());
		for (int i = 0; i < list.size(); i++) {
			for (int j = i + 1; j < list.size(); j++) {
				Position a = list.get(i);
				Position b = list.get(j);
				Reach reach = reach(a, b);
				if (reach == Reach.NONE) {
					continue;
				}
				double connect = settings.connectRadius() * factor(a, b);
				if (reach == Reach.PROXIMITY && a.distanceSquared(b) > connect * connect) {
					continue;
				}
				Pair pair = Pair.of(a.id(), b.id());
				if (!links.containsKey(pair)) {
					Link link = new Link(nowMs);
					links.put(pair, link);
					if (settings.enterDelayMs() <= 0) {
						link.connected = true;
						added.add(pair);
					}
				}
			}
		}

		Set<Pair> videoPairs = computeVideoPairs(byId);
		for (Pair pair : added) {
			Link link = links.get(pair);
			link.video = videoPairs.contains(pair);
			link.fullVolume = reach(byId.get(pair.low()), byId.get(pair.high())) == Reach.FULL;
			emit(changes, Action.ADD, pair, link.video, link.fullVolume);
		}
		for (Map.Entry<Pair, Link> entry : links.entrySet()) {
			Pair pair = entry.getKey();
			Link link = entry.getValue();
			if (!link.connected || added.contains(pair)) {
				continue;
			}
			boolean video = videoPairs.contains(pair);
			boolean fullVolume = reach(byId.get(pair.low()), byId.get(pair.high())) == Reach.FULL;
			if (video != link.video || fullVolume != link.fullVolume) {
				link.video = video;
				link.fullVolume = fullVolume;
				emit(changes, Action.UPDATE, pair, video, fullVolume);
			}
		}
		return changes;
	}

	/** Vídeo quando cada um está entre os {@code maxVideos} vizinhos conectados mais próximos do outro. */
	private Set<Pair> computeVideoPairs(Map<UUID, Position> byId) {
		Map<UUID, List<UUID>> neighbors = new HashMap<>();
		for (Map.Entry<Pair, Link> entry : links.entrySet()) {
			if (entry.getValue().connected) {
				Pair pair = entry.getKey();
				neighbors.computeIfAbsent(pair.low(), id -> new ArrayList<>()).add(pair.high());
				neighbors.computeIfAbsent(pair.high(), id -> new ArrayList<>()).add(pair.low());
			}
		}

		Map<UUID, Set<UUID>> nearest = new HashMap<>();
		for (Map.Entry<UUID, List<UUID>> entry : neighbors.entrySet()) {
			Position self = byId.get(entry.getKey());
			Set<UUID> top = new HashSet<>();
			entry.getValue().stream()
					.sorted(Comparator.<UUID>comparingDouble(id -> self.distanceSquared(byId.get(id))).thenComparing(id -> id))
					.limit(settings.maxVideos())
					.forEach(top::add);
			nearest.put(entry.getKey(), top);
		}

		Set<Pair> result = new HashSet<>();
		for (Map.Entry<Pair, Link> entry : links.entrySet()) {
			Pair pair = entry.getKey();
			if (entry.getValue().connected
					&& nearest.getOrDefault(pair.low(), Set.of()).contains(pair.high())
					&& nearest.getOrDefault(pair.high(), Set.of()).contains(pair.low())) {
				result.add(pair);
			}
		}
		return result;
	}

	private static void emit(List<Change> changes, Action action, Pair pair, boolean video, boolean fullVolume) {
		changes.add(new Change(action, pair.low(), pair.high(), true, video, fullVolume));
		changes.add(new Change(action, pair.high(), pair.low(), false, video, fullVolume));
	}

	/** Vizinhos conectados de um player. */
	public Set<UUID> neighbors(UUID player) {
		Set<UUID> result = new HashSet<>();
		for (Map.Entry<Pair, Link> entry : links.entrySet()) {
			Pair pair = entry.getKey();
			if (entry.getValue().connected && (pair.low().equals(player) || pair.high().equals(player))) {
				result.add(pair.other(player));
			}
		}
		return result;
	}

	public boolean areNeighbors(UUID a, UUID b) {
		Link link = links.get(Pair.of(a, b));
		return link != null && link.connected;
	}

	public void clear() {
		links.clear();
	}
}
