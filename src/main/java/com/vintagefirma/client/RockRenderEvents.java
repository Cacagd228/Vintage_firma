package com.vintagefirma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vintagefirma.VintageFirma;
import com.vintagefirma.common.RockHandler;
import com.vintagefirma.common.RockVoxel;
import com.vintagefirma.smithing.Voxels;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the stone voxels of every nearby knapping site, plus the target pattern and the hovered voxel
 * while the player holds a loose rock. The blank rests directly on top of the support block (offset 0).
 */
@Mod.EventBusSubscriber(modid = VintageFirma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RockRenderEvents
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(VintageFirma.MOD_ID,
        "textures/block/voxel_metal.png");

    private static final RenderType VOXEL_TYPE = RenderType.entitySolid(TEXTURE);

    private static final float MAX_DISTANCE_SQR = 64 * 64;

    private static final float TILE = 0.125f;

    /** Grey base colour for stone. */
    private static final int STONE_COLOR = 0x8E8E8E;

    private RockRenderEvents()
    {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        final Player player = minecraft.player;
        if (level == null || player == null || ClientRockCache.map().isEmpty()) return;

        final PoseStack poseStack = event.getPoseStack();
        final Vec3 camera = event.getCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        final boolean holdingRock = RockHandler.isLooseRock(player.getMainHandItem());
        final ClientTargeting.RockTarget hover = holdingRock ? ClientTargeting.findRockTarget(player) : null;

        final List<BlockPos> invalid = new ArrayList<>();

        final VertexConsumer buffer = buffers.getBuffer(VOXEL_TYPE);
        for (Map.Entry<BlockPos, ClientRockCache.RockWork> entry : ClientRockCache.map().entrySet())
        {
            final BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos))
            {
                invalid.add(pos);
                continue;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) continue;

            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            drawVoxels(poseStack, buffer, entry.getValue(), LevelRenderer.getLightColor(level, pos));
            poseStack.popPose();
        }
        buffers.endBatch(VOXEL_TYPE);

        if (holdingRock)
        {
            final VertexConsumer lineBuffer = buffers.getBuffer(RenderType.lines());
            for (Map.Entry<BlockPos, ClientRockCache.RockWork> entry : ClientRockCache.map().entrySet())
            {
                final BlockPos pos = entry.getKey();
                if (!level.isLoaded(pos)) continue;
                if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) continue;

                poseStack.pushPose();
                poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                drawTarget(poseStack, lineBuffer, entry.getValue());

                if (hover != null && hover.pos().equals(pos))
                {
                    LevelRenderer.renderLineBox(poseStack, lineBuffer, localBox(hover.x(), 0, hover.z()),
                        1f, 1f, 1f, 1f);
                }
                poseStack.popPose();
            }
            buffers.endBatch(RenderType.lines());
        }

        invalid.forEach(ClientRockCache::remove);
    }

    private static void drawVoxels(PoseStack poseStack, VertexConsumer buffer, ClientRockCache.RockWork work,
        int light)
    {
        final Matrix4f matrix = poseStack.last().pose();
        final byte[] voxels = work.voxels();

        for (int y = 0; y < Voxels.SIZE_Y; y++)
        {
            for (int z = 0; z < Voxels.SIZE_Z; z++)
            {
                for (int x = 0; x < Voxels.SIZE_X; x++)
                {
                    if (voxels[Voxels.index(x, y, z)] == Voxels.EMPTY) continue;

                    final int hash = hash(x, y, z);
                    final int tint = shade(STONE_COLOR, 0.92f + ((hash >>> 5) & 15) * 0.011f);

                    final float u0 = ((hash >>> 8) & 7) * TILE;
                    final float v0 = ((hash >>> 5) & 7) * TILE;
                    final float u1 = u0 + TILE;
                    final float v1 = v0 + TILE;

                    final AABB box = RockVoxel.localBox(x, y, z);
                    final float x0 = (float) box.minX;
                    final float y0 = (float) box.minY;
                    final float z0 = (float) box.minZ;
                    final float x1 = (float) box.maxX;
                    final float y1 = (float) box.maxY;
                    final float z1 = (float) box.maxZ;

                    if (!filled(voxels, x, y + 1, z))
                    {
                        quad(buffer, matrix, light, tint, u0, v0, u1, v1,
                            x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
                    }
                    if (y > 0 && !filled(voxels, x, y - 1, z))
                    {
                        quad(buffer, matrix, light, shade(tint, 0.5f), u0, v0, u1, v1,
                            x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
                    }
                    if (!filled(voxels, x, y, z - 1))
                    {
                        quad(buffer, matrix, light, shade(tint, 0.8f), u0, v0, u1, v1,
                            x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
                    }
                    if (!filled(voxels, x, y, z + 1))
                    {
                        quad(buffer, matrix, light, shade(tint, 0.8f), u0, v0, u1, v1,
                            x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
                    }
                    if (!filled(voxels, x - 1, y, z))
                    {
                        quad(buffer, matrix, light, shade(tint, 0.65f), u0, v0, u1, v1,
                            x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
                    }
                    if (!filled(voxels, x + 1, y, z))
                    {
                        quad(buffer, matrix, light, shade(tint, 0.65f), u0, v0, u1, v1,
                            x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
                    }
                }
            }
        }
    }

    private static void drawTarget(PoseStack poseStack, VertexConsumer buffer, ClientRockCache.RockWork work)
    {
        final byte[] pattern = work.pattern();
        if (pattern == null) return;

        final byte[] voxels = work.voxels();
        for (int y = 0; y < Voxels.SIZE_Y; y++)
        {
            for (int z = 0; z < Voxels.SIZE_Z; z++)
            {
                for (int x = 0; x < Voxels.SIZE_X; x++)
                {
                    final int index = Voxels.index(x, y, z);
                    final boolean filled = voxels[index] != Voxels.EMPTY;
                    final boolean required = pattern[index] != Voxels.EMPTY;

                    // Subtractive: only show voxels that must be removed (orange). Missing (green) cells
                    // cannot be added while knapping, so there is no green.
                    if (!required && filled)
                    {
                        LevelRenderer.renderLineBox(poseStack, buffer, localBox(x, y, z), 1f, 0.35f, 0.2f, 0.9f);
                    }
                }
            }
        }
    }

    private static AABB localBox(int x, int y, int z)
    {
        return RockVoxel.localBox(x, y, z);
    }

    private static boolean filled(byte[] voxels, int x, int y, int z)
    {
        return Voxels.inBounds(x, y, z) && voxels[Voxels.index(x, y, z)] != Voxels.EMPTY;
    }

    private static int hash(int x, int y, int z)
    {
        int h = x * 374761393 + y * 668265263 + z * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static int shade(int color, float factor)
    {
        final int r = clamp(Math.round(((color >> 16) & 0xFF) * factor));
        final int g = clamp(Math.round(((color >> 8) & 0xFF) * factor));
        final int b = clamp(Math.round((color & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(255, value));
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, int light, int color,
        float u0, float v0, float u1, float v1,
        float ax, float ay, float az, float bx, float by, float bz,
        float cx, float cy, float cz, float dx, float dy, float dz)
    {
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;

        vertex(buffer, matrix, ax, ay, az, r, g, b, u0, v0, light);
        vertex(buffer, matrix, bx, by, bz, r, g, b, u0, v1, light);
        vertex(buffer, matrix, cx, cy, cz, r, g, b, u1, v1, light);
        vertex(buffer, matrix, dx, dy, dz, r, g, b, u1, v0, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z,
        int r, int g, int b, float u, float v, int light)
    {
        buffer.vertex(matrix, x, y, z)
            .color(r, g, b, 255)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(0f, 1f, 0f)
            .endVertex();
    }
}