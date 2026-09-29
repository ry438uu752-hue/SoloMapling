/**
 * @description 匠人 NPC 傳送／選擇器（修正版）
 * Path: scripts/Special/CrafterFinder.js
 */

var status = -1;
var text;
var npcs = {
    1012002: "弓箭手村 - 維克 - 道具匠人", 
    1032002: "魔法森林 - 艾芭 - 道具匠人", 
    1022003: "#r勇士之村 - 克里斯 - 精煉師#k", 
    1022004: "勇士之村 - 史密斯 - 道具匠人", 
    1052002: "墮落城市 - 街頭 JM - 道具匠人",
    1091003: "鸚鵡螺號 - 淑琳 - 道具匠人",
    2020000: "#r冰原雪域 - 潘尼斯 - 精煉師#k",
    2040016: "#r玩具城 - 派 - 精煉師#k",
    2100001: "#r阿里安特 - 穆罕默德 - 精煉師#k"
};

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }
    status++;

    if (status === 0) {
        text = "請選擇要前往的匠人 NPC： \r\n\r\n";
        for (n in npcs) {
            // 使用標準的 #L 序號機制
            text += "#L" + n + "##b[" + n + "]#d - " + npcs[n] + "#l\r\n";
        }
        // 💡 修正處：改用全核心通用的 sendSimple 方法，徹底解決 Unknown identifier 錯誤
        cm.sendSimple(text);
        
    } else if (status === 1) {
        // 當玩家點擊某個匠人後，selection 會直接回傳該按鈕的 L 序號（即 NPC ID）
        cm.dispose();
        // 💡 修正處：直接引導打開對應的實體 NPC
        cm.openNpc(parseInt(selection)); 
    } else {
        cm.dispose();
    }
}
