package dev.rafadegolin.craftoffice.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

import dev.rafadegolin.craftoffice.CraftOffice;
import dev.rafadegolin.craftoffice.net.ConfigPayload;

/**
 * Configuração do servidor em {@code config/craftoffice-server.json}. Criada
 * com os valores padrão na primeira vez. Os raios imitam o Gather: conecta a
 * 6 blocos, desconecta a 8.
 */
public final class ServerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = "craftoffice-server.json";

	public double connectRadius = 6;
	public double disconnectRadius = 8;
	public int maxVideos = 4;
	public List<IceServerEntry> iceServers = new ArrayList<>(List.of(
			new IceServerEntry("stun:stun.l.google.com:19302", "", "")));

	public static final class IceServerEntry {
		public String url;
		public String username = "";
		public String credential = "";

		public IceServerEntry() {
		}

		IceServerEntry(String url, String username, String credential) {
			this.url = url;
			this.username = username;
			this.credential = credential;
		}
	}

	public static ServerConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		ServerConfig config = new ServerConfig();

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				ServerConfig loaded = GSON.fromJson(reader, ServerConfig.class);
				if (loaded != null) {
					config = loaded;
				}
			}
			catch (IOException | JsonParseException e) {
				CraftOffice.LOGGER.error("{} inválido, usando os valores padrão", FILE_NAME, e);
				return config.validated();
			}
		}

		config = config.validated();
		// Regrava para completar campos novos de versões futuras.
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		}
		catch (IOException e) {
			CraftOffice.LOGGER.warn("Não deu para gravar {}", FILE_NAME, e);
		}
		return config;
	}

	private ServerConfig validated() {
		connectRadius = Math.clamp(connectRadius, 1, 64);
		disconnectRadius = Math.clamp(disconnectRadius, connectRadius, 96);
		maxVideos = Math.clamp(maxVideos, 0, 16);
		if (iceServers == null) {
			iceServers = new ArrayList<>();
		}
		iceServers.removeIf(entry -> entry == null || entry.url == null || entry.url.isBlank());
		return this;
	}

	public ConfigPayload toPayload() {
		List<ConfigPayload.IceServer> servers = iceServers.stream()
				.map(entry -> new ConfigPayload.IceServer(entry.url,
						entry.username == null ? "" : entry.username,
						entry.credential == null ? "" : entry.credential))
				.toList();
		return new ConfigPayload(CraftOffice.PROTOCOL, connectRadius, disconnectRadius, maxVideos, servers);
	}
}
