package com.vintagefirma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vintagefirma.VintageFirma;
import com.vintagefirma.common.ClayHandler;
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
 * Draws the clay voxels of every nearby clay piece (they rest on top of an ordinary block, one block
 * higher than the smithing voxels), plus the target pattern and the hovered voxel while the player holds
 * clay.
 *
 * <p>Deliberately independent from {@link VoxelRenderEvents}: clay has no heat and uses a warmer colour,
 * so it gets its own pass instead of complicating the (working) hot metal renderer.
 */
@Mod.EventBusSubscriber(modid = VintageFirma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClayRenderEvents
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(VintageFirma.MOD_ID,
        "textures/block/voxel_clay.png");

    private static final RenderType VOXEL_TYPE = RenderType.entitySolid(TEXTURE);

    private static final float PX = 1f / 16f;
    /** Clay sits on top of a full block, so the grid starts a whole block above the block position. */
    private static final float Y_OFFSET = 1.0f;
    private static final float MAX_DISTANCE_SQR = 64 * 64;

    private static final float TILE = 0.125f;

    /** Grey base colour for unfired clay (wet clay is grey, like in Vintage Story). */
    private static final int CLAY_COLOR = 0x9A9A99;

    private ClayRenderEvents()
    {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        final Player player = minecraft.player;
        if (level == null || player == null || ClientClayCache.map().isEmpty()) return;

        final PoseStack poseStack = event.getPoseStack();
        final Vec3 camera = event.getCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        final boolean holdingClay = ClayHandler.isClay(player.getMainHandItem())
            || ClayHandler.isClay(player.getOffhandItem());
        final ClientTargeting.ClayColumn hover = holdingClay ? ClientTargeting.findClayColumn(player) : null;

        final List<BlockPos> invalid = new ArrayList<>();

        final VertexConsumer buffer = buffers.getBuffer(VOXEL_TYPE);
        for (Map.Entry<BlockPos, ClientClayCache.ClayWork> entry : ClientClayCache.map().entrySet())
        {
            final BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos) || level.getBlockState(pos).isAir())
            {
                invalid.add(pos);
                continue;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) continue;

            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            // Clay is a build guide: draw it at full brightness so it reads as grey even in the dark.
            drawVoxels(poseStack, buffer, entry.getValue(), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
        buffers.endBatch(VOXEL_TYPE);

        if (holdingClay)
        {
            final VertexConsumer lineBuffer = buffers.getBuffer(RenderType.lines());
            for (Map.Entry<BlockPos, ClientClayCache.ClayWork> entry : ClientClayCache.map().entrySet())
            {
                final BlockPos pos = entry.getKey();
                if (!level.isLoaded(pos) || level.getBlockState(pos).isAir()) continue;
                if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) continue;

                poseStack.pushPose();
                poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                drawTarget(poseStack, lineBuffer, entry.getValue());

                if (hover != null && hover.pos().equals(pos))
                {
                    final byte[] pattern = entry.getValue().pattern();
                    if (pattern != null)
                    {
                        final int layer = com.vintagefirma.common.ClayWork.currentLayer(
                            entry.getValue().voxels(), pattern);
                        if (layer < Voxels.SIZE_Y)
                        {
                            LevelRenderer.renderLineBox(poseStack, lineBuffer,
                                localBox(hover.x(), layer, hover.z()), 1f, 1f, 1f, 1f);
                        }
                    }
                }
                poseStack.popPose();
            }
            buffers.endBatch(RenderType.lines());
        }

        invalid.forEach(ClientClayCache::remove);
    }

    private static void drawVoxels(PoseStack poseStack, VertexConsumer buffer, ClientClayCache.ClayWork work,
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
                    final int tint = shade(CLAY_COLOR, 0.92f + ((hash >>> 5) & 15) * 0.011f);

                    final float u0 = ((hash >>> 8) & 7) * TILE;
                    final float v0 = ((hash >>> 5) & 7) * TILE;
                    final float u1 = u0 + TILE;
                    final float v1 = v0 + TILE;

                    final float x0 = x * PX;
                    final float y0 = Y_OFFSET + y * PX;
                    final float z0 = z * PX;
                    final float x1 = x0 + PX;
                    final float y1 = y0 + PX;
                    final float z1 = z0 + PX;

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

    private static void drawTarget(PoseStack poseStack, VertexConsumer buffer, ClientClayCache.ClayWork work)
    {
        final byte[] pattern = work.pattern();
        if (pattern == null) return;

        final byte[] voxels = work.voxels();

        // Only the current (lowest unfinished) layer is shown, so the player works strictly layer by layer.
        final int layer = com.vintagefirma.common.ClayWork.currentLayer(voxels, pattern);
        if (layer >= Voxels.SIZE_Y) return; // complete

        for (int z = 0; z < Voxels.SIZE_Z; z++)
        {
            for (int x = 0; x < Voxels.SIZE_X; x++)
            {
                final int index = Voxels.index(x, layer, z);
                final boolean filled = voxels[index] != Voxels.EMPTY;
                final boolean required = pattern[index] != Voxels.EMPTY;

                if (required && !filled)
                {
                    LevelRenderer.renderLineBox(poseStack, buffer, localBox(x, layer, z), 0.3f, 1f, 0.4f, 0.9f);
                }
                else if (!required && filled)
                {
                    LevelRenderer.renderLineBox(poseStack, buffer, localBox(x, layer, z), 1f, 0.25f, 0.2f, 0.9f);
                }
            }
        }
    }

    private static AABB localBox(int x, int y, int z)
    {
        final float x0 = x * PX;
        final float y0 = Y_OFFSET + y * PX;
        final float z0 = z * PX;
        return new AABB(x0, y0, z0, x0 + PX, y0 + PX, z0 + PX);
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