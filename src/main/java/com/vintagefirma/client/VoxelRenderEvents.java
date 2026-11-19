package com.vintagefirma.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vintagefirma.VintageFirma;
import com.vintagefirma.common.AnvilHandler;
import com.vintagefirma.common.VoxelRaycast;
import com.vintagefirma.smithing.Voxels;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.dries007.tfc.common.TFCTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the work item voxels of every nearby anvil, plus the target pattern and the hovered voxel.
 *
 * <p>The hot metal glow is a faithful port of Vintage Story's smithing shader: the incandescence colour
 * is {@code r = (T - 500) / 400}, {@code g = (T - 900) / 200}, {@code b = (T - 1100) / 200} with an
 * additive intensity of {@code (T - 550) / 2}. In Minecraft the additive part is drawn as a second pass
 * with {@link RenderType#lightning()} (additive blending, no texture), so the metal actually shines.
 */
@Mod.EventBusSubscriber(modid = VintageFirma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VoxelRenderEvents
{
    private static final ResourceLocation TEXTURE = new ResourceLocation(VintageFirma.MOD_ID,
        "textures/block/voxel_metal.png");

    /**
     * {@code entitySolid} is the only entity render type that requests mipmaps. Without them a minified
     * texture on a 1/16 block face turns into moire stripes instead of a solid cube.
     */
    private static final RenderType VOXEL_TYPE = RenderType.entitySolid(TEXTURE);
    /** Additive, texture-less glow. */
    private static final RenderType GLOW_TYPE = RenderType.lightning();

    private static final float PX = 1f / 16f;
    private static final float Y_OFFSET = VoxelRaycast.VOXEL_Y_OFFSET;
    private static final float MAX_DISTANCE_SQR = 64 * 64;

    /** A voxel face samples a 2x2 pixel tile of the 16x16 texture, so each voxel face is 2x2 texels. */
    private static final float TILE = 0.125f;

    private static final int METAL_COLOR = 0xB4B4B4;

    private VoxelRenderEvents()
    {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        final Player player = minecraft.player;
        if (level == null || player == null || ClientWorkCache.map().isEmpty()) return;

        final PoseStack poseStack = event.getPoseStack();
        final Vec3 camera = event.getCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        final boolean holdingHammer = isHoldingHammer(player);
        final ClientTargeting.Target hover = holdingHammer ? ClientTargeting.findVoxelTarget(player) : null;

        final List<BlockPos> invalid = new ArrayList<>();

        // Pass 1: the metal itself. Each pass owns its render type: the buffer source only keeps one
        // non-fixed render type in flight, so the buffers must not be interleaved.
        final VertexConsumer voxelBuffer = buffers.getBuffer(VOXEL_TYPE);
        for (Map.Entry<BlockPos, ClientWorkCache.ClientWork> entry : ClientWorkCache.map().entrySet())
        {
            final BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos) || !AnvilHandler.isOurAnvil(level.getBlockState(pos)))
            {
                invalid.add(pos);
                continue;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) continue;

            final ClientWorkCache.ClientWork work = entry.getValue();

            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            drawVoxels(poseStack, voxelBuffer, pos, entry.getValue(),
                LevelRenderer.getLightColor(level, pos), level);
            poseStack.popPose();
        }
        buffers.endBatch(VOXEL_TYPE);

        // Pass 2: additive glow of the hot metal
        final VertexConsumer glowBuffer = buffers.getBuffer(GLOW_TYPE);
        boolean drewGlow = false;
        for (Map.Entry<BlockPos, ClientWorkCache.ClientWork> entry : ClientWorkCache.map().entrySet())
        {
            final BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos) || !AnvilHandler.isOurAnvil(level.getBlockState(pos))) continue;
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) continue;

            final ClientWorkCache.ClientWork work = entry.getValue();
            if (ClientWorkCache.temperature(level, pos, work) < 550f) continue;

            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            drawGlow(poseStack, glowBuffer, pos, work, level);
            poseStack.popPose();
            drewGlow = true;
        }
        if (drewGlow)
        {
            buffers.endBatch(GLOW_TYPE);
        }

        if (holdingHammer)
        {
            final VertexConsumer lineBuffer = buffers.getBuffer(RenderType.lines());
            for (Map.Entry<BlockPos, ClientWorkCache.ClientWork> entry : ClientWorkCache.map().entrySet())
            {
                final BlockPos pos = entry.getKey();
                if (!level.isLoaded(pos) || !AnvilHandler.isOurAnvil(level.getBlockState(pos))) continue;
                if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) continue;

                poseStack.pushPose();
                poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                drawTarget(poseStack, lineBuffer, entry.getValue());

                if (hover != null && hover.pos().equals(pos))
                {
                    LevelRenderer.renderLineBox(poseStack, lineBuffer, localBox(hover.x(), hover.y(), hover.z()),
                        1f, 1f, 1f, 1f);
                }
                poseStack.popPose();
            }
            buffers.endBatch(RenderType.lines());
        }

        invalid.forEach(ClientWorkCache::remove);
    }

    private static boolean isHoldingHammer(Player player)
    {
        return player.getMainHandItem().is(TFCTags.Items.HAMMERS)
            || player.getOffhandItem().is(TFCTags.Items.HAMMERS);
    }

    private static void drawVoxels(PoseStack poseStack, VertexConsumer buffer, BlockPos pos,
                                   ClientWorkCache.ClientWork work, int light, ClientLevel level)
    {
        final Matrix4f matrix = poseStack.last().pose();
        // Read the temperature from the anvil's own inventory: TFC cools a stack lazily on read, so this
        // is exactly the value the server has - no smoothing, no extra packets, no disagreement.
        final float temperature = ClientWorkCache.temperature(level, pos, work);
        final int metal = metalColor(work.metal());
        // Hot metal is drawn fully bright so it reads as a light source of its own
        final int lightmap = temperature > 550f ? LightTexture.FULL_BRIGHT : light;

        final byte[] voxels = work.voxels();

        for (int y = 0; y < Voxels.SIZE_Y; y++)
        {
            for (int z = 0; z < Voxels.SIZE_Z; z++)
            {
                for (int x = 0; x < Voxels.SIZE_X; x++)
                {
                    final byte material = voxels[Voxels.index(x, y, z)];
                    if (material == Voxels.EMPTY) continue;

                    // Each voxel is a little hotter or colder than its neighbours (about +/- 5%), but the
                    // work item as a whole stays at the temperature it is at. That is what gives cast metal
                    // its uneven colour without turning into a patchwork.
                    final int hash = hash(x, y, z);
                    final float variation = 0.95f + ((hash >>> 5) & 15) * 0.00667f;
                    // Slag is not metal: it keeps its own dark grey colour and is not heated
                    final int tint = material == Voxels.SLAG
                        ? slagColor(hash)
                        : heatTint(metal, temperature * variation);

                    // A different 2x2 window per voxel keeps the surface from looking like a tiled grid
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

                    // All faces are wound counter clockwise as seen from outside; a wrong winding would make
                    // the face invisible because entitySolid culls back faces.
                    if (!filled(voxels, x, y + 1, z))
                    {
                        quad(buffer, matrix, lightmap, tint, u0, v0, u1, v1,
                            x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
                    }
                    if (y > 0 && !filled(voxels, x, y - 1, z))
                    {
                        // The underside of the bottom layer sits on the anvil's top face (11/16) and would
                        // z-fight with it, so it is never drawn.
                        quad(buffer, matrix, lightmap, shade(tint, 0.45f), u0, v0, u1, v1,
                            x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
                    }
                    if (!filled(voxels, x, y, z - 1))
                    {
                        quad(buffer, matrix, lightmap, shade(tint, 0.78f), u0, v0, u1, v1,
                            x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
                    }
                    if (!filled(voxels, x, y, z + 1))
                    {
                        quad(buffer, matrix, lightmap, shade(tint, 0.78f), u0, v0, u1, v1,
                            x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
                    }
                    if (!filled(voxels, x - 1, y, z))
                    {
                        quad(buffer, matrix, lightmap, shade(tint, 0.62f), u0, v0, u1, v1,
                            x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
                    }
                    if (!filled(voxels, x + 1, y, z))
                    {
                        quad(buffer, matrix, lightmap, shade(tint, 0.62f), u0, v0, u1, v1,
                            x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
                    }
                }
            }
        }
    }

    /** Deterministic per voxel hash, so the pattern does not flicker between frames. */
    private static int hash(int x, int y, int z)
    {
        int h = x * 374761393 + y * 668265263 + z * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static int clampChannel(int value)
    {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * Additive glow, exactly like the original game: the incandescence colour is added on top of the metal
     * with an intensity that grows from 550 °C.
     */
    private static void drawGlow(PoseStack poseStack, VertexConsumer buffer, BlockPos pos,
                                 ClientWorkCache.ClientWork work, ClientLevel level)
    {
        final Matrix4f matrix = poseStack.last().pose();
        final float temperature = ClientWorkCache.temperature(level, pos, work);

        final float r = Mth.clamp((temperature - 500f) / 400f, 0f, 1f);
        final float g = Mth.clamp((temperature - 900f) / 200f, 0f, 1f);
        final float b = Mth.clamp((temperature - 1100f) / 200f, 0f, 1f);
        final float intensity = Mth.clamp((temperature - 550f) / 700f, 0f, 1f);

        // Vintage Story adds the incandescence colour on top of the metal texture (extraGlow = (T-550)/2),
        // so the glow has to be strong enough to be seen on the already bright metal.
        final int red = (int) (r * 255);
        final int green = (int) (g * 255);
        final int blue = (int) (b * 255);
        final int alpha = (int) (intensity * 235f);
        if (alpha <= 0) return;

        final byte[] voxels = work.voxels();

        for (int y = 0; y < Voxels.SIZE_Y; y++)
        {
            for (int z = 0; z < Voxels.SIZE_Z; z++)
            {
                for (int x = 0; x < Voxels.SIZE_X; x++)
                {
                    if (voxels[Voxels.index(x, y, z)] == Voxels.EMPTY) continue;

                    final float x0 = x * PX;
                    final float y0 = Y_OFFSET + y * PX;
                    final float z0 = z * PX;
                    final float x1 = x0 + PX;
                    final float y1 = y0 + PX;
                    final float z1 = z0 + PX;

                    if (!filled(voxels, x, y + 1, z))
                    {
                        glowQuad(buffer, matrix, red, green, blue, alpha, x0, y1, z0, x0, y1, z1, x1, y1, z1,
                            x1, y1, z0);
                    }
                    if (!filled(voxels, x, y, z - 1))
                    {
                        glowQuad(buffer, matrix, red, green, blue, alpha, x0, y0, z0, x0, y1, z0, x1, y1, z0,
                            x1, y0, z0);
                    }
                    if (!filled(voxels, x, y, z + 1))
                    {
                        glowQuad(buffer, matrix, red, green, blue, alpha, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                            x0, y0, z1);
                    }
                    if (!filled(voxels, x - 1, y, z))
                    {
                        glowQuad(buffer, matrix, red, green, blue, alpha, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                            x0, y0, z0);
                    }
                    if (!filled(voxels, x + 1, y, z))
                    {
                        glowQuad(buffer, matrix, red, green, blue, alpha, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                            x1, y0, z1);
                    }
                }
            }
        }
    }

    private static void glowQuad(VertexConsumer buffer, Matrix4f matrix, int r, int g, int b, int alpha,
                                 float ax, float ay, float az, float bx, float by, float bz,
                                 float cx, float cy, float cz, float dx, float dy, float dz)
    {
        buffer.vertex(matrix, ax, ay, az).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, bx, by, bz).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, cx, cy, cz).color(r, g, b, alpha).endVertex();
        buffer.vertex(matrix, dx, dy, dz).color(r, g, b, alpha).endVertex();
    }

    private static void drawTarget(PoseStack poseStack, VertexConsumer buffer, ClientWorkCache.ClientWork work)
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

                    if (required && !filled)
                    {
                        LevelRenderer.renderLineBox(poseStack, buffer, localBox(x, y, z), 0.3f, 1f, 0.4f, 0.9f);
                    }
                    else if (!required && filled)
                    {
                        LevelRenderer.renderLineBox(poseStack, buffer, localBox(x, y, z), 1f, 0.25f, 0.2f, 0.9f);
                    }
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

    private static void quad(VertexConsumer buffer, Matrix4f matrix, int light, int color,
                             float u0, float v0, float u1, float v1,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz)
    {
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;

        vertex(buffer, matrix, ax, ay, az, r, g, b, u0, v0, light, 0f, 1f, 0f);
        vertex(buffer, matrix, bx, by, bz, r, g, b, u0, v1, light, 0f, 1f, 0f);
        vertex(buffer, matrix, cx, cy, cz, r, g, b, u1, v1, light, 0f, 1f, 0f);
        vertex(buffer, matrix, dx, dy, dz, r, g, b, u1, v0, light, 0f, 1f, 0f);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z,
                               int r, int g, int b, float u, float v, int light,
                               float nx, float ny, float nz)
    {
        buffer.vertex(matrix, x, y, z)
            .color(r, g, b, 255)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(nx, ny, nz)
            .endVertex();
    }

    /** Slag is a dark, slightly porous grey, clearly different from every metal. */
    private static int slagColor(int hash)
    {
        final int noise = ((hash & 0x1F) - 16) * 2;
        final int base = 58 + noise;
        return clampChannel(base) << 16 | clampChannel(base + 2) << 8 | clampChannel(base + 4);
    }

    private static int metalColor(String metal)
    {
        if (metal == null) return METAL_COLOR;
        return switch (metal.toLowerCase(Locale.ROOT))
        {
            case "wrought_iron", "cast_iron" -> 0xB4B4B4;
            case "steel" -> 0x6E6E6E;
            case "copper" -> 0xC4785C;
            case "bronze" -> 0xAC7434;
            case "brass" -> 0xC8A040;
            case "gold" -> 0xF0CC55;
            case "silver" -> 0xE8E8E8;
            case "bismuth_bronze" -> 0x9A7A4E;
            case "black_bronze" -> 0x6A5A44;
            case "nickel" -> 0xD8D8C0;
            case "tin" -> 0xE0E0E0;
            case "zinc" -> 0xC8D0D0;
            case "platinum" -> 0xE8E8E0;
            case "red_steel" -> 0x7A6A6A;
            case "blue_steel" -> 0x6A7A8A;
            case "sterling_silver" -> 0xF0F0F0;
            case "rose_gold" -> 0xF0C090;
            default -> METAL_COLOR;
        };
    }

    /**
     * The solid pass colour: metal at room temperature, then the same incandescence curve as the glow pass
     * (but fully opaque), so the ingot already looks red / yellow / white before the additive glow kicks in.
     */
    private static int heatTint(int metalColor, float temperature)
    {
        if (temperature <= 480f)
        {
            return metalColor;
        }

        final float r = Mth.clamp((temperature - 500f) / 400f, 0f, 1f);
        final float g = Mth.clamp((temperature - 900f) / 200f, 0f, 1f);
        final float b = Mth.clamp((temperature - 1100f) / 200f, 0f, 1f);
        final float blend = Mth.clamp((temperature - 480f) / 120f, 0f, 1f);

        // Full incandescence colour, but never darker than the metal itself: hot metal is brighter
        final int tr = Math.max((metalColor >> 16) & 0xFF, (int) (r * 255));
        final int tg = Math.max((metalColor >> 8) & 0xFF, (int) (g * 255));
        final int tb = Math.max(metalColor & 0xFF, (int) (b * 255));

        return blendInt(metalColor, (tr << 16) | (tg << 8) | tb, blend);
    }

    private static int shade(int color, float factor)
    {
        final int r = Mth.clamp(Math.round(((color >> 16) & 0xFF) * factor), 0, 255);
        final int g = Mth.clamp(Math.round(((color >> 8) & 0xFF) * factor), 0, 255);
        final int b = Mth.clamp(Math.round((color & 0xFF) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int blendInt(int from, int to, float amount)
    {
        final int r = (int) Mth.lerp(amount, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        final int g = (int) Mth.lerp(amount, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        final int b = (int) Mth.lerp(amount, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
