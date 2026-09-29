# Database setup and updates

The server uses its own MySQL schema, `solomapling`, rather than sharing the older
`cosmic` schema. The existing `cosmic` database is not copied, changed, or removed.
Both schemas remain on the same MySQL server. The current `DB_USER` is still `root`,
so this separates the data namespace but does not restrict that account from accessing
other schemas; use a schema-scoped application account for privilege-level isolation.

## First start

`config.yaml` sets `DB_NAME: solomapling` and `DB_AUTO_CREATE: true`. At startup,
`DatabaseConnection` creates the schema if it is missing, using `utf8mb4`; Liquibase
then creates the current tables and seeds initial game data. The configured MySQL
account must have permission to create a schema and to apply the Liquibase changesets.

Docker Compose uses the same schema name. The `database/docker-db-data` directory is
the persistent MySQL data volume and remains ignored by Git; changing
`MYSQL_DATABASE` does not delete or rename schemas already present in that volume.

## Apply current database changes without starting the game

Run `database\Update-Database.bat` from the project checkout. It builds the current
server JAR, connects using the active `config.yaml` and `DB_HOST`/`DB_NAME` overrides,
creates the schema if needed, and runs the Liquibase changelog. The game Server also
runs the same Liquibase update automatically during startup.

Add future table/schema updates as new Liquibase changesets in
`src/main/resources/db/changelog-tables.xml` or `db/extensions`. Do not edit a
changeset that has already run on any database; Liquibase validates its checksum.

## Fresh database and old test data

The new `solomapling` schema starts fresh with the project's latest schema and seed
data. Existing test accounts and characters remain in `cosmic` and are intentionally
not migrated. The changelog seeds its bundled `admin` test account and character
(`admin` / `admin`, PIN `0000`, PIC `000000`); change those credentials before exposing
the game publicly. To use an existing MySQL account with limited privileges, create
the schema and grant that account access first, then set `DB_AUTO_CREATE: false`.
