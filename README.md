# Villager News — NeoForge

NeoForge port by **NycTheModman**.

An unofficial Java adaptation of the Villager News Bedrock add-on for **Minecraft 1.21.1** and **NeoForge 21.1.255 or newer in the 1.21.1 series**.

The mod includes the converted models, textures, animations, dialogue, sounds and handbook. GeckoLib and Mocha are bundled into the release JAR, so installation needs one mod JAR.

## Build

Install a **JDK 21** and set `JAVA_HOME` to its installation directory. The Gradle wrapper is included; the first build needs internet access to download dependencies.

On Windows, run this from the project folder:

```powershell
.\gradlew.bat build
```

On Linux or macOS:

```sh
chmod +x gradlew
./gradlew build
```

The mod JAR is written to `build/libs/`.

## Development

Use `./gradlew runClient` to launch the development client or `./gradlew runServer` to launch a development server. On Windows, use `.\gradlew.bat` instead of `./gradlew`. Development worlds and settings are stored under `run/`.

Run `./gradlew test` for the automated test suite. Client Java sources are under `src/client/java`; shared and server code is under `src/main/java`.

## Installation

Install NeoForge for Minecraft 1.21.1, then place the built JAR in your instance's `mods` folder. Replace older versions of this mod when updating. No separate mcaddon, resource pack, GeckoLib or Mocha download is needed.

## Included fixes

This source includes the correction for villagers' face lighting when looking down and the fix for the held handbook's cover being obscured by its outline geometry.

## Credits and licensing

Based on the MIT-licensed Villager News: Javafied code. The original copyright notices and MIT terms are retained in `LICENSE` and `src/main/resources/META-INF/LICENSE-JAVAFIED.txt`.

Villager News and the original add-on models, textures, sounds, animations, dialogue and artwork belong to Element Animation and Oreville Studios. The code's MIT license does not license those assets. This adaptation is unofficial.
