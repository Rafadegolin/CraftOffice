package dev.rafadegolin.craftoffice;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.rafadegolin.craftoffice.net.SignalPayload;

public class CraftOffice implements ModInitializer {
	public static final String MOD_ID = "craftoffice";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(SignalPayload.TYPE, SignalPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SignalPayload.TYPE, SignalPayload.CODEC);

		// Fase 0: repassa para qualquer player online. A regra de vizinhança
		// entra com o motor de proximidade na Fase 2.
		ServerPlayNetworking.registerGlobalReceiver(SignalPayload.TYPE, (payload, context) -> {
			ServerPlayer from = context.player();
			ServerPlayer to = context.server().getPlayerList().getPlayer(payload.peer());

			if (to == null || to == from || !ServerPlayNetworking.canSend(to, SignalPayload.TYPE)) {
				return;
			}

			ServerPlayNetworking.send(to, new SignalPayload(from.getUUID(), payload.kind(), payload.data()));
		});

		LOGGER.info("CraftOffice carregado");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
