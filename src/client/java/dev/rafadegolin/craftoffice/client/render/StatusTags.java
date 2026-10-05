package dev.rafadegolin.craftoffice.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.rafadegolin.craftoffice.OfficeStatus;
import dev.rafadegolin.craftoffice.client.OfficeSession;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.media.PeerSession;

/** Status de quem não está disponível, acima do nome. Vale para todos os players com o mod por perto. */
public final class StatusTags {
	private static final double VISIBLE_DISTANCE = 32;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float SCALE = 0.025f;
	/** Logo acima da plaquinha de nome. */
	private static final double ABOVE_NAME = 0.8;

	private StatusTags() {
	}

	public static void collect(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !OfficeSession.serverHasMod()) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		float partial = camera.cameraEntityPartialTicks;
		MediaEngine engine = MediaEngine.getIfLoaded();

		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			OfficeStatus status = self ? OfficeSession.status() : OfficeSession.statusOf(player.getUUID());
			if (status == OfficeStatus.AVAILABLE || player.isInvisible()
					|| self && mc.options.getCameraType().isFirstPerson()) {
				continue;
			}
			Vec3 pos = player.getPosition(partial);
			if (camera.pos.distanceToSqr(pos) > VISIBLE_DISTANCE * VISIBLE_DISTANCE) {
				continue;
			}

			boolean video = self ? engine != null && engine.videoOn() : hasVideo(engine, player);
			double height = video ? VideoBillboards.topOffset(camera, pos) + 0.15 : ABOVE_NAME;
			draw(context, camera, mc.font, pos.add(0, player.getBbHeight() + height, 0), status);
		}
	}

	private static boolean hasVideo(MediaEngine engine, Player player) {
		if (engine == null) {
			return false;
		}
		PeerSession session = engine.sessions().get(player.getUUID());
		return session != null && VideoBillboards.showsVideo(session);
	}

	private static void draw(LevelRenderContext context, CameraRenderState camera, Font font, Vec3 at, OfficeStatus status) {
		FormattedCharSequence text = Component.literal("● ").append(Component.translatable(status.translationKey())).getVisualOrderText();
		float x = -font.width(text) / 2f;

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(at.x - camera.pos.x, at.y - camera.pos.y, at.z - camera.pos.z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(SCALE, -SCALE, SCALE);
		context.submitNodeCollector().submitText(poseStack, x, 0, text, false, Font.DisplayMode.NORMAL,
				FULL_BRIGHT, status.color(), 0x60000000, 0);
		poseStack.popPose();
	}
}
