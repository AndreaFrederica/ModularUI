package com.cleanroommc.modularui.network.packets;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.network.IPacket;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.api.sync.MuiTemplateContract;

import io.netty.handler.codec.DecoderException;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class OpenGuiPacket<T extends GuiData> implements IPacket {

    private int windowId;
    private int networkId;
    private UIFactory<T> factory;
    private PacketBuffer data;
    private MuiTemplateContract templateContract;

    public OpenGuiPacket() {}

    public OpenGuiPacket(int windowId, int networkId, UIFactory<T> factory, PacketBuffer data) {
        this(windowId, networkId, factory, data, null);
    }

    public OpenGuiPacket(int windowId, int networkId, UIFactory<T> factory, PacketBuffer data,
                         MuiTemplateContract templateContract) {
        this.windowId = windowId;
        this.networkId = networkId;
        this.factory = factory;
        this.data = data;
        this.templateContract = templateContract;
    }

    @Override
    public void write(PacketBuffer buf) throws IOException {
        buf.writeVarInt(this.windowId);
        buf.writeVarInt(this.networkId);
        NetworkUtils.writeStringSafe(buf, this.factory.getFactoryName(), 32, true);
        buf.writeBoolean(this.templateContract != null);
        if (this.templateContract != null) {
            buf.writeVarInt(this.templateContract.getPlanFormatVersion());
            NetworkUtils.writeStringSafe(buf, this.templateContract.getSchemaId(), 256, true);
            buf.writeVarInt(this.templateContract.getSchemaVersion());
            buf.writeBytes(this.templateContract.getFingerprint());
        }
        NetworkUtils.writeByteBuf(buf, this.data, 1 << 20);
    }

    @Override
    public void read(PacketBuffer buf) {
        this.windowId = buf.readVarInt();
        this.networkId = buf.readVarInt();
        this.factory = (UIFactory<T>) GuiManager.getFactory(NetworkUtils.readStringSafe(buf, 32));
        if (buf.readBoolean()) {
            int formatVersion = buf.readVarInt();
            String schemaId = NetworkUtils.readStringSafe(buf, 256);
            int schemaVersion = buf.readVarInt();
            if (buf.readableBytes() < MuiTemplateContract.FINGERPRINT_BYTES) {
                throw new DecoderException("Truncated MUI template fingerprint");
            }
            byte[] fingerprint = new byte[MuiTemplateContract.FINGERPRINT_BYTES];
            buf.readBytes(fingerprint);
            this.templateContract = new MuiTemplateContract(formatVersion, schemaId, schemaVersion, fingerprint);
        }
        this.data = NetworkUtils.readPacketBuffer(buf, 1 << 20);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public @Nullable IPacket executeClient(NetHandlerPlayClient handler) {
        GuiManager.openFromClient(this.windowId, this.networkId, this.factory, this.data,
                this.templateContract, Platform.getClientPlayer());
        return null;
    }

    @Override
    public @Nullable IPacket executeServer(NetHandlerPlayServer handler) {
        T guiData = this.factory.readGuiData(handler.player, this.data);
        GuiManager.open(this.factory, guiData, handler.player);
        return null;
    }
}
