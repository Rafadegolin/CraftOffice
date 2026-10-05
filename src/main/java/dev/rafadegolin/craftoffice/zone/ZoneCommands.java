package dev.rafadegolin.craftoffice.zone;

import java.util.function.Supplier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /officezone}: criar e apagar salas é só para operadores; trancar,
 * bater e atender é para quem está nelas. Raiz própria para não disputar o
 * {@code /office} do cliente.
 */
public final class ZoneCommands {
	private ZoneCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<ZoneManager> zones) {
		dispatcher.register(Commands.literal("officezone")
				.then(Commands.literal("create")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("name", StringArgumentType.string())
								.then(Commands.argument("from", BlockPosArgument.blockPos())
										.then(Commands.argument("to", BlockPosArgument.blockPos())
												.executes(ctx -> create(ctx, zones.get()))))))
				.then(Commands.literal("delete")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("name", StringArgumentType.string())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
										zones.get().store().names().stream().map(ZoneManager::quote), builder))
								.executes(ctx -> delete(ctx, zones.get()))))
				.then(Commands.literal("list").executes(ctx -> list(ctx, zones.get())))
				.then(Commands.literal("lock").executes(ctx -> reply(ctx,
						zones.get().lock(ctx.getSource().getPlayerOrException(), ctx.getSource().getServer().getPlayerList().getPlayers()),
						"Sala trancada. Quem chegar vai precisar bater.")))
				.then(Commands.literal("unlock").executes(ctx -> reply(ctx,
						zones.get().unlock(ctx.getSource().getPlayerOrException()), "Sala destrancada.")))
				.then(Commands.literal("knock")
						.then(Commands.argument("name", StringArgumentType.string())
								.executes(ctx -> reply(ctx, zones.get().knock(ctx.getSource().getPlayerOrException(),
										StringArgumentType.getString(ctx, "name"), ctx.getSource().getServer().getPlayerList().getPlayers()),
										"Você bateu na porta. Aguarde alguém abrir."))))
				.then(Commands.literal("accept")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> reply(ctx, zones.get().answer(ctx.getSource().getPlayerOrException(),
										EntityArgument.getPlayer(ctx, "player"), true), "Porta aberta."))))
				.then(Commands.literal("deny")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> reply(ctx, zones.get().answer(ctx.getSource().getPlayerOrException(),
										EntityArgument.getPlayer(ctx, "player"), false), "Pedido recusado.")))));
	}

	private static int create(CommandContext<CommandSourceStack> ctx, ZoneManager zones) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "name").trim();
		if (name.isEmpty() || name.length() > 32) {
			ctx.getSource().sendFailure(Component.literal("O nome precisa ter de 1 a 32 caracteres."));
			return 0;
		}
		BlockPos from = BlockPosArgument.getBlockPos(ctx, "from");
		BlockPos to = BlockPosArgument.getBlockPos(ctx, "to");
		Zone zone = Zone.of(name, ctx.getSource().getLevel().dimension().toString(),
				from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());

		Zone conflict = zones.store().create(zone);
		if (conflict != null) {
			ctx.getSource().sendFailure(Component.literal(conflict.name().equalsIgnoreCase(name)
					? "Já existe uma sala chamada " + conflict.name() + "."
					: "A sala encosta na sala " + conflict.name() + ". Salas não podem se sobrepor."));
			return 0;
		}
		zones.markDirty();
		int sizeX = zone.maxX() - zone.minX() + 1;
		int sizeY = zone.maxY() - zone.minY() + 1;
		int sizeZ = zone.maxZ() - zone.minZ() + 1;
		ctx.getSource().sendSuccess(() -> Component.literal("Sala " + name + " criada: " + sizeX + "x" + sizeY + "x" + sizeZ + " blocos."), true);
		return 1;
	}

	private static int delete(CommandContext<CommandSourceStack> ctx, ZoneManager zones) {
		String name = StringArgumentType.getString(ctx, "name");
		if (!zones.store().delete(name)) {
			ctx.getSource().sendFailure(Component.literal("Sala não encontrada: " + name));
			return 0;
		}
		zones.markDirty();
		ctx.getSource().sendSuccess(() -> Component.literal("Sala " + name + " apagada."), true);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx, ZoneManager zones) {
		if (zones.store().all().isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("Nenhuma sala. Operadores criam com /officezone create <nome> <canto> <canto>."), false);
			return 1;
		}
		for (Zone zone : zones.store().all()) {
			String text = String.format("%s%s: de %d %d %d a %d %d %d", zone.name(), zones.isLocked(zone) ? " (trancada)" : "",
					zone.minX(), zone.minY(), zone.minZ(), zone.maxX(), zone.maxY(), zone.maxZ());
			ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		}
		return 1;
	}

	/** {@code error} nulo é sucesso. */
	private static int reply(CommandContext<CommandSourceStack> ctx, String error, String success) {
		if (error != null) {
			ctx.getSource().sendFailure(Component.literal(error));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.literal(success), false);
		return 1;
	}

}
