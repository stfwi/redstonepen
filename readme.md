
## Redstone Pen

----
## Version history

    - v1.11.33    [U] Updated to 1.21 neoforge.

    - v1.8.32     [A] Basic Lever, Button, and Pulse Button added.
                  [M] TICKRATE=t now ignores signal interrupts (makes
                      closed loop control simpler, issue #41).
                  [M] Refactoring to improve loader compatibility.

    - v1.8.31     [F] Fixed RLC UI initialization locking (issue #40, ty shinchai).
                  [F] Bridge Relay to Track update fixed.
                  [F] RCA init order fixed.
                  [M] RLC editor cursor brightness increased.

    - v1.8.30     [F] Fixed track segments adding/removing updates.

    - v1.8.29     [U] Initial port for Neoforge 1.20.4 (20.4.200).
                  [M] Added RLC interval timer instance `TIV3`.
                  [M] Added RLC TIVx enable signal (2nd argument).
                  [M] Re-implemented/refactored Track optimizations.
                  [M] Redstone Track colour brightness adapted.
                  [M] Redstone Relay stone texture edited.

    - v1.3.22     [F] Fixed connection update for Redstone Tracks (issue #28, ty fluppkin).

    - v1.3.21     [U] Initial port to NeoForged 1.20.1.

    - v1.3.20     [U] Initial Forge port to 1.20.1.

    - v1.2.20     [F] Fixed Optifine related preview-rendering issue (#26, thx wisquimas).

    - v1.2.19     [M] RLC font size reduced, enabling to write 23 code lines.

    - v1.2.18     [F] Fixed RLC tickrate=1 TON operation.
                  [A] Added deadline based RLC tick adaption.
                  [A] Added RLC TIV1/2 function blocks (Interval timed pulse
                      every N ticks, two instances TIV1 and TIV2).
                  [A] Added RLC comparator signal capture on redstone outputs.

    - v1.2.17     [F] Fixed RLC swapped port colors when placed on ceilings.

    - v1.2.16     [U] Initial Forge port to 1.19.2.

    - v1.1.15     [U] Initial Forge port to 1.19.1.
                  [A] Added translation zh_cn (PR#15, deluxghost).

    - v1.0.14     [U] Forge updated to Forge 41.1.0.

    - v1.0.13     [U] Forge updated to Forge 1.19-41.0.64.

    - v1.0.12     [F] Minor Track placement fixes.
                  [M] Library/obsolete MC override cleanups.

    - v1.0.11     [U] Initial 1.19 port.

    - v1.0.10     [R] Release build.
                  [F] Fixed RLC GUI freeze on hot bar item quick move (issue #11, ty serifina).

    - v1.0.10-b3  [F] Fixed RCA indicator in the RLC GUI.

    - v1.0.10-b2  [A] Added basic mmap based approach for the Redstone
                      Client Adapter (interfacing Arduino & Co).

    - v1.0.10-b1  [U] Updated to 1.18.2.

    - v1.0.9      [R] Release build.
                  [A] Added RLC counter documentation.
                  [A] Added RLC TICKRATE variable.
                  [M] Pen recipe advancement edited.

    - v1.0.9-b3   [A] Added Redstone Logic Control.
                  [A] Added relay placement preview.
                  [A] Added language support for Português (PR#2, faelBrunnoS).
                  [A] Added Pen crafting advancement.
                  [A] Added language es_es (PR#3, hacheraw).

    - v1.0.9-b2   [R] Fixed regression issue related to #1.

    - v1.0.9-b1   [F] Fixed diagonal placement RS notification (issue #1, ty iris-xii, rodg).
                  [M] Creative mode pen handling edited.
                  [F] Relay occlusion/shadow fixed.

    - v1.0.8      [R] Release build.

    - v1.0.8-b2   [M] Forge/gradle update, minor adaptions.

    - v1.0.8-b1   [U] Initial 1.18.1 port.

    - v1.0.7      [R] Initial 1.17.1 release.

    - v1.0.7-b1   [U] Initial 1.17.1 port.

    - v1.0.6      [F] Bridging Relay added to support wire crossings.

    - v1.0.6-b1   [F] Fixed track net update power flags caching issue.

    - v1.0.5      [A] Added Pulse Redstone Relay.

    - v1.0.4      [A] Added Inverted Redstone Relay.
                  [A] Added Bi-Stable Redstone Relay.
                  [F] Fixed Pen/Quill model 3rd person view.

    - v1.0.3      [A] Added Redstone Relay.

    - v1.0.2      [A] Added Redstone Quill (uses Redstone directly from the inventory).
                  [F] Wire update when removing diagonal/orthogonal tracks fixed.

    - v1.0.2-b1   [F] Fixed placement net update of tracks around the corner.
                  [I] Known issue: Power update around the corner when placing
                      not yet always working.

    - v1.0.1      [F] Fixed block removal bug (thx focsie).
                  [F] Fixed vanilla Redstone connection bug.

    - v1.0.0      [R] Initial release.
                  [F] Internal mod logo image rescaled.
                  [F] Fixed indirect non-wire bulk connector power reading.

    - v1.0.0-b1   [A] Initial implementation.

-----
