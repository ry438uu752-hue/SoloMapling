package net.server.channel.handlers;

import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.maps.HyperTeleportRockService;
import tools.PacketCreator;

public final class HyperTeleportRockHandler extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(HyperTeleportRockHandler.class);

    @Override
    public void handlePacket(InPacket packet, Client client) {
        if (packet.available() != Integer.BYTES) {
            log.warn("Malformed Hyper Teleport Rock packet from {}: {} payload bytes",
                    client.getRemoteAddress(), packet.available());
            client.sendPacket(PacketCreator.enableActions());
            return;
        }

        int targetMapId = packet.readInt();
        if (!HyperTeleportRockService.tryTeleport(client, targetMapId)) {
            client.sendPacket(PacketCreator.enableActions());
        }
    }
}
