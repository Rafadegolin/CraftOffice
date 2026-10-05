package dev.rafadegolin.craftoffice.proximity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.rafadegolin.craftoffice.proximity.ProximityEngine.Action;
import dev.rafadegolin.craftoffice.proximity.ProximityEngine.Change;
import dev.rafadegolin.craftoffice.proximity.ProximityEngine.Position;

class ProximityEngineTest {
	private static final String OVERWORLD = "overworld";
	/** 5 ticks a 20 por segundo, como no servidor. */
	private static final long STEP = 250;

	private final UUID a = new UUID(0, 1);
	private final UUID b = new UUID(0, 2);
	private final UUID c = new UUID(0, 3);

	private ProximityEngine engine;
	private long now;

	@BeforeEach
	void setUp() {
		engine = new ProximityEngine(new ProximityEngine.Settings(6, 8, 500, 1000, 4));
		now = 0;
	}

	private static Position at(UUID id, double x) {
		return new Position(id, OVERWORLD, x, 64, 0);
	}

	/** Roda o motor por {@code millis} com as mesmas posições e junta as mudanças. */
	private List<Change> run(long millis, Position... players) {
		List<Change> all = new ArrayList<>();
		long end = now + millis;
		do {
			all.addAll(engine.update(List.of(players), now));
			now += STEP;
		} while (now <= end);
		return all;
	}

	private static long count(List<Change> changes, Action action) {
		return changes.stream().filter(change -> change.action() == action).count();
	}

	@Test
	void connectsInsideRadiusAfterEnterDelay() {
		List<Change> changes = run(250, at(a, 0), at(b, 5));
		assertEquals(0, count(changes, Action.ADD), "não conecta antes do meio segundo");

		changes = run(500, at(a, 0), at(b, 5));
		assertEquals(2, count(changes, Action.ADD), "um aviso para cada lado");
		assertTrue(engine.areNeighbors(a, b));
	}

	@Test
	void smallerUuidInitiates() {
		List<Change> changes = run(1000, at(a, 0), at(b, 5));
		Change toA = changes.stream().filter(change -> change.player().equals(a)).findFirst().orElseThrow();
		Change toB = changes.stream().filter(change -> change.player().equals(b)).findFirst().orElseThrow();
		assertTrue(toA.initiator());
		assertFalse(toB.initiator());
		assertEquals(b, toA.peer());
		assertEquals(a, toB.peer());
	}

	@Test
	void passingByDoesNotConnect() {
		run(250, at(a, 0), at(b, 5));
		List<Change> changes = run(2000, at(a, 0), at(b, 20));
		assertEquals(0, count(changes, Action.ADD));
	}

	@Test
	void exactlyAtConnectRadiusConnects() {
		List<Change> changes = run(1000, at(a, 0), at(b, 6));
		assertEquals(2, count(changes, Action.ADD));
	}

	@Test
	void staysConnectedBetweenRadii() {
		run(1000, at(a, 0), at(b, 5));
		List<Change> changes = run(5000, at(a, 0), at(b, 7.5));
		assertEquals(0, count(changes, Action.REMOVE), "entre 6 e 8 blocos continua conectado");
		assertTrue(engine.areNeighbors(a, b));
	}

	@Test
	void disconnectsOnlyAfterExitDelayBeyondRadius() {
		run(1000, at(a, 0), at(b, 5));

		List<Change> changes = run(750, at(a, 0), at(b, 9));
		assertEquals(0, count(changes, Action.REMOVE), "menos de 1 segundo fora ainda não corta");

		changes = run(500, at(a, 0), at(b, 9));
		assertEquals(2, count(changes, Action.REMOVE));
		assertFalse(engine.areNeighbors(a, b));
	}

	@Test
	void doesNotFlickerAtTheEdge() {
		run(1000, at(a, 0), at(b, 5));
		List<Change> changes = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			changes.addAll(run(0, at(a, 0), at(b, i % 2 == 0 ? 8.5 : 7.5)));
		}
		assertEquals(0, count(changes, Action.REMOVE), "voltar para dentro zera a contagem de saída");
	}

	@Test
	void threeInARowOnlyConnectsAdjacent() {
		run(1000, at(a, 0), at(b, 5), at(c, 10));
		assertTrue(engine.areNeighbors(a, b));
		assertTrue(engine.areNeighbors(b, c));
		assertFalse(engine.areNeighbors(a, c), "10 blocos é longe demais");
		assertEquals(Set.of(a, c), engine.neighbors(b));
	}

	@Test
	void dimensionChangeDisconnectsImmediately() {
		run(1000, at(a, 0), at(b, 5));
		List<Change> changes = engine.update(List.of(at(a, 0), new Position(b, "nether", 5, 64, 0)), now);
		assertEquals(2, count(changes, Action.REMOVE));
		assertFalse(engine.areNeighbors(a, b));
	}

	@Test
	void sameCoordinatesInOtherDimensionNeverConnect() {
		List<Change> changes = run(2000, at(a, 0), new Position(b, "nether", 0, 64, 0));
		assertEquals(0, count(changes, Action.ADD));
	}

	@Test
	void disconnectedPlayerIsRemovedImmediately() {
		run(1000, at(a, 0), at(b, 5), at(c, 3));
		List<Change> changes = engine.update(List.of(at(a, 0), at(b, 5)), now);
		Set<UUID> notified = changes.stream()
				.filter(change -> change.action() == Action.REMOVE)
				.map(Change::player)
				.collect(Collectors.toSet());
		assertEquals(Set.of(a, b, c), notified, "os dois vizinhos e o próprio recebem o aviso");
		assertFalse(engine.areNeighbors(a, c));
		assertTrue(engine.areNeighbors(a, b));
	}

	@Test
	void videoLimitedToNearestNeighbors() {
		engine = new ProximityEngine(new ProximityEngine.Settings(6, 8, 0, 1000, 2));
		UUID d = new UUID(0, 4);
		List<Change> changes = run(0, at(a, 0), at(b, 1), at(c, 2), at(d, 3));

		for (UUID player : List.of(a, b, c, d)) {
			long videos = changes.stream().filter(change -> change.player().equals(player) && change.video()).count();
			assertTrue(videos <= 2, "ninguém recebe mais vídeos que o limite");
		}
		assertTrue(changes.contains(new Change(Action.ADD, a, b, true, true)), "o mais próximo manda vídeo");
		assertTrue(changes.contains(new Change(Action.ADD, a, d, true, false)), "o mais longe fica só com áudio");
		assertEquals(Set.of(b, c, d), engine.neighbors(a), "todos continuam conectados por áudio");
	}

	@Test
	void videoMovesToNewNearestNeighbor() {
		engine = new ProximityEngine(new ProximityEngine.Settings(6, 8, 0, 1000, 1));
		run(0, at(a, 0), at(b, 2), at(c, 4));

		List<Change> changes = run(0, at(a, 0), at(b, 4), at(c, 1));
		assertTrue(changes.contains(new Change(Action.UPDATE, a, c, true, true)), "C passa a mandar vídeo para A");
		assertTrue(changes.contains(new Change(Action.UPDATE, a, b, true, false)), "B passa a só áudio");
	}
}
