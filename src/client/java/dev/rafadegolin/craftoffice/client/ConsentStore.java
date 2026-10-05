package dev.rafadegolin.craftoffice.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import dev.rafadegolin.craftoffice.CraftOffice;

/**
 * Servidores em que o player já aceitou a tela de consentimento, em
 * {@code config/craftoffice-client.json}. A tela aparece uma vez por servidor.
 */
public final class ConsentStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("craftoffice-client.json");

	private static Data data;

	private static final class Data {
		Set<String> consentedServers = new LinkedHashSet<>();
	}

	private ConsentStore() {
	}

	/** Identifica o servidor atual. Mundos abertos para LAN mudam de porta, então contam pelo endereço sem porta. */
	public static String currentServerKey() {
		Minecraft mc = Minecraft.getInstance();
		ServerData server = mc.getCurrentServer();
		if (server != null) {
			String address = server.ip.toLowerCase();
			return "server:" + (server.isLan() ? address.replaceFirst(":\\d+$", "") : address);
		}
		return mc.isLocalServer() ? "local" : "unknown";
	}

	public static synchronized boolean hasConsent(String serverKey) {
		return load().consentedServers.contains(serverKey);
	}

	public static synchronized void setConsent(String serverKey, boolean consent) {
		Data current = load();
		boolean changed = consent ? current.consentedServers.add(serverKey) : current.consentedServers.remove(serverKey);
		if (changed) {
			save();
		}
	}

	private static Data load() {
		if (data != null) {
			return data;
		}
		data = new Data();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				if (loaded != null && loaded.consentedServers != null) {
					data = loaded;
				}
			}
			catch (IOException | JsonParseException e) {
				CraftOffice.LOGGER.warn("craftoffice-client.json inválido, consentimentos zerados", e);
			}
		}
		return data;
	}

	private static void save() {
		try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
			GSON.toJson(data, writer);
		}
		catch (IOException e) {
			CraftOffice.LOGGER.warn("Não deu para gravar craftoffice-client.json", e);
		}
	}
}
