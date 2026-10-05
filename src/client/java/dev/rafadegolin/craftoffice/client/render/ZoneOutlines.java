package dev.rafadegolin.craftoffice.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import dev.rafadegolin.craftoffice.client.ClientZones;
import dev.rafadegolin.craftoffice.client.OfficeSession;
import dev.rafadegolin.craftoffice.net.ZonesPayload.ZoneInfo;

/** Contorno das salas por perto: ciano aberta, vermelho trancada. */
public final class ZoneOutlines {
	private static final double VISIBLE_DISTANCE = 64;
	private static final int OPEN = 0xC000BCD4;
	private static final int LOCKED = 0xE0F44336;

	private ZoneOutlines() {
	}

	public static void collect(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !OfficeSession.serverHasMod() || ClientZones.zones().isEmpty()) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		String dimension = ClientZones.dimension(mc);

		for (ZoneInfo zone : ClientZones.zones()) {
			if (!zone.dimension().equals(dimension) || !near(zone, camera.pos)) {
				continue;
			}
			draw(context, camera, zone);
		}
	}

	private static boolean near(ZoneInfo zone, Vec3 pos) {
		double dx = Math.max(Math.max(zone.minX() - pos.x, 0), pos.x - (zone.maxX() + 1));
		double dz = Math.max(Math.max(zone.minZ() - pos.z, 0), pos.z - (zone.maxZ() + 1));
		return dx * dx + dz * dz < VISIBLE_DISTANCE * VISIBLE_DISTANCE;
	}

	private static void draw(LevelRenderContext context, CameraRenderState camera, ZoneInfo zone) {
		float x0 = (float) (zone.minX() - camera.pos.x);
		float y0 = (float) (zone.minY() - camera.pos.y);
		float z0 = (float) (zone.minZ() - camera.pos.z);
		float x1 = (float) (zone.maxX() + 1 - camera.pos.x);
		float y1 = (float) (zone.maxY() + 1 - camera.pos.y);
		float z1 = (float) (zone.maxZ() + 1 - camera.pos.z);
		int color = zone.locked() ? LOCKED : OPEN;

		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, lines) -> {
			// Base e teto.
			for (float y : new float[] {y0, y1}) {
				line(pose, lines, x0, y, z0, x1, y, z0, color);
				line(pose, lines, x1, y, z0, x1, y, z1, color);
				line(pose, lines, x1, y, z1, x0, y, z1, color);
				line(pose, lines, x0, y, z1, x0, y, z0, color);
			}
			// Quinas.
			line(pose, lines, x0, y0, z0, x0, y1, z0, color);
			line(pose, lines, x1, y0, z0, x1, y1, z0, color);
			line(pose, lines, x1, y0, z1, x1, y1, z1, color);
			line(pose, lines, x0, y0, z1, x0, y1, z1, color);
		});
	}

	private static void line(PoseStack.Pose pose, VertexConsumer lines, float ax, float ay, float az, float bx, float by, float bz, int color) {
		float nx = bx - ax;
		float ny = by - ay;
		float nz = bz - az;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		nx /= length;
		ny /= length;
		nz /= length;
		lines.addVertex(pose, ax, ay, az).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2f);
		lines.addVertex(pose, bx, by, bz).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(2f);
	}
}
