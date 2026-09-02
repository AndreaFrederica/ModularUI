package com.cleanroommc.modularui.network;

import com.cleanroommc.modularui.ModularUI;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class NetworkUtils {

    public static final Consumer<PacketBuffer> EMPTY_PACKET = buffer -> {};

    public static final boolean DEDICATED_CLIENT = detectDedicatedClient();

    private static boolean detectDedicatedClient() {
        try {
            net.minecraftforge.fml.relauncher.Side side = FMLCommonHandler.instance().getSide();
            return side != null && side.isClient();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isClient() {
        return FMLCommonHandler.instance().getEffectiveSide().isClient();
    }

    public static boolean isDedicatedClient() {
        return DEDICATED_CLIENT;
    }

    public static boolean isClient(EntityPlayer player) {
        if (player == null) return isClient();
        return player.world == null ? player instanceof EntityPlayerSP : player.world.isRemote;
    }

    public static void writeByteBuf(PacketBuffer writeTo, ByteBuf writeFrom) {
        writeByteBuf(writeTo, writeFrom, Integer.MAX_VALUE);
    }

    public static void writeByteBuf(PacketBuffer writeTo, ByteBuf writeFrom, int maxBytes) {
        if (maxBytes < 0) throw new IllegalArgumentException("Max byte buffer size must not be negative");
        int length = writeFrom.readableBytes();
        if (length > maxBytes) {
            throw new IllegalArgumentException("Byte buffer exceeds maximum size of " + maxBytes + " bytes: " + length);
        }
        writeTo.writeVarInt(length);
        writeTo.writeBytes(writeFrom);
    }

    public static ByteBuf readByteBuf(PacketBuffer buf) {
        return readByteBuf(buf, Integer.MAX_VALUE);
    }

    public static ByteBuf readByteBuf(PacketBuffer buf, int maxBytes) {
        if (maxBytes < 0) throw new IllegalArgumentException("Max byte buffer size must not be negative");
        int length = buf.readVarInt();
        if (length < 0 || length > maxBytes) {
            throw new DecoderException("Byte buffer length is outside the allowed range 0.." + maxBytes + ": " + length);
        }
        if (length > buf.readableBytes()) {
            throw new DecoderException("Byte buffer declares " + length + " bytes, but only " +
                    buf.readableBytes() + " are readable");
        }
        return Unpooled.copiedBuffer(buf.readSlice(length));
    }

    public static PacketBuffer readPacketBuffer(PacketBuffer buf) {
        return new PacketBuffer(readByteBuf(buf));
    }

    public static PacketBuffer readPacketBuffer(PacketBuffer buf, int maxBytes) {
        return new PacketBuffer(readByteBuf(buf, maxBytes));
    }

    public static void writeItemStack(PacketBuffer buffer, ItemStack itemStack) {
        buffer.writeItemStack(itemStack);
    }

    public static ItemStack readItemStack(PacketBuffer buffer) {
        try {
            return buffer.readItemStack();
        } catch (IOException e) {
            ModularUI.LOGGER.catching(e);
            return ItemStack.EMPTY;
        }
    }

    public static void writeFluidStack(PacketBuffer buffer, @Nullable FluidStack fluidStack) {
        if (fluidStack == null) {
            buffer.writeBoolean(true);
        } else {
            buffer.writeBoolean(false);
            NBTTagCompound fluidStackTag = fluidStack.writeToNBT(new NBTTagCompound());
            buffer.writeCompoundTag(fluidStackTag);
        }
    }

    @Nullable
    public static FluidStack readFluidStack(PacketBuffer buffer) {
        if (buffer.readBoolean()) {
            return null;
        }
        try {
            return FluidStack.loadFluidStackFromNBT(buffer.readCompoundTag());
        } catch (IOException e) {
            ModularUI.LOGGER.throwing(e);
            return null;
        }
    }

    public static void writeStringSafe(PacketBuffer buffer, String string) {
        writeStringSafe(buffer, string, Short.MAX_VALUE, false);
    }

    public static void writeStringSafe(PacketBuffer buffer, @Nullable String string, boolean crash) {
        writeStringSafe(buffer, string, Short.MAX_VALUE, crash);
    }

    public static void writeStringSafe(PacketBuffer buffer, @Nullable String string, int maxBytes) {
        writeStringSafe(buffer, string, maxBytes, false);
    }

    public static void writeStringSafe(PacketBuffer buffer, @Nullable String string, int maxBytes, boolean crash) {
        maxBytes = Math.min(maxBytes, Short.MAX_VALUE);
        if (string == null) {
            buffer.writeVarInt(Short.MAX_VALUE + 1);
            return;
        }
        if (string.isEmpty()) {
            buffer.writeVarInt(0);
            return;
        }
        byte[] bytesTest = string.getBytes(StandardCharsets.UTF_8);
        byte[] bytes;

        if (bytesTest.length > maxBytes) {
            if (crash) {
                throw new IllegalArgumentException("Max String size is " + maxBytes + ", but found " + bytesTest.length + " bytes for '" + string + "'!");
            }
            bytes = new byte[maxBytes];
            System.arraycopy(bytesTest, 0, bytes, 0, maxBytes);
            ModularUI.LOGGER.warn("Warning! Synced string exceeds max length!");
        } else {
            bytes = bytesTest;
        }
        buffer.writeVarInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    public static String readStringSafe(PacketBuffer buffer) {
        return readStringSafe(buffer, Short.MAX_VALUE);
    }

    public static String readStringSafe(PacketBuffer buffer, int maxBytes) {
        if (maxBytes < 0) throw new IllegalArgumentException("Max string size must not be negative");
        maxBytes = Math.min(maxBytes, Short.MAX_VALUE);
        int length = buffer.readVarInt();
        if (length == Short.MAX_VALUE + 1) return null;
        if (length < 0 || length > maxBytes) {
            throw new DecoderException("String length is outside the allowed range 0.." + maxBytes + ": " + length);
        }
        if (length > buffer.readableBytes()) {
            throw new DecoderException("String declares " + length + " bytes, but only " +
                    buffer.readableBytes() + " are readable");
        }
        if (length == 0) return StringUtils.EMPTY;
        String s = buffer.toString(buffer.readerIndex(), length, StandardCharsets.UTF_8);
        buffer.readerIndex(buffer.readerIndex() + length);
        return s;
    }
}
