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
 * Preferências do cliente em {@code config/craftoffice-client.json}:
 * servidores com consentimento, áudio do mod ligado ou não, e os
 * dispositivos escolhidos, guardados pelo nome.
 */
public final class ClientSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("craftoffice-client.json");
	/** Id do Simple Voice Chat no Fabric. */
	private static final String VOICE_CHAT_MOD = "voicechat";

	private static Data data;

	private static final class Data {
		Set<String> consentedServers = new LinkedHashSet<>();
		/** Nulo até o player escolher. Aí o padrão depende do Simple Voice Chat. */
		Boolean audioEnabled;
		String microphone;
		String speaker;
	}

	private ClientSettings() {
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

	/**
	 * Áudio do mod ligado. Com o Simple Voice Chat instalado começa desligado,
	 * para os dois não disputarem o microfone.
	 */
	public static synchronized boolean audioEnabled() {
		Boolean value = load().audioEnabled;
		return value != null ? value : !FabricLoader.getInstance().isModLoaded(VOICE_CHAT_MOD);
	}

	public static synchronized void setAudioEnabled(boolean enabled) {
		load().audioEnabled = enabled;
		save();
	}

	public static synchronized String microphone() {
		return load().microphone;
	}

	public static synchronized void setMicrophone(String name) {
		load().microphone = name;
		save();
	}

	public static synchronized String speaker() {
		return load().speaker;
	}

	public static synchronized void setSpeaker(String name) {
		load().speaker = name;
		save();
	}

	private static Data load() {
		if (data != null) {
			return data;
		}
		data = new Data();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				Data loaded = GSON.fromJson(reader, Data.class);
				if (loaded != null) {
					data = loaded;
					if (data.consentedServers == null) {
						data.consentedServers = new LinkedHashSet<>();
					}
				}
			}
			catch (IOException | JsonParseException e) {
				CraftOffice.LOGGER.warn("craftoffice-client.json inválido, voltando ao padrão", e);
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
