/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation version 3 as published by
 the Free Software Foundation. You may not use, modify or distribute
 this program under any other version of the GNU Affero General Public
 License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;

import java.awt.*;

/**
 * Click-to-blink teleport (client mod "worldmap_blink", opcode 0x106).
 *
 * <p>The client sends the ABSOLUTE world target (camera center + click offset).
 * This handler clamps it to the map's VR bounds and warps the character there
 * ({@link Character#changeMap(server.maps.MapleMap, Point)} with spawn point 0x80),
 * i.e. a same-map instantaneous move — no item, no FieldLimit, no autoban.
 */
public final class BlinkTeleportHandler extends AbstractPacketHandler {

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character player = c.getPlayer();
        if (player == null || player.getMap() == null) {
            return;
        }

        // Only honor when the admin panel enabled blink for this character.
        if (!net.server.AdminHackState.isBlink(player.getId())) {
            return;
        }

        // Client sends the absolute world target (camera center + click offset).
        // Clamp it to the map's VR bounds, then warp directly to it (SET_FIELD,
        // which has a brief map-transition fade).
        short targetX = p.readShort();
        short targetY = p.readShort();

        java.awt.Rectangle area = player.getMap().getMapArea();
        int x = Math.max(area.x, Math.min((int) targetX, area.x + area.width));
        int y = Math.max(area.y, Math.min((int) targetY, area.y + area.height));

        player.changeMap(player.getMap(), new Point(x, y));
    }
}