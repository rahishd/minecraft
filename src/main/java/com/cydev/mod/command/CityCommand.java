package com.cydev.mod.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.cydev.mod.sim.city.City;
import com.cydev.mod.world.persistence.CityData;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

/** /cydev city create <name> and /cydev city list (temporary dev commands). */
public final class CityCommand {
	private CityCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(literal("cydev").then(literal("city")
				.then(literal("create").requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
					.then(argument("name", StringArgumentType.word()).executes(ctx -> {
						String name = StringArgumentType.getString(ctx, "name");
						CityData data = CityData.get(ctx.getSource().getServer());
						City city = data.registry().create(name);
						data.markDirty();
						ctx.getSource().sendFeedback(() -> Text.literal("Created city " + city.name), true);
						return 1;
					})))
				.then(literal("list").executes(ctx -> {
					CityData data = CityData.get(ctx.getSource().getServer());
					if (data.registry().all().isEmpty()) {
						ctx.getSource().sendFeedback(() -> Text.literal("No cities yet."), false);
					}
					data.registry().all().values().forEach(c -> ctx.getSource().sendFeedback(
						() -> Text.literal(c.name + " (treasury " + c.treasury + ")"), false));
					return 1;
				})))));
	}
}
