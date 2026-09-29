/*
 * 9010004 服務 NPC 的全域天氣控制子選單。
 * 每一層獨立、立即生效；可任意組合。
 * 層的位元值定義於 server.weather.WeatherService 與 src/weather.h。
 */

var WeatherService = Java.type("server.weather.WeatherService");
var WeatherPackets = Java.type("server.weather.WeatherPackets");

var selectedLayers = 0;
var layerOptions = [
    { bit: 1,   label: "雲層" },
    { bit: 2,   label: "下雨" },
    { bit: 4,   label: "飄雪" },
    { bit: 8,   label: "濃霧" },
    { bit: 16,  label: "閃電" },
    { bit: 32,  label: "楓葉" },
    { bit: 64,  label: "花瓣" },
    { bit: 128, label: "沙塵" },
    { bit: 256, label: "暴風雪粒子" },
    { bit: 512, label: "彩虹" }
];

function start() {
    selectedLayers = 0;
    showMenu();
}

function checkbox(option, index) {
    var on = (selectedLayers & option.bit) !== 0;
    return "#L" + index + "#" + (on ? "#g[開]#k " : "[  ] ") + option.label + "#l\r\n";
}

function showMenu(message) {
    var text = "#e#b[ 全域天氣控制 ]#k#n\r\n";
    text += "每次點擊立即生效，可同時勾選任意組合，沒有組合限制。\r\n";
    text += "目前伺服器天空：#r" + WeatherService.skyName(WeatherService.currentSky()) + "#k；時間：";
    text += WeatherService.clockString() + "\r\n\r\n";
    if (message) text += "#d" + message + "#k\r\n\r\n";

    for (var i = 0; i < layerOptions.length; i++) {
        text += checkbox(layerOptions[i], i);
    }

    text += "\r\n#L20##r[清除所有天氣層]#l\r\n";
    text += "#L22##d[恢復自動天氣／時間]#l\r\n\r\n";
    text += "時間：#L30#白天#l  #L31#黃昏#l  #L32#夜晚#l  #L33#黎明#l";
    cm.sendSimple(text);
}

function broadcast() {
    WeatherPackets.broadcast(cm.getClient().getWorldServer(), false);
}

function applyLayers() {
    WeatherService.setWeatherLayers(selectedLayers, WeatherService.OVERRIDE_HOLD_MS);
    broadcast();
}

function setTime(minute, label) {
    WeatherService.setTime(minute);
    WeatherService.setBareSky(false);
    broadcast();
    cm.sendOk("全域時間已設定為「" + label + "」。天氣層仍各自獨立控制。");
    cm.dispose();
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }

    if (selection >= 0 && selection < layerOptions.length) {
        selectedLayers ^= layerOptions[selection].bit;
        applyLayers();
        showMenu(layerOptions[selection].label + ((selectedLayers & layerOptions[selection].bit) !== 0
            ? " 已開啟。" : " 已關閉。"));
        return;
    }

    if (selection === 20) {
        selectedLayers = 0;
        applyLayers();
        showMenu("已清除所有視覺天氣層；天空會保持晴朗直到自動模式或逾時。");
        return;
    }
    if (selection === 22) {
        WeatherService.clearTimeOverride();
        WeatherService.clearSkyOverride();
        WeatherService.setBareSky(false);
        broadcast();
        cm.sendOk("時間與天氣已恢復自動模式。");
        cm.dispose();
        return;
    }
    if (selection === 30) { setTime(WeatherService.TIME_DAY, "白天"); return; }
    if (selection === 31) { setTime(WeatherService.TIME_DUSK, "黃昏"); return; }
    if (selection === 32) { setTime(WeatherService.TIME_NIGHT, "夜晚"); return; }
    if (selection === 33) { setTime(WeatherService.TIME_DAWN, "黎明"); return; }
    cm.dispose();
}
