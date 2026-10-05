package dev.rafadegolin.craftoffice.client;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import dev.rafadegolin.craftoffice.CraftOffice;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.media.PeerSession;
import dev.rafadegolin.craftoffice.client.render.VideoHud;
import dev.rafadegolin.craftoffice.client.ui.Keys;
import dev.rafadegolin.craftoffice.client.ui.OfficeActions;
import dev.rafadegolin.craftoffice.client.ui.StatusHud;
import dev.rafadegolin.craftoffice.net.ConfigPayload;
import dev.rafadegolin.craftoffice.net.HelloPayload;
import dev.rafadegolin.craftoffice.net.SignalPayload;

public class CraftOfficeClient implements ClientModInitializer {
	private final VideoHud hud = new VideoHud();
	private int statsTicks;

	@Override
	public void onInitializeClient() {
		// Carrega a parte nativa já na abertura, fora da thread do jogo.
		MediaEngine.get();

		Keys.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			OfficeSession.tick(client);

			// Com conexão aberta, grava os números no log a cada 5 segundos.
			MediaEngine engine = MediaEngine.getIfLoaded();
			if (++statsTicks >= 100 && engine != null && !engine.sessions().isEmpty()) {
				statsTicks = 0;
				engine.run(() -> statsLines(engine).forEach(line -> CraftOffice.LOGGER.info("[auto] {}", line)));
			}
		});

		HudElementRegistry.addLast(CraftOffice.id("status"), StatusHud::extract);
		HudElementRegistry.addLast(CraftOffice.id("video"), hud::extract);

		// Servidor sem o mod não registra o hello: o mod fica quieto e nada quebra.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			OfficeSession.reset();
			if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
				ClientPlayNetworking.send(new HelloPayload(CraftOffice.PROTOCOL));
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
			if (payload.protocol() != CraftOffice.PROTOCOL) {
				CraftOffice.LOGGER.warn("Servidor usa o protocolo {} e o cliente o {}", payload.protocol(), CraftOffice.PROTOCOL);
				return;
			}
			OfficeSession.onConfig(payload);
		});

		ClientPlayNetworking.registerGlobalReceiver(SignalPayload.TYPE, (payload, context) -> {
			// Sem consentimento, ninguém abre conexão com este player.
			if (!OfficeSession.active()) {
				return;
			}
			MediaEngine engine = MediaEngine.get();
			engine.run(() -> {
				if (!engine.ready()) {
					return;
				}
				if (payload.kind().equals("bye")) {
					engine.closeSession(payload.peer());
					return;
				}
				PeerSession session = engine.sessions().get(payload.peer());
				if (session == null && payload.kind().equals("offer")) {
					session = engine.session(payload.peer(), false);
				}
				if (session != null) {
					session.onSignal(payload.kind(), payload.data());
				}
			});
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MediaEngine engine = MediaEngine.getIfLoaded();
			if (engine != null) {
				engine.run(engine::reset);
			}
			client.execute(() -> {
				OfficeSession.reset();
				hud.clear();
			});
		});

		// Sem isso a thread nativa da WebRTC segura o processo e o jogo não fecha.
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			MediaEngine engine = MediaEngine.getIfLoaded();
			if (engine != null) {
				engine.shutdownAndWait(3000);
				startExitGuard(Thread.currentThread());
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal("office")
						.then(ClientCommands.literal("devices").executes(this::devices))
						.then(ClientCommands.literal("cam").executes(ctx -> {
							OfficeActions.toggleCamera();
							return 1;
						}))
						.then(ClientCommands.literal("call")
								.then(ClientCommands.argument("player", StringArgumentType.word()).executes(this::call)))
						.then(ClientCommands.literal("hangup").executes(ctx -> {
							MediaEngine engine = MediaEngine.get();
							engine.run(engine::closeAll);
							return 1;
						}))
						.then(ClientCommands.literal("mic")
								.then(ClientCommands.argument("number", IntegerArgumentType.integer(1)).executes(ctx -> {
									int index = IntegerArgumentType.getInteger(ctx, "number");
									MediaEngine engine = MediaEngine.get();
									engine.run(() -> feedback(List.of(engine.setMicrophone(index))));
									return 1;
								})))
						.then(ClientCommands.literal("stats").executes(this::stats))));
	}

	private int devices(CommandContext<FabricClientCommandSource> ctx) {
		MediaEngine engine = MediaEngine.get();
		engine.run(() -> {
			List<String> lines = engine.ready()
					? engine.describeDevices()
					: List.of("webrtc-java não carregou: " + engine.loadError());
			feedback(lines);
		});
		return 1;
	}

	private int call(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "player");
		PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfoIgnoreCase(name);
		if (info == null) {
			ctx.getSource().sendError(Component.literal("Player não encontrado: " + name));
			return 0;
		}
		if (!OfficeSession.serverHasMod()) {
			ctx.getSource().sendError(Component.translatable("craftoffice.status.no_server_mod"));
			return 0;
		}
		if (!OfficeSession.hasConsent()) {
			ctx.getSource().sendError(Component.translatable("craftoffice.status.no_consent"));
			return 0;
		}

		UUID peer = info.getProfile().id();
		if (peer.equals(ctx.getSource().getPlayer().getUUID())) {
			ctx.getSource().sendError(Component.literal("Não dá para chamar você mesmo"));
			return 0;
		}
		MediaEngine engine = MediaEngine.get();
		engine.run(() -> {
			if (!engine.ready()) {
				feedback(List.of("webrtc-java não carregou: " + engine.loadError()));
				return;
			}
			engine.closeSession(peer);
			engine.session(peer, true);
			feedback(List.of("Chamando " + info.getProfile().name()));
		});
		return 1;
	}

	private int stats(CommandContext<FabricClientCommandSource> ctx) {
		MediaEngine engine = MediaEngine.get();
		engine.run(() -> {
			List<String> lines = statsLines(engine);
			feedback(lines);
			lines.forEach(line -> CraftOffice.LOGGER.info("[stats] {}", line));
		});
		return 1;
	}

	private static List<String> statsLines(MediaEngine engine) {
		List<String> lines = new java.util.ArrayList<>();
		lines.add("webrtc-java: " + (engine.ready() ? "carregada em " + engine.loadMillis() + " ms" : "falhou: " + engine.loadError()));
		lines.add(String.format("Câmera: %s, %.1f fps, origem %s, jogo %d fps", engine.videoOn() ? "ligada" : "desligada",
				engine.selfSlot().fps(), engine.selfSlot().sourceSize(), Minecraft.getInstance().getFps()));
		lines.add("Microfone: " + engine.micName());
		for (PeerSession s : engine.sessions().values()) {
			lines.add(String.format("%s: %s, par %s, vídeo %.1f fps %s, áudio %s, %.0f callbacks/s, pico %d",
					s.peer().toString().substring(0, 8), s.state(), s.selectedPair(), s.remoteSlot().fps(),
					s.remoteSlot().sourceSize(), s.remoteAudioFormat(), s.remoteAudioCallbacksPerSecond(), s.remoteAudioPeak()));
		}
		return lines;
	}

	/**
	 * A captura da webcam deixa uma thread nativa presa à JVM como não-daemon,
	 * mesmo depois de liberada, e o processo não termina. Quando a thread
	 * principal do jogo acaba, espera 2 s e encerra a JVM pelo caminho normal,
	 * com os ganchos de saída. Se nada estiver preso, a JVM sai antes e esta
	 * thread, que é daemon, morre junto.
	 */
	private static void startExitGuard(Thread mainThread) {
		Thread guard = new Thread(() -> {
			try {
				mainThread.join();
				Thread.sleep(2000);
			}
			catch (InterruptedException e) {
				return;
			}
			CraftOffice.LOGGER.info("Thread nativa ainda presa ao sair. Encerrando a JVM");
			System.exit(0);
		}, "craftoffice-exit-guard");
		guard.setDaemon(true);
		guard.start();
	}

	private static void feedback(List<String> lines) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.player != null) {
				lines.forEach(line -> mc.player.sendSystemMessage(Component.literal(line)));
			}
		});
	}
}
