/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.auth.server;

import com.github.steveice10.mc.protocol.codec.MinecraftCodec;
import com.github.steveice10.mc.protocol.codec.MinecraftCodecHelper;
import com.github.steveice10.mc.protocol.data.game.chunk.ChunkSection;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.level.LightUpdateData;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityInfo;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.LongArrayTag;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.nukkitx.math.vector.Vector3i;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.auth.AuthManager;
import org.barrelmc.barrel.server.ProxyServer;
import org.barrelmc.barrel.utils.Utils;

import java.util.BitSet;
import java.util.Collections;

public class AuthServer extends SessionAdapter {

    private final String username;

    public AuthServer(Session session, String username) {
        this.username = username;
        session.send(new ClientboundLoginPacket(
                0, false, GameMode.ADVENTURE, GameMode.ADVENTURE,
                1, new String[]{"minecraft:overworld"}, ProxyServer.getInstance().getDimensionTag(),
                "minecraft:overworld", "minecraft:overworld", 100,
                10, 6, 6, false, true, false, false, null
        ));

        ChunkSection emptyChunk = new ChunkSection();
        Utils.fillPalette(emptyChunk.getChunkData());
        Utils.fillPalette(emptyChunk.getBiomeData());

        ChunkSection chunk = new ChunkSection();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    chunk.setBlock(x, y, z, y == 0 ? 14 : 0);
                }
            }
        }
        Utils.fillPalette(chunk.getBiomeData());

        ByteBuf bytebuf = Unpooled.buffer();
        MinecraftCodecHelper helper = MinecraftCodec.CODEC.getHelperFactory().get();
        for (int i = 0; i < 16; i++) {
            if (i == 5) {
                helper.writeChunkSection(bytebuf, chunk);
                continue;
            }
            helper.writeChunkSection(bytebuf, emptyChunk);
        }

        CompoundTag heightMaps = new CompoundTag("");
        heightMaps.put(new LongArrayTag("MOTION_BLOCKING", new long[37]));
        session.send(new ClientboundLevelChunkWithLightPacket(
                0, 0, bytebuf.array(), heightMaps,
                new BlockEntityInfo[0],
                new LightUpdateData(new BitSet(), new BitSet(), new BitSet(), new BitSet(), Collections.emptyList(), Collections.emptyList(), true)
        ));
        bytebuf.release();

        session.send(new ClientboundSetDefaultSpawnPositionPacket(Vector3i.from(8, 82, 8), 0));
        session.send(new ClientboundPlayerPositionPacket(8, 82, 8, 0, 0, 0, false));
        session.send(new ClientboundSystemChatPacket(Component.text("§ePlease input your email and password.\n§aEx: account@mail.com:password123"), false));
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (packet instanceof ServerboundChatPacket) {
            String messageStr = ((ServerboundChatPacket) packet).getMessage();

            String[] message = messageStr.split(":");
            if (message.length != 2) {
                session.send(new ClientboundSystemChatPacket(Component.text("§cWrong format"), false));
                return;
            }

            if (message[1].length() < 8) {
                session.send(new ClientboundSystemChatPacket(Component.text("§cInvalid password length"), false));
                return;
            }

            session.send(new ClientboundSystemChatPacket(Component.text("§eLogging in..."), false));

            try {
                String token = AuthManager.getInstance().getXboxLogin().getAccessToken(message[0], message[1]);
                AuthManager.getInstance().getAccessTokens().put(this.username, token);
                AuthManager.getInstance().getLoginPlayers().put(this.username, true);
            } catch (Exception e) {
                session.send(new ClientboundSystemChatPacket(Component.text("§cLogin failed! Account or password invalid, please re-input the email and password"), false));
                return;
            }

            session.send(new ClientboundSystemChatPacket(Component.text("§aLogin successfull! Please re-join."), false));
        }
    }
}
