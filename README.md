# Auto Builder (Fabric, Minecraft 1.21.1, client-side)

Keys (rebindable): B menu | G confirm build in preview | R rotate preview | X stop

Flow: aim at the ground -> B -> pick a build -> Build (places at the spot you aimed at)
or Preview (ghost follows your crosshair, G to start).

Materials: creative = conjured automatically. Survival = pulled from your inventory
into the hotbar; it pauses and tells you what you're out of.
It stays within normal reach, so walk along with it on big builds.

Add builds: drop .json into config/autobuilder/builds (see example_pad.json there).
Speed: -/+ in the menu, default 7 blocks/sec.

Build: gradle wrapper --gradle-version 8.10 && ./gradlew build  -> build/libs/autobuilder-1.0.0.jar
