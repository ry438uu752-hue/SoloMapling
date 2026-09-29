/*  
    Function: 隨身服務中心主選單（含核心指令資訊）
    Path: scripts/npc/9010004.js
*/

var status = -1;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode === 1) {
        status++;
    } else if (mode === -1) {
        status--;
    } else {
        cm.dispose();
        return;
    }

    if (status === 0) {
        // ✨ 熱情歡迎詞與祝福語 (Welcome greeting & Wishing a wonderful day)
        var text = "\t\t\t\t#e#d[ 歡迎來到隨身服務中心 ]#k#n\t\t\t\t\r\n";
        text += "嗨，勇敢的冒險家！歡迎來到你的全能隨身助理中心。\r\n";
        text += "祝你旅途愉快，擁有美好的一天！☀️\r\n\r\n";
        
        // 💰 玩家資產狀態看板 (Player info dashboard)
        text += "#e#b[ 你的資產 ]#k#n\r\n";
        text += " > 樂豆點：" + cm.getPlayer().getCashShop().getCash(1) + "\r\n";
        text += " > 楓點：" + cm.getPlayer().getCashShop().getCash(2) + "\r\n";
        text += " > 楓幣：" + cm.getPlayer().getMeso().toLocaleString() + "\r\n\r\n";
        
        // 💡 核心指令介紹區（包含 @beauty、@checkin 以及新增的 @commands）
        text += "#e#r[💡 實用指令小提示 ]#k#n\r\n";
        text += "  #b@beauty#k   - 可以儲存你的髮型、臉型和膚色造型！\r\n";
        text += "  #b@checkin#k  - 開啟每日簽到視窗。\r\n";
        text += "  #b@commands#k - 查看你帳號可用的完整指令清單。\r\n"; // 💡 新增這一行
        text += "  *(直接在聊天欄輸入即可使用！)*\r\n\r\n";
        
        // 項目選單按鈕 (Menu selections)
        text += "#e#d[ 可用服務 ]#k#n\r\n";
        text += "#L0# #b隨身倉庫#k#l\r\n";
        text += "#L1# #g免費美髮沙龍（基礎美容）#k#l\r\n"; 
		text += "#L2# #r匠人查找#k#l\r\n";
		text += "#L3# #d兌換系統（道具／點數）#k#l\r\n";
		text += "#L4# #m查看所有指令清單#k#l\r\n"; // 這裡點擊也會開啟對應 @commands 的 UI 畫面
		text += "#L5##c天氣控制#k#l\r\n";
		text += "#L6# #dBOT招募管理（分身）#k#l";
        cm.sendSimple(text);
        
    } else if (status === 1) {
        doSelect(selection);
    } else {
        cm.dispose();
    }
}

function doSelect(selection) {
    switch (selection) {
        case 0:
            openNpc("PortableStorage"); 
            break;
		case 1:
            openNpc("FreeSalon"); 
            break;
		case 2:
            openNpc("CrafterFinder");
            break;
		case 3:
            openNpc("ExchangeSystem");
            break;
		case 4:
            openNpc("commands"); // 呼叫先前為您修改、動態撈取核心指令的命令清單
            break;
		case 5:
			openNpc("WeatherControl");
			break;
		case 6:
			openNpc("BotRecruitManager");
			break;
        default:
            cm.sendOk("此功能尚未開放，敬請期待！");
            cm.dispose();
    }
}

function openNpc(scriptName) {
    cm.dispose();
    cm.openNpc(9010004, scriptName);
}
