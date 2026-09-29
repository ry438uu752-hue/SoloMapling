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
package server.maps;

import constants.id.MapId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import provider.DataDirectoryEntry;
import provider.DataEntry;
import provider.DataFileEntry;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.wz.WZFiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dynamically scans {@code wz/Map.wz/Map/Map0..Map9/*.img} at first access and collects
 * every valid map id.
 *
 * <p>There is no hardcoded map array: after a WZ re-import (see the admin panel's
 * one-click WZ import) and a server restart, newly added maps are discovered
 * automatically. Invalid entries — the null map (0), {@link MapId#NONE} and any
 * non-numeric file name — are filtered out.
 */
public final class MapDataScanner {

    private static final Logger log = LoggerFactory.getLogger(MapDataScanner.class);

    private static final List<Integer> ALL_MAP_IDS = Collections.unmodifiableList(load());
    private static final Set<Integer> ALL_MAP_ID_SET = new HashSet<>(ALL_MAP_IDS);

    private MapDataScanner() {
    }

    /** Every valid map id found under {@code Map.wz/Map}, sorted ascending. */
    public static List<Integer> getAllMapIds() {
        return ALL_MAP_IDS;
    }

    /** True if {@code mapId} is a real map present in the current {@code Map.wz}. */
    public static boolean isValidMap(int mapId) {
        return ALL_MAP_ID_SET.contains(mapId);
    }

    private static List<Integer> load() {
        List<Integer> ids = new ArrayList<>();
        try {
            DataProvider mapData = DataProviderFactory.getDataProvider(WZFiles.MAP);
            DataEntry mapEntry = mapData.getRoot().getEntry("Map");
            if (!(mapEntry instanceof DataDirectoryEntry mapDir)) {
                log.warn("MapDataScanner: no 'Map' directory under Map.wz, map list is empty.");
                return ids;
            }

            for (DataDirectoryEntry area : mapDir.getSubdirectories()) {
                for (DataFileEntry file : area.getFiles()) {
                    String name = file.getName();   // e.g. "100000000.img"
                    if (name == null || !name.endsWith(".img")) {
                        continue;
                    }
                    String raw = name.substring(0, name.length() - 4);
                    int mapId;
                    try {
                        mapId = Integer.parseInt(raw);
                    } catch (NumberFormatException e) {
                        continue;   // non-numeric entry -> not a map
                    }
                    if (mapId > 0 && mapId != MapId.NONE) {
                        ids.add(mapId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("MapDataScanner: failed to scan Map.wz/Map.", e);
        }

        Collections.sort(ids);
        log.info("MapDataScanner: found {} valid map ids in Map.wz/Map.", ids.size());
        return ids;
    }
}
