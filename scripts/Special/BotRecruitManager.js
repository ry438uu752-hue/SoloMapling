/*
 * BOT 招募管理（分身 Doppelganger）
 * Path: scripts/Special/BotRecruitManager.js
 *
 * 4 選項：招募單兵（自選職業）／隨機招滿（職業輪流）／踢除指定 ID／全隊剔除。
 * 分身 = 臨時伴侶假人：跟隨隊長 + 自動打怪；取消僱傭立即刪除、不留伺服器紀錄。
 */

var status = -1;
var mode = 0; // 0=主選單, 1=招募職業選擇, 2=踢除 ID 輸入
var CLASS_NAMES = ['戰士', '法師', '弓手', '飛俠']; // baseClass 1..4

// 惰性取得 Java class：不能在頂層直接 Java.type(...)，否則腳本載入時就拋錯、
// 選單整個開不起來。第一次要用才解析。
var manager = null;
function getManager() {
    if (manager === null) {
        manager = Java.type('soloMapling.ArtificialPlayer.BotTypes.DoppelgangerManager');
    }
    return manager;
}

function start() {
    status = -1;
    mode = 0;
    action(1, 0, 0);
}

function action(actMode, type, selection) {
    if (actMode !== 1) {
        cm.dispose();
        return;
    }
    status++;

    if (status === 0) {
        var text = "#e#d[ BOT 招募管理 ]#k#n\r\n";
        text += "招募臨時分身（跟隨 + 自動打怪），取消僱傭立即刪除。\r\n\r\n";
        text += "#L0# 招募單兵（自選職業）#l\r\n";
        text += "#L1# 隨機招滿（職業輪流）#l\r\n";
        text += "#L2# 踢除指定 ID#l\r\n";
        text += "#L3# 全隊剔除#l";
        cm.sendSimple(text);

    } else if (status === 1) {
        if (selection === 0) {
            mode = 1;
            var t = "選擇分身職業：\r\n";
            for (var i = 0; i < CLASS_NAMES.length; i++) {
                t += "#L" + i + "# " + CLASS_NAMES[i] + "#l\r\n";
            }
            cm.sendSimple(t);
        } else if (selection === 1) {
            var n = getManager().recruitFill(cm.getPlayer());
            cm.sendOk("已招募 " + n + " 名分身（職業輪流、外觀隨機）。");
            cm.dispose();
        } else if (selection === 2) {
            mode = 2;
            cm.sendGetText("請輸入要踢除的分身 ID：");
        } else if (selection === 3) {
            var k = getManager().kickAll(cm.getPlayer());
            cm.sendOk("已剔除 " + k + " 名分身，立即刪除、不留紀錄。");
            cm.dispose();
        } else {
            cm.dispose();
        }

    } else if (status === 2) {
        if (mode === 1) {
            if (selection >= 0 && selection < CLASS_NAMES.length) {
                var ok = getManager().recruitSingle(cm.getPlayer(), selection + 1);
                cm.sendOk(ok
                    ? "已招募 1 名「" + CLASS_NAMES[selection] + "」分身。"
                    : "招募失敗（隊伍可能已滿或地圖不允許）。");
            }
            cm.dispose();
        } else if (mode === 2) {
            var txt = cm.getText();
            var id = parseInt(txt, 10);
            if (isNaN(id)) {
                cm.sendOk("無效的 ID。");
            } else {
                var kicked = getManager().kickBot(id);
                cm.sendOk(kicked
                    ? "已踢除分身 ID " + id + "。"
                    : "找不到該分身（或它不是分身）。");
            }
            cm.dispose();
        } else {
            cm.dispose();
        }

    } else {
        cm.dispose();
    }
}
