package com.example.cord;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class CordClient implements ClientModInitializer {

	private static final Identifier PIN_ICON = new Identifier("cord", "textures/hud/pin.png");
	private static final Identifier CLOCK_ICON = new Identifier("cord", "textures/hud/clock.png");

	@Override
	public void onInitializeClient() {
		HudRenderCallback.EVENT.register(this::renderHud);
	}

	private void renderHud(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null || client.options.hudHidden) {
			return;
		}

		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();

		// baris ini diposisikan relatif ke hotbar (persis di atas baris health/hunger),
		// biar tetep pas di resolusi & GUI scale berapa pun.
		int rowY = screenHeight - 57;

		BlockPos pos = client.player.getBlockPos();
		long day = (client.world.getTimeOfDay() / 24000L) + 1;

		// --- Baris koordinat, nempel di sisi kiri hotbar ---
		int coordX = screenWidth / 2 - 91;
		context.drawTexture(PIN_ICON, coordX, rowY + 1, 0, 0, 8, 10, 8, 10);

		Text coordText = Text.literal("X: ").formatted(Formatting.WHITE)
				.append(Text.literal(String.valueOf(pos.getX())).formatted(Formatting.GREEN))
				.append(Text.literal(" Y: ").formatted(Formatting.WHITE))
				.append(Text.literal(String.valueOf(pos.getY())).formatted(Formatting.GREEN))
				.append(Text.literal(" Z: ").formatted(Formatting.WHITE))
				.append(Text.literal(String.valueOf(pos.getZ())).formatted(Formatting.GREEN));

		context.drawTextWithShadow(client.textRenderer, coordText, coordX + 12, rowY, 0xFFFFFF);

		// --- Baris "Day", diposisikan proporsional ke kanan dari tengah hotbar ---
		int dayX = screenWidth / 2 + 41;
		context.drawTexture(CLOCK_ICON, dayX, rowY + 1, 0, 0, 9, 10, 9, 10);

		Text dayText = Text.literal("Day ").formatted(Formatting.AQUA)
				.append(Text.literal(String.valueOf(day)).formatted(Formatting.AQUA));

		context.drawTextWithShadow(client.textRenderer, dayText, dayX + 13, rowY, 0x55FFFF);
	}
}
