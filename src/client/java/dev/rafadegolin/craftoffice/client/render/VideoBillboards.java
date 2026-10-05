package dev.rafadegolin.craftoffice.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.rafadegolin.craftoffice.client.OfficeSession;
import dev.rafadegolin.craftoffice.client.media.FrameSlot;
import dev.rafadegolin.craftoffice.client.media.MediaEngine;
import dev.rafadegolin.craftoffice.client.media.PeerSession;

/**
 * O rosto de cada vizinho acima da cabeça, sempre virado para quem olha. Usa o
 * tipo de render dos textos do mundo com luz máxima, então o vídeo fica claro
 * à noite e não precisa de shader próprio.
 */
public final class VideoBillboards {
	/** Luz máxima de céu e de bloco. */
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float WIDTH = 0.8f;
	private static final float HEIGHT = WIDTH * FrameSlot.HEIGHT / FrameSlot.WIDTH;
	/** Acima da plaquinha de nome. */
	private static final double ABOVE_HEAD = 0.75;

	private VideoBillboards() {
	}

	public static void collect(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		MediaEngine engine = MediaEngine.getIfLoaded();
		if (engine == null || mc.level == null || mc.player == null || !OfficeSession.active()) {
			return;
		}

		VideoTextures.update();
		CameraRenderState camera = context.levelState().cameraRenderState;
		float partial = camera.cameraEntityPartialTicks;

		for (PeerSession session : engine.sessions().values()) {
			VideoTexture texture = VideoTextures.remote(session.peer());
			Player other = mc.level.getPlayerByUUID(session.peer());
			if (texture == null || other == null || other.isInvisible() || !showsVideo(session)) {
				continue;
			}
			draw(context, camera, other, texture, partial);
		}

		// Em terceira pessoa, o próprio rosto também aparece, como os outros veem.
		VideoTexture self = VideoTextures.self();
		if (engine.videoOn() && self != null && !mc.options.getCameraType().isFirstPerson()) {
			draw(context, camera, mc.player, self, partial);
		}
	}

	/** Câmera ligada do outro lado, vídeo permitido pelo limite do servidor e frames chegando. */
	public static boolean showsVideo(PeerSession session) {
		return OfficeSession.peerCameraOn(session.peer())
				&& OfficeSession.videoAllowed(session.peer())
				&& session.remoteSlot().fps() > 0;
	}

	private static void draw(LevelRenderContext context, CameraRenderState camera, Player player, VideoTexture texture, float partial) {
		Vec3 pos = player.getPosition(partial);
		double distance = camera.pos.distanceTo(pos);
		// Cresce um pouco com a distância, para continuar legível de longe.
		float scale = (float) Math.clamp(1 + (distance - 3) * 0.06, 1, 1.4);
		float halfWidth = WIDTH * scale / 2;
		float height = HEIGHT * scale;

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(pos.x - camera.pos.x, pos.y + player.getBbHeight() + ABOVE_HEAD - camera.pos.y, pos.z - camera.pos.z);
		poseStack.rotate(camera.orientation);

		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.text(texture.id()), (pose, vertices) -> {
			vertices.addVertex(pose, -halfWidth, 0, 0).setColor(0xFFFFFFFF).setUv(0, 1).setLight(FULL_BRIGHT);
			vertices.addVertex(pose, halfWidth, 0, 0).setColor(0xFFFFFFFF).setUv(1, 1).setLight(FULL_BRIGHT);
			vertices.addVertex(pose, halfWidth, height, 0).setColor(0xFFFFFFFF).setUv(1, 0).setLight(FULL_BRIGHT);
			vertices.addVertex(pose, -halfWidth, height, 0).setColor(0xFFFFFFFF).setUv(0, 0).setLight(FULL_BRIGHT);
		});
		poseStack.popPose();
	}
}
