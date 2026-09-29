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
    along with this program.  If not, see <http://gnu.org>.
*/
package net.server.channel.handlers;

import client.Character;
import client.Client;
import config.YamlConfig;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.maps.FieldLimit;
import server.maps.MiniDungeonInfo;
import tools.PacketCreator;

public final class EnterMTSHandler extends AbstractPacketHandler {

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character chr = c.getPlayer();

        // 1. 安全檢查：如果設定檔關閉拍賣，或角色狀態異常則直接解除卡死返回
        if (chr == null || !chr.isAlive()) {
            c.sendPacket(PacketCreator.enableActions());
            return;
        }

        // 2. 狀態過濾：限制特定副本、活動或地圖不可使用萬能選單
        if (chr.getEventInstance() != null) {
            c.sendPacket(PacketCreator.serverNotice(5, "目前狀態下無法使用隨身中心。"));
            c.sendPacket(PacketCreator.enableActions());
            return;
        }

        if (MiniDungeonInfo.isDungeonMap(chr.getMapId())) {
            c.sendPacket(PacketCreator.serverNotice(5, "迷你地下城內無法使用隨身中心。"));
            c.sendPacket(PacketCreator.enableActions());
            return;
        }

        if (FieldLimit.CANNOTMIGRATE.check(chr.getMap().getFieldLimit())) {
            chr.dropMessage(1, "當前地圖限制，無法使用隨身中心。");
            c.sendPacket(PacketCreator.enableActions());
            return;
        }

        if (chr.getLevel() < 10) {
            c.sendPacket(PacketCreator.blockedMessage2(5));
            c.sendPacket(PacketCreator.enableActions());
            return;
        }

        // 3. 收尾防卡：關閉玩家當前的互動視窗（防止卡交易或卡對話）
        chr.closePlayerInteractions();
        chr.closePartySearchInteractions();
        chr.unregisterChairBuff();

        // 4. 關鍵核心：發送解除卡死封包，並直接拉起 9010004 腳本！
        c.sendPacket(PacketCreator.enableActions());
        scripting.npc.NPCScriptManager.getInstance().start(c, 9010004, chr);
    }
}
