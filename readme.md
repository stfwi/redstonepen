
## Redstone Pen

----
## Version history

    - v1.11.42    [M] Fabric updated, mod meta data fixed (issue #62, ty ppc).

    - v1.11.41    [M] Updated lang zh_cn (ty special_tt).

    - v1.11.40    [A] Redstone Remote can now enable/disable Redstone Logic Controls.
                  [M] Adapted Pen bulk connector placement (no sneak required now).
                  [F] Fixed RCA client sync (adapted to changed networking).

    - v1.11.39    [F] Fixed Remote missing click event handling in survival mode.
                  [M] Pen cannot break any blocks except Redstone wires/tracks.

    - v1.11.38    [M] Pen/Quill Track segment placement/removal now vanilla-like
                      (right-click: place segment, left-click: remove segment).
                  [F] Fixed loot table folder name (causing no drops on break).

    - v1.11.37    [F] In-block-multi-track update fix and performance cleanups.
                  [E] Added Basic Redstone Gauge (*experimental*, signal display).
                  [E] Added Redstone Remote (*experimental*, lever/button control).

    - v1.11.36    [F] Fixed RLC preview rendering FOV inconsistency (issue #48, ty Meowca).
                  [F] Added TIVx() minimum interval documentation (issue #50, ty aerotactics).
                  [F] In-block-multi-track update regression fixed (#51, ty aerotactics).

    - v1.11.35    [M] RLC parser edited to indicate an error for unknown signal processing
                      identifiers (".CO", ".RE", ".FE"). (Issue #46, ty aerotactics).
                  [F] Addressed incompatibility with FrozenLib (issue #47, ty GuineaPig)
                  [M] Log file tagging improved, Pen debugging capabilities added in
                      release mode.

    - v1.11.34    [F] Fixed Pen recipes.
                  [M] Moved RCA (Redstone Client Adapter) memory map file
                      root from the tmpdir to the game directory, @see
                      https://github.com/stfwi/redstonepen/tree/redstone-client-adapter.

    - v1.11.33    [U] Updated to 1.21 fabric.

    - v1.10.33    [U] Updated to 1.20.6 fabric.

    - v1.9.33     [U] Updated to 1.20.5 fabric.

    - v1.9.32     [U] Updated to 1.20.5-pre fabric.

    - v1.8.32     [F] Timer documentation edited (issue #42).

    - v1.8.31     [A] Mod startup GIT commit ID logging added.
                  [A] Basic Lever, Button, and Pulse Button added.
                  [M] TICKRATE=t now ignores signal interrupts (makes
                      closed loop control simpler, issue #41).
                  [M] Refactoring to improve loader compatibility.

    - v1.8.30     [F] Fixed RLC UI initialization locking (issue #40, ty shinchai).
                  [F] Fixed RLC RCA (Arduino % Co connector) sync.
                  [F] Bridge Relay to Track update fixed.
                  [M] RLC editor cursor brightness increased.

    - v1.8.29     [M] Added RLC interval timer instance `TIV3`.
                  [M] Added RLC TIVx enable signal (2nd argument).
                  [M] Re-implemented/refactored Track optimizations.
                  [M] Redstone Track color brightness adapted.
                  [M] Redstone Relay stone texture edited.

    - v1.8.28     [F] Fixed localization codec exception for RS signal inspection
                      with the Pen.
                  [F] Adapted signal update ordering.
                  [F] Pack meta file format fixed (issue #33, ty tropheusj).

    - v1.8.27     [F] Fixed neighbour updates of removed track segments (issue #32, ty lukescott)

    - v1.8.26     [F] Fixed recipe network deserialization (issue #30, ty nickademas)

    - v1.8.25     [F] Updated to loom 1.4.4.

    - v1.8.24     [U] Updated to 1.20.4 fabric.

    - v1.7.24     [U] Updated to 1.20.3 fabric.

    - v1.6.24     [F] Fixed connection update for Redstone Tracks (issue #28, ty fluppkin).

    - v1.6.23     [U] Updated to 1.20.2 fabric.

    - v1.5.23     [U] Updated to 1.20.1 fabric.

    - v1.4.23     [U] Updated to 1.19.4 fabric.

    - v1.3.22     [F] RLC font size reduced, enabling to write 23 code lines.

    - v1.3.21     [A] Added RLC TIV1/2 function blocks (Interval timed pulse
                      every N ticks, two instances TIV1 and TIV2).
                  [A] Added RLC comparator signal capture on redstone outputs.

    - v1.3.20     [F] Fixed RLC tickrate=1 TON operation.
                  [A] Added deadline based RLC tick adaption.

    - v1.3.19     [U] Ported to 1.19.3 Fabric.

    - v1.2.18     [F] Fixed RLC swapped port colors when placed on ceilings.

    - v1.2.17     [U] Ported to 1.19.2 Fabric.

    - v1.0.16     [A] Added translation zh_cn (PR#15, deluxghost).
                  [F] Fixed indirect block signal detection (issue #16, ty Grumpey102).

    - v1.0.15     [U] Ported to 1.19.1 Fabric.

    - v1.0.11     [U] Initial 1.19 Fabric port.

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
