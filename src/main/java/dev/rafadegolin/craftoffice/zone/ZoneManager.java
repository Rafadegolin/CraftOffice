package dev.rafadegolin.craftoffice.zone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import dev.rafadegolin.craftoffice.net.ZonesPayload;

/**
 * Zonas em funcionamento: quem está em qual, trancas e batidas na porta.
 * Vale para todos os players, com ou sem o mod. Thread do servidor.
 */
public final class ZoneManager {
	/** Uma batida vale por um minuto. */
	private static final long KNOCK_TTL_MS = 60_000;

	private final ZoneStore store;
	/** Zonas trancadas e quem pode estar dentro, pelo nome da zona em minúsculas. */
	private final Map<String, Set<UUID>> locked = new HashMap<>();
	/** Batidas pendentes: zona, quem bateu e quando. */
	private final Map<String, Map<UUID, Long>> knocks = new HashMap<>();
	/** Última posição fora de zona trancada, para onde volta quem tenta entrar sem permissão. */
	private final Map<UUID, Vec3> lastAllowed = new HashMap<>();
	/** Zona atual de cada player, pelo nome. */
	private final Map<UUID, String> current = new HashMap<>();
	/** Quando cada player recebeu o aviso de sala trancada pela última vez. */
	private final Map<UUID, Long> warnedAt = new HashMap<>();
	private boolean dirty = true;

	public ZoneManager(MinecraftServer server) {
		this.store = new ZoneStore(server.getWorldPath(LevelResource.ROOT));
	}

	public ZoneStore store() {
		return store;
	}

	private static String key(String name) {
		return name.toLowerCase();
	}

	public static String dimensionOf(ServerPlayer player) {
		return player.level().dimension().toString();
	}

	/** A zona onde o player está, pelo último tick. */
	public Zone zoneOf(ServerPlayer player) {
		String name = current.get(player.getUUID());
		return name != null ? store.get(name) : null;
	}

	public boolean isLocked(Zone zone) {
		return locked.containsKey(key(zone.name()));
	}

	/** Mudou zona ou tranca: o aviso de zonas precisa sair de novo. */
	public boolean consumeDirty() {
		boolean was = dirty;
		dirty = false;
		return was;
	}

	public void markDirty() {
		dirty = true;
	}

	/** Atualiza quem está em qual zona e barra quem não pode entrar. */
	public void tick(List<ServerPlayer> players) {
		long now = System.currentTimeMillis();
		knocks.values().forEach(map -> map.values().removeIf(time -> now - time > KNOCK_TTL_MS));

		for (ServerPlayer player : players) {
			UUID id = player.getUUID();
			Zone zone = store.at(dimensionOf(player), player.getX(), player.getY(), player.getZ());
			Set<UUID> allowed = zone != null ? locked.get(key(zone.name())) : null;

			if (allowed != null && !allowed.contains(id) && !player.isSpectator()) {
				pushBack(player, zone);
				continue;
			}

			String previous = current.get(id);
			String zoneNow = zone != null ? zone.name() : null;
			if (previous != null && !previous.equalsIgnoreCase(zoneNow == null ? "" : zoneNow)) {
				// Saiu de uma sala trancada: para voltar, bate de novo.
				Set<UUID> previousAllowed = locked.get(key(previous));
				if (previousAllowed != null) {
					previousAllowed.remove(id);
				}
			}
			if (zoneNow != null) {
				current.put(id, zoneNow);
			}
			else {
				current.remove(id);
			}
			lastAllowed.put(id, player.position());
		}

		// Sala trancada sem ninguém dentro destranca sozinha.
		Set<String> occupied = new HashSet<>();
		current.values().forEach(name -> occupied.add(key(name)));
		if (locked.keySet().removeIf(zone -> !occupied.contains(zone))) {
			dirty = true;
		}
	}

	private void pushBack(ServerPlayer player, Zone zone) {
		Vec3 back = lastAllowed.get(player.getUUID());
		if (back == null || zone.contains(dimensionOf(player), back.x, back.y, back.z)) {
			// Sem posição conhecida fora: empurra para fora pelo lado mais perto.
			back = nearestOutside(zone, player.position());
		}
		player.connection.teleport(back.x, back.y, back.z, player.getYRot(), player.getXRot());

		// Encostado na porta, o aviso sai no máximo a cada 5 segundos.
		long now = System.currentTimeMillis();
		Long last = warnedAt.get(player.getUUID());
		if (last == null || now - last > 5000) {
			warnedAt.put(player.getUUID(), now);
			player.sendSystemMessage(Component.literal("A sala " + zone.name() + " está trancada. ")
					.withStyle(ChatFormatting.GOLD)
					.append(button("[Bater na porta]", "/officezone knock " + quote(zone.name()), "Pede para entrar")));
		}
	}

