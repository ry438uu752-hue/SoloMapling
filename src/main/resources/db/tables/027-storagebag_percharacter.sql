-- ============================================================================
-- Storage Bag  ->  per-CHARACTER + rename the CASH bag to the MOUNT bag.
-- This runs against a BRAND-NEW database (this is a brand-new SoloMapling
-- server, so none of the bag columns exist yet). It is plain DDL that Liquibase
-- executes exactly once; re-runs are tracked in the DATABASECHANGELOG table.
--
-- After this, the 4 bags (ore / scroll / chair / mount) behave exactly like the
-- normal inventory: their items live in `inventoryitems` keyed by `characterid`
-- + `type`
--   type 10 = ore bag, 11 = scroll bag, 12 = chair bag, 13 = mount bag
-- (no metadata table; capacity is a fixed 200 slots).
--
-- The 4 auto-collect toggles stay ON `characters` (per character):
--   autoOreStorage, autoScrollStorage, autoChairStorage, autoMountStorage.
--
-- NOTE: no DELIMITER / stored procedures here -- this file is executed through
-- Liquibase (JDBC), which does not understand the mysql client's DELIMITER
-- directive.
-- ============================================================================

ALTER TABLE `characters` ADD COLUMN `autoOreStorage`    TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE `characters` ADD COLUMN `autoScrollStorage` TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE `characters` ADD COLUMN `autoChairStorage`  TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE `characters` ADD COLUMN `autoMountStorage`  TINYINT(1) NOT NULL DEFAULT 0;

-- Legacy per-ACCOUNT bag items (only if a prior deploy existed): they were stored
-- in `inventoryitems` with characterid = NULL. Clearing just those NULL rows
-- never touches per-character bag items (characterid IS NOT NULL).
DELETE FROM `inventoryitems` WHERE `type` IN (10, 11, 12, 13) AND `characterid` IS NULL;

-- Old per-account metadata table is no longer used (slots are fixed at 200,
-- keyed by character).
DROP TABLE IF EXISTS `orestorages`;