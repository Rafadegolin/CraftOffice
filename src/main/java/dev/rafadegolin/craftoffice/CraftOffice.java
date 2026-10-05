package dev.rafadegolin.craftoffice;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.rafadegolin.craftoffice.net.ConfigPayload;
import dev.rafadegolin.craftoffice.net.HelloPayload;
import dev.rafadegolin.craftoffice.net.PeerPayload;
import dev.rafadegolin.craftoffice.net.PeerStatePayload;
import dev.rafadegolin.craftoffice.net.SignalPayload;
import dev.rafadegolin.craftoffice.net.StatePayload;
import dev.rafadegolin.craftoffice.net.StatusesPayload;
import dev.rafadegolin.craftoffice.net.ZonesPayload;
import dev.rafadegolin.craftoffice.server.OfficeServer;

public class CraftOffice implements ModInitializer {
	public static final String MOD_ID = "craftoffice";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Versão dos avisos entre cliente e servidor. Sobe a cada mudança incompatível. */
	public static final int PROTOCOL = 6;

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PeerPayload.TYPE, PeerPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PeerStatePayload.TYPE, PeerStatePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ZonesPayload.TYPE, ZonesPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(StatusesPayload.TYPE, StatusesPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SignalPayload.TYPE, SignalPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SignalPayload.TYPE, SignalPayload.CODEC);

		OfficeBlocks.register();
		OfficeServer.register();

		LOGGER.info("CraftOffice carregado");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
