/**
 * @description 兌換系統 NPC 腳本（完整批量兌換 + 自動收集安全模式）
 * Path: scripts/Special/ExchangeSystem.js
 */

var status = -1;
var selectedOption = -1;

// 定義道具常數
var MAPLE_LEAF_ID = 4001126;      // 標準楓葉 ID
var PET_FOOD_ID = 2120000;        // 修正後的寵物飼料 ID（寵物飼料）
var MYSTERY_SKILL_BOOK = 2022415;   // 神秘技能書（解鎖母書：200 楓葉）
var MYSTERY_MASTERY_BOOK = 2290285; // 神秘精通書（衝 20/30 上限書：300 楓葉）

// 動態收集椅子的陣列
var validChairs = [];

// 動態收集阿爾泰造型（Cap）的陣列
var validComicCaps = [];

// 🔒 【閃退黑名單】如果你測試時發現抽到哪一張椅子會斷線，請把 ID 填在下面（用逗號隔開）
var crashChairsBlacklist = [
    3010007, 
    3010008  
];

function start() {
    status = -1;
    // 像美髮沙龍一樣，NPC 點開時自動動態抓取全服椅子
    initSafePools();
    action(1, 0, 0);
}

function initSafePools() {
    if (validChairs.length === 0) {
        // 抓取經典椅子區段 (3010000 - 3019999，這個區段最穩定、最安全)
        for (var id = 3010000; id <= 3019999; id++) {
            if (cm.itemExists(id)) {
                var isBlacklisted = false;
                for (var i = 0; i < crashChairsBlacklist.length; i++) {
                    if (id === crashChairsBlacklist[i]) {
                        isBlacklisted = true;
                        break;
                    }
                }
                if (!isBlacklisted) {
                    validChairs.push(id);
                }
            }
        }
    }
    if (validComicCaps.length === 0) {
        // 抓取阿爾泰造型 Cap 區段 (1008900 - 1009999)
        for (var id = 1008900; id <= 1009999; id++) {
            if (cm.itemExists(id)) {
                validComicCaps.push(id);
            }
        }
    }
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }
    status++;

    if (status === 0) {
        var text = "\t\t\t\t#e[ 楓之谷兌換中心 ]#n\t\t\t\t\r\n\r\n";
        text += "楓點：" + cm.getPlayer().getCashShop().getCash(2) + "\r\n";
        text += "楓幣：" + cm.getPlayer().getMeso() + "\r\n";
        text += "楓葉：" + cm.getItemQuantity(MAPLE_LEAF_ID) + "\r\n";
        text += "可用椅子：#r" + validChairs.length + "#k 種\r\n";
        text += "可用阿爾泰造型：#r" + validComicCaps.length + "#k 種\r\n\r\n";
        text += "請選擇兌換服務：\r\n";
        text += "#L0##b[方案 1]#k 10,000 楓幣 #e->#n 1 楓葉 #r(批量)#k#l\r\n";
        text += "#L1##b[方案 2]#k 100,000 楓幣 #e->#n 5 楓點 #r(批量)#k#l\r\n"; // 💡 已修改
        text += "#L2##b[方案 3]#k 20 楓幣 #e->#n 1 寵物飼料 #r(批量)#k#l\r\n";
        text += "#L3##r[方案 4]#k 200 楓葉 #e->#n 1 神秘技能書（技能解鎖）#l\r\n";
        text += "#L4##r[方案 5]#k 300 楓葉 #e->#n 1 神秘精通書（提升等級上限）#l\r\n";
        text += "#L5##g[方案 6]#k 100 楓葉 #e->#n 1 隨機椅子 #d(自動收集)#k#l\r\n";
        text += "#L6##g[方案 7]#k 20 楓葉 #e->#n 1 隨機阿爾泰造型 #d(自動收集)#k#l\r\n";
        cm.sendSimple(text);

    } else if (status === 1) {
        if (selectedOption === -1) {
            selectedOption = selection;
        }

        switch (selectedOption) {
            case 0: // 方案 1：批量楓葉
                cm.sendGetNumber("你想兌換多少 #b楓葉#k？\r\n（每片成本：10,000 楓幣，限額：1 - 999）", 1, 1, 999);
                break;

            case 1: // 方案 2：批量楓點 💡（已加入輸入框機制）
                cm.sendGetNumber("你想兌換多少組 #b楓點（每組 5 點）#k？\r\n（每組成本：100,000 楓幣，限額：1 - 999）", 1, 1, 999);
                break;

            case 2: // 方案 3：批量寵物飼料
                cm.sendGetNumber("你想兌換多少個 #b寵物飼料#k？\r\n（每個成本：20 楓幣，限額：1 - 999）", 1, 1, 999);
                break;

            case 3: // 方案 4：200 楓葉 -> 神秘技能書
                if (cm.getItemQuantity(MAPLE_LEAF_ID) < 200) {
                    cm.sendOk("你的楓葉不足，至少需要 200 個。");
                } else if (!cm.canHold(MYSTERY_SKILL_BOOK, 1)) {
                    cm.sendOk("你的背包已滿，請先清出空間。");
                } else {
                    cm.gainItem(MAPLE_LEAF_ID, -200);
                    cm.gainItem(MYSTERY_SKILL_BOOK, 1);
                    cm.sendOk("兌換成功！獲得 1 本 #b神秘技能書#k。\r\n在消耗欄雙擊可隨機開出 4 轉技能母書！");
                }
                cm.dispose();
                break;

            case 4: // 方案 5：300 楓葉 -> 神秘精通書
                if (cm.getItemQuantity(MAPLE_LEAF_ID) < 300) {
                    cm.sendOk("你的楓葉不足，至少需要 300 個。");
                } else if (!cm.canHold(MYSTERY_MASTERY_BOOK, 1)) {
                    cm.sendOk("你的背包已滿，請先清出空間。");
                } else {
                    cm.gainItem(MAPLE_LEAF_ID, -300);
                    cm.gainItem(MYSTERY_MASTERY_BOOK, 1);
                    cm.sendOk("兌換成功！獲得 1 本 #b神秘精通書#k。\r\n在消耗欄雙擊可隨機開出 20 或 30 精通書！");
                }
                cm.dispose();
                break;

            case 5: // 方案 6：100 楓葉 -> 隨機椅子
                if (cm.getItemQuantity(MAPLE_LEAF_ID) < 100) {
                    cm.sendOk("你的楓葉不足，至少需要 100 個。");
                } else if (validChairs.length === 0) {
                    cm.sendOk("錯誤：系統資料範圍內找不到可用椅子。");
                } else {
                    var randomIdx = Math.floor(Math.random() * validChairs.length);
                    var pickedChairId = validChairs[randomIdx];
                    
                    if (!cm.canHold(pickedChairId, 1)) {
                        cm.sendOk("你的背包已滿，請先清出空間。");
                    } else {
                        cm.gainItem(MAPLE_LEAF_ID, -100);
                        cm.gainItem(pickedChairId, 1);
                        cm.sendOk("兌換成功！獲得 #i" + pickedChairId + "# #z" + pickedChairId + "#。");
                    }
                }
                cm.dispose();
                break;

            case 6: // 方案 7：20 楓葉 -> 隨機阿爾泰造型（Cap）
                if (cm.getItemQuantity(MAPLE_LEAF_ID) < 20) {
                    cm.sendOk("你的楓葉不足，至少需要 20 個。");
                } else if (validComicCaps.length === 0) {
                    cm.sendOk("錯誤：系統資料範圍內找不到阿爾泰造型。");
                } else {
                    var randomIdx2 = Math.floor(Math.random() * validComicCaps.length);
                    var pickedCapId = validComicCaps[randomIdx2];

                    if (!cm.canHold(pickedCapId, 1)) {
                        cm.sendOk("你的背包已滿，請先清出空間。");
                    } else {
                        cm.gainItem(MAPLE_LEAF_ID, -20);
                        cm.gainItem(pickedCapId, 1);
                        cm.sendOk("兌換成功！獲得 #i" + pickedCapId + "# #z" + pickedCapId + "#。");
                    }
                }
                cm.dispose();
                break;

            default:
                cm.dispose();
                break;
        }

    } else if (status === 2) {
        var quantity = selection;

        // 嚴格阻斷玩家透過封包修改傳入非法數字 (例如負數、0或破千)
        if (quantity < 1 || quantity > 999) {
            cm.sendOk("選擇的數量無效，請輸入 1 到 999 之間的數字。");
            cm.dispose();
            return;
        }

        if (selectedOption === 0) { // 方案 1 批量楓葉
            var totalCostMeso = quantity * 10000;
            if (cm.getMeso() < totalCostMeso) {
                cm.sendOk("你的楓幣不足，需要 " + totalCostMeso.toLocaleString() + " 楓幣。");
            } else if (!cm.canHold(MAPLE_LEAF_ID, quantity)) {
                cm.sendOk("你的背包空間不足。");
            } else {
                cm.gainMeso(-totalCostMeso);
                cm.gainItem(MAPLE_LEAF_ID, quantity);
                cm.sendOk("兌換成功！獲得 " + quantity + " 個楓葉。");
            }
            cm.dispose();

        } else if (selectedOption === 1) { // 方案 2 批量楓點 💡（後端安全加乘運算）
            var totalCostMeso = quantity * 100000;
            var totalEarnedPoints = quantity * 5;

            if (cm.getMeso() < totalCostMeso) {
                cm.sendOk("你的楓幣不足，需要 " + totalCostMeso.toLocaleString() + " 楓幣。");
            } else {
                cm.gainMeso(-totalCostMeso);
                // 2 代表標準點數商城系統中的 Maple Points (楓點)
                cm.getPlayer().getCashShop().gainCash(2, totalEarnedPoints); 
                cm.sendOk("兌換成功！花費 " + totalCostMeso.toLocaleString() + " 楓幣，獲得 " + totalEarnedPoints + " 楓點。");
            }
            cm.dispose();

        } else if (selectedOption === 2) { // 方案 3 批量寵物飼料
            var totalCostMeso = quantity * 20;
            if (cm.getMeso() < totalCostMeso) {
                cm.sendOk("你的楓幣不足，需要 " + totalCostMeso.toLocaleString() + " 楓幣。");
            } else if (!cm.canHold(PET_FOOD_ID, quantity)) {
                cm.sendOk("你的背包空間不足。");
            } else {
                cm.gainMeso(-totalCostMeso);
                cm.gainItem(PET_FOOD_ID, quantity);
                cm.sendOk("兌換成功！獲得 " + quantity + " 個寵物飼料。");
            }
            cm.dispose();
        } else {
            cm.dispose();
        }
    } else {
        cm.dispose();
    }
}
