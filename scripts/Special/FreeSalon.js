/*
 * 免費角色美容中心。
 * 不需要會員券、點券或任何購買道具，直接選擇並更換外觀。
 */

var status = -1;
var mode = 0;
var styles = [];
var page = 0;
var PAGE_SIZE = 100;

function start() {
    status = -1;
    action(1, 0, 0);
}

function addAvailable(list, itemid) {
    if (cm.itemExists(itemid) && !cm.isCosmeticEquipped(itemid)) {
        list.push(itemid);
    }
}

function collectRange(startId, endId) {
    var result = [];
    for (var id = startId; id <= endId; id++) {
        addAvailable(result, id);
    }
    return result;
}

function collectHairColors() {
    var result = [];
    var base = parseInt(cm.getPlayer().getHair() / 10) * 10;
    for (var i = 0; i < 10; i++) {
        addAvailable(result, base + i);
    }
    return result;
}

function collectEyeColors() {
    var result = [];
    var face = cm.getPlayer().getFace();
    var base = face - (face % 100);
    for (var i = 0; i < 10; i++) {
        addAvailable(result, base + i * 100);
    }
    return result;
}

function showMainMenu() {
    cm.sendSimple(
        "#e#b[ 免費美髮沙龍 ]#k#n\r\n\r\n" +
        "不需要會員券、點券或任何道具，直接選擇外觀即可。\r\n\r\n" +
        "#L1##b[髮型]#k#l\r\n" +
        "#L2##r[臉型]#k#l\r\n" +
        "#L3##b[髮色]#k#l\r\n" +
        "#L4##r[眼睛顏色]#k#l\r\n" +
        "#L5##g[膚色]#k#l\r\n"
    );
}

function showPageMenu() {
    var pages = Math.max(1, Math.ceil(styles.length / PAGE_SIZE));
    var text = "#e#b[ 免費 ]#k#n\r\n";
    text += "目前共有 #r" + styles.length + "#k 個可選項目，請選擇分頁：\r\n\r\n";
    for (var i = 0; i < pages; i++) {
        var from = i * PAGE_SIZE + 1;
        var to = Math.min((i + 1) * PAGE_SIZE, styles.length);
        text += "#L" + (1000 + i) + "##b第 " + (i + 1) + " 頁#k (" + from + " - " + to + ")#l\r\n";
    }
    text += "\r\n#L999##d[返回主選單]#k#l";
    cm.sendSimple(text);
}

function showStylePage() {
    var from = page * PAGE_SIZE;
    var to = Math.min(from + PAGE_SIZE, styles.length);
    var current = [];
    for (var i = from; i < to; i++) {
        current.push(styles[i]);
    }
    cm.sendStyle("請選擇外觀；此更換完全免費。", current);
}

function applySelection(selection) {
    var selected = styles[page * PAGE_SIZE + selection];
    if (selected == null) {
        cm.dispose();
        return;
    }
    if (mode === 1 || mode === 3) {
        cm.setHair(selected);
    } else if (mode === 2 || mode === 4) {
        cm.setFace(selected);
    } else if (mode === 5) {
        cm.setSkin(selected);
    }
    cm.sendOk("外觀更換成功，完全免費。");
    cm.dispose();
}

function action(actionMode, type, selection) {
    if (actionMode !== 1) {
        cm.dispose();
        return;
    }
    status++;

    if (status === 0) {
        showMainMenu();
        return;
    }

    if (status === 1) {
        if (selection === 1) {
            mode = 1;
            // 頭髮全部在 3xxxx（4xxxx 已重映射到 30000~39999 空隙）
            styles = collectRange(30000, 39999);
        } else if (selection === 2) {
            mode = 2;
            styles = collectRange(20000, 29999);
        } else if (selection === 3) {
            mode = 3;
            styles = collectHairColors();
        } else if (selection === 4) {
            mode = 4;
            styles = collectEyeColors();
        } else if (selection === 5) {
            mode = 5;
            styles = [0, 1, 2, 3, 4, 5, 9, 10];
        } else {
            cm.dispose();
            return;
        }
        page = 0;
        showPageMenu();
        return;
    }

    if (status === 2) {
        if (selection === 999) {
            status = 0;
            showMainMenu();
        } else if (selection >= 1000) {
            page = selection - 1000;
            showStylePage();
        } else {
            cm.dispose();
        }
        return;
    }

    if (status === 3) {
        applySelection(selection);
    } else {
        cm.dispose();
    }
}
