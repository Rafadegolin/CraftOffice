package dev.rafadegolin.craftoffice.zone;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import dev.rafadegolin.craftoffice.CraftOffice;

/** Zonas do mundo, em {@code <mundo>/craftoffice/zones.json}. Thread do servidor. */
public final class ZoneStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final Map<String, Zone> zones = new LinkedHashMap<>();

	public ZoneStore(Path worldRoot) {
		this.file = worldRoot.resolve("craftoffice").resolve("zones.json");
		load();
	}

	private void load() {
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Zone[] loaded = GSON.fromJson(reader, Zone[].class);
			if (loaded != null) {
				Arrays.stream(loaded).forEach(zone -> zones.put(key(zone.name()), zone));
			}
			CraftOffice.LOGGER.info("{} zona(s) carregada(s)", zones.size());
		}
		catch (IOException | JsonParseException e) {
			CraftOffice.LOGGER.error("zones.json inválido, nenhuma zona carregada", e);
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(zones.values().toArray(Zone[]::new), writer);
			}
		}
		catch (IOException e) {
			CraftOffice.LOGGER.error("Não deu para gravar zones.json", e);
		}
	}

	private static String key(String name) {
		return name.toLowerCase();
	}

	/** Cria a zona. Devolve a zona com que ela colide, ou nulo se deu certo. */
	public Zone create(Zone zone) {
		for (Zone other : zones.values()) {
			if (other.name().equalsIgnoreCase(zone.name()) || other.overlaps(zone)) {
				return other;
			}
		}
		zones.put(key(zone.name()), zone);
		save();
		return null;
	}

	public boolean delete(String name) {
		boolean removed = zones.remove(key(name)) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	public Zone get(String name) {
		return zones.get(key(name));
	}

	public Collection<Zone> all() {
		return List.copyOf(zones.values());
	}

	public List<String> names() {
		return new ArrayList<>(zones.values().stream().map(Zone::name).toList());
	}

	/** A zona que contém o ponto, ou nulo. Zonas não se sobrepõem, então há no máximo uma. */
	public Zone at(String dimension, double x, double y, double z) {
		for (Zone zone : zones.values()) {
			if (zone.contains(dimension, x, y, z)) {
				return zone;
			}
		}
		return null;
	}
}