	private static Vec3 nearestOutside(Zone zone, Vec3 pos) {
		double toMinX = pos.x - zone.minX();
		double toMaxX = zone.maxX() + 1 - pos.x;
		double toMinZ = pos.z - zone.minZ();
		double toMaxZ = zone.maxZ() + 1 - pos.z;
		double min = Math.min(Math.min(toMinX, toMaxX), Math.min(toMinZ, toMaxZ));
		if (min == toMinX) {
			return new Vec3(zone.minX() - 0.7, pos.y, pos.z);
		}
		if (min == toMaxX) {
			return new Vec3(zone.maxX() + 1.7, pos.y, pos.z);
		}
		if (min == toMinZ) {
			return new Vec3(pos.x, pos.y, zone.minZ() - 0.7);
		}
		return new Vec3(pos.x, pos.y, zone.maxZ() + 1.7);
	}

	/** Tranca a zona onde o player está. Quem está dentro pode ficar. */
	public String lock(ServerPlayer player, List<ServerPlayer> online) {
		Zone zone = zoneOf(player);
		if (zone == null) {
			return "Você precisa estar dentro de uma sala para trancar.";
		}
		Set<UUID> inside = new HashSet<>();
		for (ServerPlayer other : online) {
			if (zone.name().equalsIgnoreCase(current.get(other.getUUID()))) {
				inside.add(other.getUUID());
			}
		}
		locked.put(key(zone.name()), inside);
		knocks.remove(key(zone.name()));
		dirty = true;
		return null;
	}

	public String unlock(ServerPlayer player) {
		Zone zone = zoneOf(player);
		if (zone == null || locked.remove(key(zone.name())) == null) {
			return "Você não está numa sala trancada.";
		}
		knocks.remove(key(zone.name()));
		dirty = true;
		return null;
	}

	/** Bate na porta: avisa quem está dentro, com botões de aceitar e recusar. */
	public String knock(ServerPlayer player, String zoneName, List<ServerPlayer> online) {
		Zone zone = store.get(zoneName);
		if (zone == null) {
			return "Sala não encontrada: " + zoneName;
		}
		if (!isLocked(zone)) {
			return "A sala " + zone.name() + " não está trancada. Pode entrar.";
		}
		knocks.computeIfAbsent(key(zone.name()), k -> new HashMap<>()).put(player.getUUID(), System.currentTimeMillis());

		String name = player.getScoreboardName();
		MutableComponent message = Component.literal(name + " está batendo na porta. ").withStyle(ChatFormatting.AQUA)
				.append(button("[Aceitar]", "/officezone accept " + name, "Deixa entrar"))
				.append(" ")
				.append(button("[Recusar]", "/officezone deny " + name, "Não deixa entrar"));
		int notified = 0;
		for (ServerPlayer other : online) {
			if (zone.name().equalsIgnoreCase(current.get(other.getUUID()))) {
				other.sendSystemMessage(message);
				notified++;
			}
		}
		return notified == 0 ? "Ninguém dentro para atender." : null;
	}

	/** Quem está dentro da sala trancada aceita ou recusa uma batida. */
	public String answer(ServerPlayer insider, ServerPlayer knocker, boolean accept) {
		Zone zone = zoneOf(insider);
		if (zone == null || !isLocked(zone)) {
			return "Você não está numa sala trancada.";
		}
		Map<UUID, Long> pending = knocks.get(key(zone.name()));
		if (pending == null || pending.remove(knocker.getUUID()) == null) {
			return knocker.getScoreboardName() + " não bateu nesta sala.";
		}
		if (accept) {
			locked.get(key(zone.name())).add(knocker.getUUID());
			knocker.sendSystemMessage(Component.literal(insider.getScoreboardName() + " abriu a porta da sala "
					+ zone.name() + ". Pode entrar.").withStyle(ChatFormatting.GREEN));
		}
		else {
			knocker.sendSystemMessage(Component.literal("Não deixaram você entrar na sala " + zone.name() + " agora.")
					.withStyle(ChatFormatting.GRAY));
		}
		return null;
	}

	public void forget(UUID player) {
		current.remove(player);
		lastAllowed.remove(player);
		warnedAt.remove(player);
		locked.values().forEach(set -> set.remove(player));
		knocks.values().forEach(map -> map.remove(player));
	}

	public ZonesPayload payload() {
		return new ZonesPayload(store.all().stream()
				.map(zone -> new ZonesPayload.ZoneInfo(zone.name(), zone.dimension(), zone.minX(), zone.minY(), zone.minZ(),
						zone.maxX(), zone.maxY(), zone.maxZ(), isLocked(zone)))
				.toList());
	}

	static String quote(String name) {
		return name.matches("[A-Za-z0-9_.+-]+") ? name : "\"" + name.replace("\"", "") + "\"";
	}

	private static MutableComponent button(String label, String command, String hover) {
		return Component.literal(label).withStyle(style -> style
				.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}
}
