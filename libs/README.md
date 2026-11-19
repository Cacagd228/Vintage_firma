# Dependencies (not committed)

`build.gradle` resolves TerraFirmaCraft and Patchouli from this folder as plain mod jars
(see `gradle.properties` for the exact versions):

- `tfc-1.20.1-<tfc_version>.jar`
- `patchouli-1.20.1-<patchouli_version>.jar`

They are intentionally **not** committed (large binaries). Download the matching jars for
Forge 1.20.1 and drop them here before running `./gradlew build`.

Current versions (from `gradle.properties`):

- TerraFirmaCraft: `3.2.30`
- Patchouli: `85`