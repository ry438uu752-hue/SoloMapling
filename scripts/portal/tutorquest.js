/*
	Royal Knights (Cygnus Knights) tutorial portals — maps 130030001 ~ 130030004.

	These west00 portals are type-7 (scripted) in the WZ data and originally gated
	the player behind tutorial quests 20010~20013. Those quests are driven by trainer
	NPC scripts (1102004~1102008 / Kimu 1101000) which are not present in this repack,
	so the gate permanently blocked players from walking to the next map.

	We open the portals unconditionally: the player can always walk forward through
	the tutorial (130030001 -> ... -> 130030006 -> Ereve), keeping the level 1~10 path
	usable while the trainer NPC quests remain optional/unimplemented.
*/

var TUTORIAL_WARPS = {
    130030001: 130030002,
    130030002: 130030003,
    130030003: 130030004,
    130030004: 130030005
};

function enter(pi) {
    var target = TUTORIAL_WARPS[pi.getPlayer().getMapId()];

    if (target != null) {
        pi.playPortalSound();
        pi.warp(target, 0);
        return true;
    }

    return false;
}
