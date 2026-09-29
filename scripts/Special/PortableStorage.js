/*  
    Function: 隨身功能 - 倉庫
    Path: scripts/Special/PortableStorage.js
*/

function start() {
    // 呼叫服內正確的個人倉庫開啟封包 API
    cm.getPlayer().getStorage().sendStorage(cm.getClient(), 9010004);
    cm.dispose();
}
