WorldEdit Expanded
==================

This fork adds extra commands, patterns and masks on top of upstream WorldEdit.
Everything lives in `worldedit-core`, so it works on every platform. NeoForge is the primary target (Fabric, Bukkit/Paper and Sponge also work).

## Running it (NeoForge, Minecraft 26.3)

You need **JDK 25** installed (e.g. [Adoptium Temurin 25](https://adoptium.net/)). Check with `java -version`.
The first build downloads Gradle, Minecraft and all dependencies, so expect it to take several minutes.

### Option A: launch a dev Minecraft client straight from the repo (easiest)

```sh
# macOS / Linux
./gradlew :worldedit-neoforge:runClient

# Windows
gradlew.bat :worldedit-neoforge:runClient
```

This starts Minecraft 26.3 with NeoForge and this WorldEdit already loaded, using an offline dev account.
Create a creative world with cheats enabled and use the commands below.
`:worldedit-neoforge:runServer` starts a dedicated server instead (accept the EULA in `worldedit-neoforge/run/eula.txt` first).

### Option B: build a jar and install it in your normal game

```sh
./gradlew :worldedit-neoforge:build
```

Copy `worldedit-neoforge/build/libs/worldedit-neoforge-mc26.3-<version>-dist.jar` into the `mods` folder of a
NeoForge 26.3 install (the NeoForge version this was built against is `neoforge` in `gradle/libs.versions.toml`).
Remove any other WorldEdit jar first. Use the `-dist` jar, not `-dist-slim` (an intermediate jar missing the embedded jar-in-jar dependencies).

Other platforms work the same way, e.g. `./gradlew :worldedit-fabric:runClient` for Fabric.
`./gradlew build` builds everything, with jars in `worldedit-<platform>/build/libs`.

## Building where you aim, and tab-completion hints

**Build at your crosshair.** Add `-c` to a generator command and it builds where your crosshair points instead of
where you stand: in the air block just in front of the surface you are looking at (the closest spot to the crosshair
that isn't inside the ground), up to `jumpto-max-distance` (2048) blocks away, using the same long-range sight line as
`/jumpto`. Where `-c` already meant something else, the letter is different:

| Flag | Commands |
|---|---|
| `-c` | `//sphere` `//hsphere` `//cyl` `//hcyl` `//cone` `//pyramid` `//hpyramid` `//helix` `//hhelix` `//torus` `//htorus` `/forestgen` `/pumpkins` `//feature` `//structure`, `//selshape`, `//build` `//text` `//image`, `//skyscraper`, `//rock` `//crystals` `//giantmushroom` `//roots` `//coral`, `//combine add\|cut\|keep` (the first shape decides where it is built), `//radial` `//grid`, `//river` `//ptree` |
| `-t` | `//paste` (its `-c` is conform), `//lathe` (its `-c` is the nearest-edge axis) |
| `-x` | `//shape` (its `-c` is ring copies and `-t` is tilt) |

The flag works before or after the settings, e.g. `//skyscraper art_deco 30 -c` or `//rock stone 5 -c`. Only players
can aim; from the console you get a message.

**Hints while typing.** `//build` and `//builds` suggest the part names; `//skyscraper`, `//style info`, `//style save`
(base style) and `//style delete` suggest the style names (including saved ones); and the settings of `//build` and
`//skyscraper` are suggested too, e.g. `//skyscraper art_deco 20 cr` offers `crown=flat`, `crown=spire`,
`crown=stepped` and `crown=pyramid`, and the style roles (`window=`, `trim=`, ...). Shape names (`//shape`) and warp
names (`//warp`) already tab-complete.

## Edit preview

`//preview` turns preview mode on (run it again to turn it off). While it's on, your edits (commands *and* brushes)
are not built. Instead you see them as "ghost" blocks that only you can see, so it works with a plain vanilla client.
Previewed edits stack: a second command sees the result of the first.

| Command | What it does |
|---|---|
| `//preview [on\|off]` | Turn preview mode on or off. Turning it off throws away anything not yet applied. |
| `//preview apply` | Build everything you're previewing, as one edit you can `//undo`. |
| `//preview cancel` | Throw away the previewed changes and show the real world again. |
| `//preview show` | Re-send the ghost blocks (they vanish if you walk far away and the chunks reload). |
| `//preview status` | Is preview mode on, and how many changes are waiting. |

Notes: only blocks are previewed. Biome changes and pasted entities are skipped while previewing, and commands that
*remove* entities (`//butcher`, `//remove`) still act immediately. `//undo`/`//redo` always work on the real world.
At most `max-preview-blocks` (default 100,000) ghost blocks are shown at once; bigger previews can still be applied.

## Large edits without freezing the server

Like AsyncWorldEdit, big edits (50,000+ changed blocks by default) are written into the world a slice at a time,
using at most 20 ms of each server tick, so the server keeps running while a huge `//set` or paste goes in.
You get a message when it starts and finishes.

* The edit is still *calculated* all at once; what's spread out is placing the blocks, which is usually the slow part.
* Starting another edit in the same world first finishes any edits still being placed there, so nothing gets out of
  order. Stopping the server finishes them too.
* `//jobs` lists edits being placed, `//jobs cancel <id>` stops one (the part already placed can be `//undo`ne),
  and `//jobs finish` places the rest immediately.
* Works on NeoForge and Fabric. Settings in `worldedit.properties`: `time-sliced-edits`, `time-sliced-edit-threshold`,
  `time-sliced-edit-tick-budget-ms`, `max-preview-blocks`.

## Terrain painting and heightmaps

`//texture <rules> [-d depth] [-s noiseScale]` paints the top of the terrain in the selection by rules, picking the
first rule that matches each column. A rule is `pattern@condition&condition...`, or just `pattern` for "anything else".
Conditions: `y>N`, `y<N`, `slope>D`, `slope<D` (degrees), `noise>F`, `noise<F` (0-1), and `>=`/`<=` also work. A noise threshold outside 0-1 or a slope outside 0-90 can never match, so it is refused.

```
//texture snow_block@y>140,stone@slope>45,coarse_dirt@noise>0.7&slope>25,grass_block
```

`//heightmap export <file> [-c] [-f]` saves the selection's terrain heights as a 16-bit grayscale PNG
(`-c`: a colour map of the top blocks instead; `-f`: overwrite). `//heightmap import <file> <pattern> [maxHeight] [-t topPattern] [-k]`
builds terrain from a PNG stretched to fit the selection: black is the bottom of the selection, white is `maxHeight`
blocks up (default: the top of the selection). `-t` sets the surface block, and `-k` keeps what's above the new surface
instead of clearing it. Files go in the `heightmaps` folder of WorldEdit's config directory.

## Landscaping

| Command | What it does |
|---|---|
| `//path <pattern> [width] [maxSlope] [-s stairs]` (alias `//road`) | Road that follows the terrain between your selection points (pos1 → pos2, or through the points of a polygon/convex selection). It finds a gentle route, grades it so each step changes by at most one block, tunnels through hills (3 blocks of headroom), and puts pillars under bridges. `-s` puts stairs on the steps. Width is 1 to 64 blocks (default 3; 1 makes a narrow footpath). |
| `//river [width] [depth] [-l maxLength] [-b bed]` | Carves a river downhill from your position until it reaches water, `-l` blocks (default 256), or a dip, where it makes a pond. The water never flows uphill, and low banks get levees. The bed defaults to sand. |
| `//scatter <count> [minSpacing] [-k name] [-o yOffset] [-r seed] [-n] [-a]` | Places `count` randomly rotated copies of your clipboard (or named clipboard `-k`, in memory or saved with `//clipboards save`), spread out across the surface of the selection, e.g. rocks, ruins or bushes. `-n`: no rotation, `-a`: skip air, `-r`: repeatable result. The selection must reach down to the ground. |
| `//ptree <log> <leaves> [height] [branches] [leafRadius] [-l lean°] [-t 1\|2] [-r seed] [-d]` | A custom tree at your position with a leaning, curving trunk (`-t 2` for 2×2), branches ending in leaf clusters, and a crown. Leaves are made persistent unless you pass `-d`. |

## Building tools

| Command | What it does |
|---|---|
| `//retheme <from> <to> [-m mask]` | Swaps a whole material family in the selection and keeps stair facing, door hinges, sign text and so on. Examples: `//retheme oak spruce`, `//retheme oak crimson` (logs become stems, leaves become wart blocks), `//retheme cobblestone mossy_cobblestone`, `//retheme stone_brick deepslate_brick`. Blocks with no equivalent are left alone and listed. `oak` doesn't touch `dark_oak`/`pale_oak`. A material that no block has in its name (a typo such as `spruse`) is reported instead of changing nothing. |
| `//symmetry [x\|z\|xz\|off] [-p]` (alias `//sym`) | Mirrors everything you build, with commands *and* brushes, across a plane through where you stand (`-p`: through the selection centre). `xz` mirrors 4 ways. Stairs, doors etc. are flipped correctly, and one `//undo` undoes both sides. It only applies in the world where you turned it on, and is forgotten on restart. (`//mirror` was already taken by `//flip`.) |
| `//lightfill [minLight] [-b block] [-s spacing]` | Makes the selection mob-proof: adds light sources (invisible `light` blocks by default, or e.g. `-b torch`) until every spot a mob could spawn on has at least `minLight` block light (default 1). Lower `-s` spreads lights more densely. The lighting is an approximation of the game's. |
| `//find <mask> [limit] [-r radius] [-c]` | Lists the nearest blocks matching the mask in the selection (or within `-r` blocks of you) plus the total. Click coordinates to set pos1, or `[tp]` to teleport. `-c` shows which chunks have the most matches. |

## History, comparing and clipboards

| Command | What it does |
|---|---|
| `//diff [-k name] [-s] [-v]` | Compares the world with your clipboard (or named clipboard), at the spot it was copied from: how many blocks were changed, removed or added, and the most common changes. `-s` selects the area that changed. `-v` highlights differences only you can see: red = removed, lime = added, yellow = changed. `//diff clear` removes the highlights. |
| `//diff restore [-k name] [-m mask]` | Puts the clipboard's blocks back wherever they differ, e.g. to repair griefing from a saved copy. `-m` limits it to a mask. You can `//undo` it. |
| `//undo -m <mask> [times]` | Undo only the parts of your last edit(s) that match a mask, e.g. `//undo -m #region` or `//undo -m water`. A later full `//undo` or `//redo` still behaves correctly. |
| `//clipboards save\|load\|delete <name>`, `//clipboards list` | Named clipboards are now also saved to disk (`clipboards/<your uuid>/<name>.schem`), so they survive restarts. `//paste -k <name>` loads from disk if needed. Names: letters, digits, `_` and `-`, up to 32 characters. |

**Undo history survives restarts:** your last 15 edits are saved to disk (`history/<your uuid>/`), so `//undo` and `//redo`
keep working after a server restart or when you log back in. Edits are undone in the world they were made in, and
biome changes are included but entities are not. Edits over 2 million blocks aren't saved, and `//undo` stops there.
`//clearhistory` deletes the saved history too. Set `persistent-history-size` in `worldedit.properties` (0 turns it off).

## Pruning unused chunks (offline)

The command-line build of WorldEdit can shrink a world by deleting chunks players barely visited (like MCA Selector).
Run it with the server **stopped**:

```sh
./gradlew :worldedit-cli:shadowJar
java -jar worldedit-cli/build/libs/worldedit-cli-*-dist.jar prune path/to/world --min-inhabited 1200 --center 0,0 --radius 2000
```

By default it only reports what it *would* delete. Add `--apply --backup path/to/backup` to actually do it
(or `--no-backup` if you already have a backup). Chunks within `--radius` blocks of `--center` are always kept, and so
are chunks players have spent at least `--min-inhabited` ticks in (default 1200 = 1 minute). For the Nether or End, point it at
`world/DIM-1` or `world/DIM1`.

## Shapes by name (no formulas)

`//shape <name> <pattern> <size> [settings] [-h] [-b] [-f] [-r degrees] [-t degrees] [-m mask] [-a count [-l spacing | -c radius] [-s rise]]`
builds a shape around your position by name. You never type a formula: every setting is a plain number with a sensible default.

* **size** is the radius in blocks (like `//sphere`), or `x,y,z` radii to stretch it, e.g. `10,25,10` for something tall.
* **settings** are numbers, either in order (`//shape star gold_block 10 6`) or by name (`//shape star gold_block 10 points=6`).
  Anything you leave out uses its default.
* `-h` hollow, `-b` puts the bottom of the shape at your feet instead of its middle, `-f` turns it to face you,
  `-r 45` turns it 45° clockwise, `-t 90` tilts it onto its side. Flags can go before or after the settings.
* `//shapes` lists every shape (click one), and `//shapes <name>` shows its settings, their ranges and an example.
* `/brush shape <name> <pattern> [radius] [settings]` stamps the shape wherever you click.
* `//undo` works as usual.

**Carving and masks.** Use `air` as the pattern to carve the shape out of terrain instead of building it, and
`-m <mask>` so that only blocks matching the mask are changed. For example `//shape torus air 20 -m stone` bores a
ring-shaped tunnel through stone but leaves ores, water and the sky alone, and `//shape heart red_wool 10 -m air`
builds a heart without replacing anything already there. Any WorldEdit mask works (`-m !air`, `-m #existing`, ...).

**Copies (arrays).** `-a <count>` builds several copies in one go, all in one edit, so a single `//undo` removes them all:

| Flags | What you get |
|---|---|
| `-a 5 -l 30` | A line of 5 copies, 30 blocks apart, going the way you face (straight up or down if you look steeply up or down). A negative spacing goes backwards. Without `-l` or `-c` the copies are spaced by the shape's width plus a small gap. |
| `-a 8 -c 40` | A ring of 8 copies, 40 blocks from your position, starting in front of you. |
| `-a 12 -c 20 -s 4` | A rising spiral: a ring whose copies climb 4 blocks each. With `-l`, `-s` makes a staircase instead. |

Example: `//shape star gold_block 5 -f -a 6 -c 25 -s 3`. `-l` and `-c` can't be combined, and `-l`, `-c` and `-s`
need `-a`. At most 1000 copies. When the command is not run by a player (e.g. from the console), lines go east.
All of these flags may be typed before or after the settings.

**Selecting a shape.** `//selshape <name> <size> [settings] [-h] [-b] [-f] [-r degrees] [-t degrees]`
(alias `//shapesel`) selects exactly the blocks of the shape, centred on your position (or with its bottom there,
with `-b`). Then `//set`, `//replace`, `//walls`, `//faces`, `//smooth`, `//count`, `//distr`, `//copy` and other
selection commands work inside the heart, star or torus: `//selshape heart 12 -f` then `//replace stone red_wool`.
`-h` selects only the outer layer.

* This is a separate command rather than `//sel shape` because `//sel` only takes the name of a selector,
  while a shape also needs a size and settings.
* Clicking with the wand (either button), or `//pos1` / `//pos2`, moves the shape so it is centred on the clicked
  block. To go back to normal two-point selections, use `//sel cuboid`, which also turns the shape into its
  bounding box.
* `//shift` and `//move -s` move the shape selection. `//expand`, `//contract`, `//outset` and `//inset` don't
  work on it (you get a message); select the shape again with another size instead.
* `//size` shows the shape, where it is, its bounding box and the exact number of blocks. WorldEdit CUI shows the
  bounding box.
* `//sel` clears it until your next wand click, which places the same shape again.

| Group | Shapes (aliases in brackets) |
|---|---|
| Solids | `sphere` (ball), `cube` (box, cuboid), `cylinder` (cyl, upright), `cone` (point up), `pyramid` (square base, point up), `heart` (love), `star`, `torus` (donut, ring), `egg`, `crescent` (moon), `superellipsoid` (roundcube), `tetrahedron`, `octahedron`, `dodecahedron`, `icosahedron` |
| Building | `arch`, `tower` (twist, prism), `hourglass`, `hyperboloid` (coolingtower) |
| Knots and curves | `helix` (spring), `knot` (trefoil, torusknot), `dna`, `coil`, `lissajous`, `lorenz` (butterfly) |
| Surfaces | `saddle` (pringle), `ripple`, `bowl` (dish), `mobius`, `klein`, `seashell` |
| Nature | `mountain`, `volcano`, `galaxy` |
| Fractals | `mandelbulb`, `menger` (sponge), `sierpinski` |
| Lattices | `gyroid`, `schwarzp`, `schwarzd` |

Common `//generate` formulas and their shortcuts:

| Instead of this formula | Use |
|---|---|
| `(0.75 - sqrt(x^2 + z^2))^2 + y^2 < 0.25^2` | `//shape torus stone 20 ring=0.25` |
| `rotate(x, y, 0.5); (0.75 - sqrt(x^2 + z^2))^2 + y^2 < 0.25^2` | `//shape torus stone 20 -t 30` |
| `y < x^2 - z^2` | `//shape saddle stone 20 solid=1` (leave out `solid=1` for a thin sheet, e.g. a roof) |
| `y < cos(sqrt(x^2 + z^2)^2 * 10) * 0.2` | `//shape ripple water 20 waves=3 solid=1` |
| the egg formula | `//shape egg stone 10,15,10` (`pointy=0.4` for a pointier top) |
| `y = y * 5; (x - cos(y))^2 + (z - sin(y))^2 < 0.1` | `//shape helix stone 10,30,10 turns=4` (`-h` is not needed, it is already a strand) |
| `(x^2 + (9/4)*z^2 + y^2 - 1)^3 - x^2*y^3 - (9/80)*z^2*y^3 < 0` | `//shape heart red_wool 15 -f` |

More examples: `//shape knot stone 20 loops=3 winds=5`, `//shape tower quartz_block 8,30,8 sides=6 twist=120`,
`//shape mountain stone 60,35,60 roughness=0.7 seed=12 -b`, `//shape gyroid glass 20 cell=12`,
`//shape menger stone 27 level=3`, `//shape star gold_block 12 points=6 puff=1 -f`.

**Presets** save a shape with your favourite settings under a new name. Still no formulas, just the shape name and numbers:

| Command | What it does |
|---|---|
| `//shapedef save <name> <shape> [settings]` | e.g. `//shapedef save bigknot knot loops=3 winds=5 tube=2`, then `//shape bigknot stone 25`. Settings can still be changed when you use it. |
| `//shapedef delete <name>` | Delete a preset. |
| `//shapedef list` | List presets (they also show up in `//shapes`). |

Presets are shared by everyone on the server and saved in `shape-presets.txt` in WorldEdit's config folder.

## Long-distance /jumpto

`/jumpto` (and right-clicking with the navigation compass) now reaches anything you can see, up to 2048 blocks away
by default, instead of stopping at 300 blocks.

* It follows your crosshair block by block, so it can't slip through a thin wall or corner.
* Chunks that aren't loaded but already exist are read from disk. Chunks that have **never been generated**
  (for example far-away terrain drawn by a distant-terrain mod such as Distant Horizons) aren't generated just to
  look through them. Instead the world generator's terrain height is used to find where your crosshair meets the
  ground, and only the chunk you land in is generated. This ignores trees and buildings there, because they don't
  exist yet. On Bukkit/Paper, never-generated chunks are looked through, since there's no way to ask the generator
  for the height.
* Set the range with `jumpto-max-distance` in `worldedit.properties` (or `navigation.jumpto-max-distance` in
  `config.yml`). The compass still uses its own `nav-wand-distance` (default 50), so raise that too if you want
  the compass to reach as far.

## Warping the selection by name (no formulas)

`//warp <name> [settings] [-c]` reshapes everything in your selection: twist it, bend it, make it wavy, melt it.
It replaces `//deform` formulas. Settings are plain numbers (angles in degrees) with defaults, given in order
(`//warp wave 4 20`) or by name (`//warp wave amplitude=4 wavelength=20`). Axis settings take `x`, `y`, `z` or `auto`.

* Each block takes the original block from where the warp says it came from, so leave some empty room around the
  thing you warp (e.g. select a tower plus a few blocks of air to each side before leaning it).
* Anything that would come from outside the selection becomes air. `-c` pulls in the real blocks from outside
  instead, which suits terrain.
* `//warps` lists the warps, and `//warps <name>` shows the settings, their ranges and an example.
* `//undo` works as usual.

| Warp (aliases) | Settings (defaults) | Example |
|---|---|---|
| `twist` (spin, corkscrew) | `angle` 90: degrees the top turns, clockwise seen from above; the bottom stays put | `//warp twist 180` |
| `bend` (curve, arc) | `angle` 90, `axis` auto (the longer side, x or z), `vertical` 0 (1 = bend up/down; negative angle makes an arch) | `//warp bend -120 vertical=1` |
| `taper` (narrow, pinch) | `top` 0.5 and `bottom` 1: width at the top and bottom as a share of the original | `//warp taper 0.2` |
| `wave` (waves, ripple) | `amplitude` 3 blocks, `wavelength` 16 blocks, `axis` auto (`y` wiggles it sideways going up), `phase` 0 degrees | `//warp wave 4 20 x` |
| `shear` (lean, slant) | `angle` 30 degrees from upright, `axis` auto (x or z) | `//warp shear 20 z` |
| `spherize` (round, ball) | `amount` 1 (0 = no change, 1 = a ball that fits the selection, negative = boxier) | `//warp spherize 0.7` |
| `inflate` (puff, bulge) | `amount` 3 blocks to push out from the middle (negative pulls in) | `//warp inflate 2` |
| `melt` (droop, sag) | `amount` 0.5, `noise` 0.5 (uneven drips), `spread` 0.5 (puddle at the bottom), `seed` 0 | `//warp melt 0.7 noise=0.9 seed=5` |

To mirror or flip, use the existing `//flip` and `//rotate` on a copy.

## Layout patterns (no formulas)

These patterns mix two or more block patterns in a layout. Inside the brackets list the block patterns (any pattern
works, including block states such as `oak_log[axis=x]`), then the settings: plain numbers in order, `name=value`, or
`x`/`y`/`z` for the axis. All but `#bricks` and `#dither` take more than two blocks and cycle through them.

| Pattern | Settings (defaults) | Example |
|---|---|---|
| `#stripes[a,b,...]` | `width` 1, `axis` y (y = flat layers, x/z = upright stripes) | `//set #stripes[white_wool,red_wool,2,x]` |
| `#checker[a,b,...]` | `size` 1 (cubes, so it works on walls and floors) | `//set #checker[black_concrete,white_concrete,2]` |
| `#rings[a,b,...]` | `width` 2, `axis` y (rings seen from above) | `//set #rings[stone,andesite,diorite,3]` |
| `#spiral[a,b,...]` | `arms` 1 per block, `curl` 16 (blocks an arm moves out per turn; 0 = spokes), `axis` y | `//set #spiral[white_wool,black_wool,3]` |
| `#diagonal[a,b,...]` | `width` 1 | `//set #diagonal[yellow_concrete,black_concrete,2]` |
| `#bricks[brick,mortar]` | `length` 3, `height` 1, `axis` y (rows stack along it; x or z to pave a floor) | `//set #bricks[bricks,smooth_stone,4,2]` |
| `#dither[a,b]` | `mix` (share of `b`, 0 to 1), `axis` y | `//set #dither[stone,grass_block]` |

* Rings and spirals are centred on the middle of your selection; with no selection, on your position.
* Bricks run in a running bond: 1-block mortar lines between rows, and each row is shifted by half a brick. They
  follow both x and z, so the corners of a building line up.
* `#dither[a,b]` blends evenly from `a` at the bottom of the selection to `b` at the top using an ordered (Bayer)
  dither; `#dither[a,b,x]` blends along x instead, and `#dither[a,b,0.3]` mixes in a fixed 30% of `b` everywhere
  (no selection needed).

## Building parts, block letters and pixel art

Like `//shape`, these take names and plain numbers only (angles in degrees), and everything can be `//undo`ne.
"Facing you" means the cardinal direction you are looking in (north if a console or script runs the command).

**`//build <part> <pattern> [settings] [-w pattern] [-s stair block]`** builds a ready-made part at your placement
position. Settings are numbers in order or `name=value`; `//builds` lists the parts (click one) and `//builds <part>`
shows its settings, ranges and an example. `-w` fills windows, panes and railings; `-s` adds stairs to spiral steps.
Flags can go before or after the settings.

| Part (aliases) | Settings (defaults) | Notes |
|---|---|---|
| `spiralstair` (spiral) | `radius 4`, `height 16` steps, `turns 1`, `pole 1`, `clockwise 1` | First step is in front of you. `-s oak_stairs` puts stairs, facing up the spiral, on the front edge of each step. |
| `dome` (cupola) | `radius 10`, `thickness 1`, `windows 0` | Half sphere; windows round the bottom, filled with `-w` (default glass). |
| `tower` (turret) | `radius 4`, `height 20`, `battlements 1`, `windows 4` per floor, `floors 5` (blocks apart, 0 = none), `thickness 1` | Each floor has a hatch by the wall; windows `-w` (default glass, `-w air` for openings). |
| `wall` (castlewall) | `height 6`, `thickness 2`, `crenellations 1` | Runs from `//pos1` to `//pos2`, following the slope between them. |
| `column` (pillar) | `radius 1`, `height 10` | Square plinth and abacus, round rings at the base and capital. |
| `roundwindow` (rosewindow) | `radius 6`, `spokes 8`, `depth 1`, `ring 1` | Upright, centred on the placement position, facing you. Panes `-w` (default glass). |
| `bridge` (archbridge) | `width 3`, `arch 4`, `railings 1`, `thickness 1` | Arched deck from `//pos1` to `//pos2`; railings use `-w` (e.g. `-w oak_fence`) or the main pattern. |

```
//build spiralstair oak_planks radius=5 height=24 turns=1.5 -s oak_stairs
//build tower stone_bricks 6 30 windows=6 -w glass_pane
//build dome white_concrete radius=12 windows=8 -w light_blue_stained_glass
//build wall cobblestone height=8 thickness=3        (after //pos1 and //pos2 at the two ends)
//build bridge oak_planks width=5 arch=6 -w oak_fence (after //pos1 and //pos2 on the two banks)
//build roundwindow stone_bricks 8 spokes=12 -w red_stained_glass
```

**`//text <pattern> <text> [-s scale] [-d depth] [-f]`** builds block letters (built-in 5x7 font: A-Z, 0-9 and
`. , ! ? : ; ' " - + = / \ ( ) [ ] _ # & * < > @ % $ ^ |`; lower case is built as capitals, other characters as `?`).
The text starts at your placement position and reads left to right as you look at it. `-s 2` makes every pixel
2x2 blocks, `-d 3` makes the letters 3 blocks thick, `-f` lays it flat on the ground (top of the letters away from you).
Example: `//text red_wool Welcome! -s 2 -d 2`.

**`//image <file> [width] [-f] [-p palette]`** builds pixel art from a PNG/JPG/GIF/BMP in the `images` folder inside
WorldEdit's folder (`//image list` shows them). It is resized to `width` blocks wide (default: the image width, at most 128),
keeping its shape, and each pixel becomes the block whose colour looks closest (CIEDE2000 colour difference); transparent
pixels are skipped. It stands upright facing you with its bottom-left corner at the placement position; `-f` lays it flat.
Palettes for `-p`: `all` (default), `wool`, `concrete`, `terracotta`, `planks`, `stone`, `other`, or several joined
with commas. Example: `//image logo.png 64 -p wool,concrete`.

## Scaling, rings and paths for the clipboard

Plain numbers only, and every paste is a single `//undo`. Any `//rotate` or `//flip` on the clipboard is applied first.

**`//scale <factor | x,y,z> [-a fraction] [-k name]`** (alias `//resize`) resizes the clipboard by any factor:
`//scale 2.5`, `//scale 0.5`, or `//scale 1,2,1` to make it only taller. It scales around the clipboard origin (where
you stood when copying), so `//paste` lands in the same place, just bigger or smaller. Every new block looks up the
block it came from, so enlarging leaves no holes. When shrinking, each new block covers several old ones; it becomes
solid when at least a quarter of them are solid (`-a 0.5` asks for half, `-a 0.1` keeps even more detail) and takes
the most common solid block, so 1-block walls and posts survive halving. Stairs, logs and other blocks keep their
state; chest contents and sign text are kept on one copy of each block only (no duplicated items). Entities and
biomes are not scaled. The result may be at most 64M blocks. With `-k name` the named clipboard is scaled and
updated (and becomes the current clipboard).

**`//radial <count> [radius] [-a] [-k name]`** (alias `//ring`) pastes the clipboard `count` times evenly around your
placement position, like spokes: copy number k is the normal `//paste` turned k x 360/count degrees clockwise (seen
from above) around you, so the first copy is exactly where `//paste` would put it and every copy faces outward the
same way. Without a radius each copy keeps the distance it has from you when pasting; with a radius the centre of
each copy is moved straight out (or in) to that many blocks from you. Example: copy a bench standing at the edge of a
plaza, then `//radial 6`. Angles that are not multiples of 90 degrees are filled without holes, but stairs and other
directional blocks turn to the nearest direction they support (e.g. 45 degrees snaps to a side), and 1-block-thin
diagonal lines get slightly shorter. `-a` skips air.

**`//stackpath <spacing> [-a] [-r] [-g] [-k name]`** (alias `//pathstack`) repeats the clipboard along a smooth path
through your selection points, one copy every `spacing` blocks of path length, starting at the first point. The path
goes through the points of a `//sel poly` (at its lowest y) or `//sel convex` selection in the order you clicked them
(points inside the convex hull may be reordered), or from pos1 to pos2 for a cuboid, smoothed like `//curve`. Each
copy's origin goes on the path, as if you had pasted there. `-r` turns each copy to follow the path: the first copy is
as copied and later copies turn as much as the path has turned. `-g` moves each copy up or down so its lowest solid
layer sits on the highest ground under it (searching down from the top of the copy; grass and air are not ground).
`-a` skips air. Example: copy a lamp post, `//sel convex`, click points along a road, `//stackpath 8 -g -a`. At most
10,000 copies.

## Smarter selections (magic wand, whole builds, walking a polygon)

No more clicking dozens of `//sel poly` points round an irregular build.

**`//selconnected <mask> [maxDistance] [-d] [-p]`** (aliases `//magicwand`, `//selc`) works like a paint program's
magic wand. It starts from the block under your feet if that matches the mask, otherwise from the block you look at
(up to 512 blocks away; the message says which was used). From the console it starts at `//pos1`. It then spreads
through touching blocks that match the mask (any mask: `gravel`, `stone_bricks,stone_brick_stairs`, `#existing`...).
Blocks are connected through their faces; `-d` also follows blocks that only touch at an edge or corner (zig-zag
paths, diagonal stairs). The selection is **exactly those blocks**, so `//set`, `//replace`, `//copy`, `//count`,
`//walls` and so on affect only them. It reports the block count and the size of the bounding box.

```
//selc gravel                       the gravel path you are standing on
//selc stone_bricks,stone_brick_stairs -d     a wall with its stair trim
//selc water 64                     the pond you look at, at most 64 blocks out
```

**`//selbuild [maxDistance] [-g] [-p]`** selects the whole build you are looking at (console: at `//pos1`), with all
its decorations: it spreads through every non-air block touching another (also at edges and corners), but not into
natural terrain. `-g` adds the one layer of ground touching the build (the blocks under and beside it), which is handy
before `//copy` or `//move`. Natural terrain is a fixed list: grass block, dirt, coarse dirt, rooted dirt, podzol,
mycelium, mud, stone, deepslate, andesite, diorite, granite, tuff, calcite, bedrock, gravel, sand, red sand, clay
(and their suspicious variants), snow blocks and powder snow, water, lava, bubble columns, short/tall grass, ferns,
dead bushes, dry grass, seagrass, kelp, flowers (including petals, wildflowers, eyeblossoms, leaf litter), every
`*_ore`, netherrack, soul sand/soil, nylium, end stone and ancient debris. Deliberately **not** natural: leaves and
logs (hedges, timber framing and garden trees would be cut off; a tree touching the build is therefore included),
sandstone and cobblestone (common building blocks), snow layers (they lie on roofs and paths), ice, moss and basalt.
A build that touches a cliff of one of those materials will spread into it: limit the distance or trim afterwards.

**`-p`** (on both commands) turns the result into a normal `//sel poly` selection for tools that need one: the outline
follows the outer edge of the blocks' footprint seen from above (concave where the footprint is, e.g. L- or U-shaped
houses; courtyards are filled), from the lowest to the highest selected block. If the outline has more points than
polygons may have on the server (`max-polygon-points`, 20 by default), it is simplified (Douglas-Peucker) so that it
still goes around every block where possible; otherwise the message says how many edge columns were left outside.

Both commands stop at `maxDistance` blocks from the start along any axis, and at a total number of blocks. Defaults and
hard caps come from the config: `max-connected-selection-distance` (256) and `max-connected-selection-blocks`
(2,000,000) (in `worldedit.yml` under `limits:`, or `worldedit.properties`). Blocks already visited are kept in a
compact bit set (16x16x16 sections), so even large selections need little memory. After a `//selconnected` or
`//selbuild`, your next wand click (or `//pos1`/`//pos2`) starts a fresh selection of the type you were using before,
so if you were in `//sel poly` mode you are straight back to clicking polygon points.

**`//polywalk`** makes a `//sel poly` by walking around a build:

| Command | What it does |
|---|---|
| `//polywalk` or `//polywalk start [spacing]` | Start recording. Every time you have walked `spacing` blocks (default 4) horizontally from the last point, a point is added where you stand, and your polygon selection grows as you go (WorldEdit CUI and `//size` show it). |
| `//polywalk point [x,y,z]` | Add a point here now (for corners; these are never simplified away), or at the given position. |
| `//polywalk undo` | Remove the last point. |
| `//polywalk stop [-y min,max]` | Finish: drop points that are nearly on a straight line, close the loop, and select from 1 block below the lowest place you walked to the highest non-air block inside the outline (or the `-y` range). Prints the point count and size. |
| `//polywalk cancel` | Throw the recording away. |

Recording stops by itself (nothing is selected) when you log out or change world. Point sampling runs from the server
tick (a general per-tick hook, `WorldEdit#getTickListeners()`, called next to the edit job queue); it is only registered
while someone is walking. The command line has no walking player, so there only `//polywalk point x,y,z` adds points.

## Modelling from drawings and combining shapes

Draw a flat outline in blocks (any blocks, several materials are fine) and turn it into 3D, the way 3D modelling
tools do. A "drawing" is the non-air blocks of your selection, standing upright like a wall (flat along x or z) and
at most 2 blocks thick. Every block of the result looks up where it came from in the drawing, so there are no holes
and each block keeps its material (a vase drawn with a gold rim stays gold-rimmed).

| Command | What it does |
|---|---|
| `//lathe [degrees] [-c] [-r]` (alias `//spin`) | Spins the drawing around a vertical axis: vases, domes, towers, fountains. |
| `//extrude <length> [twist] [taper] [-d direction]` (alias `//pull`) | Pulls the drawing straight out, optionally twisting and tapering it. |
| `//sweep <profile> [pattern] [width] [settings] [-h] [-k]` | Drags a cross-section along a smooth path through the selection's points. |
| `//combine add\|cut\|keep\|smooth\|list\|undo\|clear\|build\|select` | Builds compound shapes from named shapes. |

**`//lathe`** (the name `//revolve` was already taken by WorldEdit's "paste rotated copies" command). Draw half of
the outline (or all of it), select it, **stand where the middle of the model should be** and run `//lathe`. The axis is
the vertical line through where you stand (your placement position), in the plane of the drawing; it does not matter
how far in front of or behind the drawing you stand. With `-c` the axis is instead the edge of the drawing nearest to
you, so you can stand anywhere on that side. Parts of the drawing on the far side of the axis are spun too, so a whole
drawn vase works as well as half of one. An angle such as `//lathe 180` spins only part of the way, clockwise seen
from above. The drawing stays where it is unless you add `-r`. Example: draw the right half of a vase in the plane
z = 100 with its centre line at x = 50, stand at x = 50, `//lathe`.

**`//extrude`** pulls the drawing along the axis it is thin in: away from you for an upright drawing, and upwards for a
drawing lying on the ground (a floor plan). `-d north|south|east|west|up|down|me` picks the direction (it must be the
axis the drawing is thin in). `twist` is how many degrees the far end is turned, `taper` how big it is (0.3 = a point,
2 = twice as big). Example: `//extrude 20 90 0.3` turns a drawn star into a twisted spire.

**`//sweep`** follows a path through the selection: pos1 to pos2 for a cuboid, or the points in order for
`//sel poly` (at the polygon's lowest y) or `//sel convex`, smoothed into a curve. The cross-section stays upright
(level left-to-right) along the whole path. `width` is in blocks (default 5); the pattern defaults to stone.
Profiles: `circle` (tube), `square` (beam; `height=`), `arch` (tunnel or aqueduct standing on the path, open at the
bottom; `height=`, `thickness=`), `ring` (pipe; `thickness=`), `rail` (two rails on sleepers; `spacing=` between
sleepers). `circle`, `square` and `ring` are centred on the path; `arch` and `rail` stand on it.
`-h` makes circles and squares hollow with open ends. `-k` (or the profile name `clipboard`) uses the flat drawing in
your clipboard as the cross-section, keeping its blocks; the path runs through the spot you stood on when you copied
it, and the drawing is used as you see it looking along the path (use `//flip` on the clipboard if it comes out
mirrored). Examples: `//sweep arch stone_bricks 9 height=8`, `//sweep ring glass 5 thickness=1`,
`//sweep rail oak_planks 3 spacing=2`, `//sweep clipboard`.

**`//combine`** builds a compound shape from the shapes of `//shape`, one step at a time:

* `//combine add <shape> <size|x,y,z> [settings] [-o x,y,z] [-r deg] [-t deg]` adds a shape (union). The first shape
  is centred where you stand; `-o` puts later shapes that many blocks from the first shape's middle (x east, y up,
  z south). `-r` turns and `-t` tilts it.
* `//combine cut ...` carves a shape away (subtract) and `//combine keep ...` keeps only the overlap (intersect).
  Steps apply in order.
* `//combine smooth <blocks>` blends every join into a rounded fillet about that wide (0 = sharp). It works on
  distance fields of the shapes, so it also rounds the edges of cuts.
* `//combine list`, `//combine undo` (remove the last step), `//combine clear`.
* `//combine build <pattern> [-h] [-m mask]` builds it where you stood when you added the first shape;
  `//combine select` selects it instead, so `//set`, `//replace`, `//count` work inside it.

Example: `//combine add superellipsoid 8 power=2`, `//combine add tower 3,10,3 -o 0,10,0`, `//combine smooth 3`,
`//combine build stone`. Each player has one compound shape; it is kept in memory only and is lost when the server
restarts.

## Skyscrapers and building styles

**`//skyscraper <style> <stories> [settings] [role=block ...]`** builds a skyscraper with its entrance facing you
(north for the console): the middle of the front wall is 3 blocks in front of your placement position and the tower
goes away from you. Settings are plain numbers, in order or as `name=value`:

| Setting | Default | Meaning |
|---|---|---|
| `width`, `depth` | 15, 15 | Footprint (5-256). `width` runs across the entrance side. |
| `story` | 4 | Blocks per story (3-12); the lobby is one block taller. |
| `setbacks` | style | Times the tower steps inward going up (0-6), art-deco style. |
| `taper` | 0 | Blocks each side narrows evenly from bottom to top. |
| `shape` | style | `box`, `rounded` (rounded corners), `round` (cylindrical), `l` (L-shaped), or 0-3. |
| `crown` | style | `flat` (parapet and rooftop plant room / water tower), `spire` (spire and antenna), `stepped` (three windowed tiers), `pyramid`, or 0-3. |
| `seed` | random | Rooftop details. |
| `interior` | 1 | 1 = floors with a central stairwell core (a stair spiralling round a 3x3 shaft, doorways on every floor), 0 = floor slabs only. Rooms are cleared to air either way. |

Every story gets a floor slab with ceiling lights; the lobby has a glass front with pillars, a 3-wide door with a
frame and a lit canopy. Each style family has its own look:

| Style | Look (default shape / crown / setbacks) |
|---|---|
| `modern` | White ribbon windows (box / flat / 0) |
| `art_deco` | Piers between vertical window strips, dark spandrels, gold details (box / stepped / 3) |
| `futuristic` | All-round glass with glowing accent bands (round / spire / 0) |
| `gothic` | Buttresses and pointed lancet windows, trim bands (box / spire / 1) |
| `brutalist` | Heavy fins and concrete bands with small windows (box / flat / 1) |
| `glass` | Curtain wall with thin mullions (rounded / flat / 0) |
| `brick` | Classic NYC punched windows and a rooftop water tower (box / flat / 0) |
| `sandstone` | Arched windows between columns (box / pyramid / 1) |

A style is a set of blocks for the roles `wall`, `window` (glass), `trim`, `pillar`, `floor`, `roof`, `accent` and
`light`. Change any of them for one building with `role=block` (any pattern works, e.g. `wall=80%bricks,20%granite`),
or save your own style:

* `//style list` — built-in and saved styles (click one for its blocks)
* `//style info <style>` — the block for every role
* `//style save <name> <base style> [role=block ...]` — saved in `building-styles.txt` in WorldEdit's folder
  (one line per style, `name = base role=block ...`); a saved style keeps its base style's look
* `//style delete <name>`

```
//skyscraper art_deco 40 width=25 depth=25
//skyscraper glass 60 width=31 depth=21 crown=spire taper=4
//skyscraper brick 12 shape=l window=cyan_stained_glass
//style save harbour art_deco window=cyan_stained_glass accent=copper_block
//skyscraper harbour 30 crown=pyramid
```

The style system is a plain reusable class (`function/generator/style`: `BuildingStyle`, `StyleRole`, `StyleLibrary`)
for other generators to use.

## Nature: rocks, crystals, giant mushrooms, roots and coral

Each takes a block pattern, a size and plain-number settings (in order or `name=value`). The size is a radius, or
`x,y,z` radii like `//shape`: things stand on the placement position, centred on it, at most `2x+1` by `2z+1`
blocks across and `2y` tall (roots hang down `2y` from it). `seed` makes the result repeatable; without it a random
seed is used and shown in the message, so you can build the same thing again. Nothing is left floating: only the
largest connected piece is kept. Types can be given by name.

| Command | Settings (defaults) | Notes |
|---|---|---|
| `//rock <pattern> <size>` (`//boulder`) | `roughness 0.5` (0 smooth - 1 jagged), `seed` | A few overlapping lumps deformed by noise, faint weathered ledges, flat base. Mixed patterns (`stone,andesite,cobblestone`) look natural. |
| `//crystals <pattern> <size>` (`//geode`) | `count 7` (1-40), `seed` | Pointed hexagonal prisms of different lengths and tilts growing from a small base; the tallest reaches about `2y`. |
| `//giantmushroom <stem> <cap> <size> [-u underside]` | `type round` (`round`, `flat`, `bell`), `seed` | Slightly curved stem; round = red-mushroom dome (hollow), flat = wide brown disc, bell = tall cone. `-u` fills the gills under the cap. Size = cap radius; height `2y`. |
| `//roots <pattern> <size>` | `count 6` (1-24), `seed` | Twisting, tapering roots that branch and spread out and down from the placement position (under trees or floating islands). |
| `//coral <pattern> <size>` (`//reef`) | `type branching` (`branching`, `brain`, `fan`, `tubes`), `seed` | Branching: tree-like; brain: a wrinkled ball with winding grooves; fan: a flat, one-block lattice; tubes: a cluster of hollow pipes. |

```
//rock stone,andesite,cobblestone 6 roughness=0.8
//rock mossy_cobblestone,stone 10,4,7 seed=12
//crystals amethyst_block,purple_stained_glass 6 count=9
//giantmushroom mushroom_stem red_mushroom_block 8 round
//giantmushroom mushroom_stem brown_mushroom_block 12,8,12 flat -u mushroom_stem
//roots oak_wood,rooted_dirt 8 count=7
//coral brain_coral_block 4 brain
//coral tube_coral_block,horn_coral_block 6 tubes seed=3
```

## Pasting onto terrain and in grids

**`//paste -c [-r radius] [-f] [-w]`** (conform paste) makes every vertical column of the clipboard follow the
terrain under it, so roads, paths, walls, fences and gardens drape over hills instead of floating or being buried.
The clipboard's bottom layer (its lowest layer that has any non-air block) is put on the ground in every column, and
the rest of that column moves with it, so things that float above the bottom layer in the clipboard (an arch, a lamp
arm) keep their height above it. Each column rests on the ground surface nearest to where a plain `//paste` would put
it: if that spot is inside the ground it climbs to the top, otherwise it looks down. Ground is any solid block except
leaves, logs and other tree parts, and snow layers; grass, flowers and air are skipped. Water is not ground (a path
across a river goes along the river bed); with `-w` water surfaces count as ground, so the path crosses on top. The
column heights are smoothed over `-r` blocks around each column (default 1, 0 = follow the terrain exactly, at most
8), so a 3-wide road loses single-block bumps, but a column is never lifted above the ground (nothing floats) and
sinks at most one block into it (it replaces a bump's top block). `-f` fills any air under the bottom block of each
column down to the ground with that block, e.g. under the doorway of a wall or under columns whose own bottom is
higher than the clipboard's bottom layer. Works with `-a`, `-k <name>`, `-s`/`-n`, `-v`, `//rotate` and `//flip`, and
is one `//undo`. `-m` works unless the clipboard is rotated or flipped; `-e` and `-b` are ignored with `-c`. Columns
with no ground at all (off the edge of the world) take the height of the nearest column that has ground. Example:
copy a 3-wide, 1-high strip of path blocks, then `//paste -c -a` across a hill.

**`//grid <columns> <rows> [spacing] [row spacing] [-s] [-a] [-r] [-d seed] [-g] [-y rise] [-k name]`** repeats the
clipboard in a grid on the ground plane: `columns` copies to your right and `rows` copies away from you (along the
nearest world axis; from the console, columns go along +x and rows along +z). `spacing` is the gap in blocks between
copies (default 0 = touching); the row spacing defaults to the same. Example: copy a 20x20 city block and
`//grid 5 5 6` for 25 blocks with 6-wide streets. The first copy goes where `//paste` would put it. With `-s` the
selection is repeated instead (like `//stack`, air included unless `-a`); the selection itself is the first copy and
is left as it is. `-r` turns each copy a random multiple of 90 degrees for variety and prints the seed; `-d <seed>`
repeats a layout (and implies `-r`). With `-r`, copies that are not square get square cells as wide as their longest
side, and each copy is centred in its cell. `-g` drops (or lifts) each copy so its lowest solid layer sits on the
ground under it, like `//stackpath -g`. `-y <rise>` raises each row by that many blocks, for terraces up a hill
(-64 to 64). At most 10,000 copies and 50 million blocks in total. One `//undo` removes the whole grid.

## Saved patterns and masks, and the history browser

**Saved patterns.** Give any pattern a name once and use it as `#name` everywhere a pattern is accepted: `//set`,
`//replace`, brushes, `//shape`, and inside other patterns.

```
//pattern save oldstone 50%stone_bricks,30%cracked_stone_bricks,20%mossy_stone_bricks
//set #oldstone
//shape heart #oldstone 10
//set #stripes[#oldstone,glass,2]
//pattern save wall #stripes[#oldstone,glass,2]
```

| Command | What it does |
|---|---|
| `//pattern save <name> <pattern>` | Saves (or replaces) a pattern. It is checked first; a pattern that doesn't work is refused with the reason. |
| `//pattern delete <name>` | Deletes it (and warns if other saved patterns used it). |
| `//pattern list` | Lists the saved patterns. Click one to put `//set #name` in your chat box, shift-click to insert `#name`. |
| `//pattern info <name>` | Shows the pattern, whether it works right now and which saved patterns use it. |
| `//mask save\|delete\|list\|info ...` | The same for masks, e.g. `//mask save ores coal_ore,iron_ore,copper_ore` then `//replace #ores stone`. Masks may contain spaces. |

* Names use letters, digits, `_` and `-` (up to 32 characters, not case sensitive). Names of built-in `#` patterns
  and masks (`#clipboard`, `#stripes`, `#gradient`, `#region`, ...) can't be used.
* Saved patterns can use other saved patterns. A pattern that ends up using itself (`a` uses `b`, `b` uses `a`) gives an
  error instead of hanging.
* They are shared by everyone on the server and kept in `saved-patterns.txt` / `saved-masks.txt` in WorldEdit's folder.
* `#name` is offered when tab-completing patterns and masks.

**History browser.** `//history [page]` (alias `//edits`) lists your recent edits, newest first: what made each one
(e.g. `//set stone`, `brush (sphere)`), how many blocks changed, how long ago, and whether it is undone. Click
`[undo to here]` to undo that edit and every newer one, or `[redo to here]` on an undone edit to redo up to it.
`[info]` shows where the edit is and how big it is, and players get `[tp]` to go to its middle. Without clicking:
`//history undo 3`, `//history redo 2`, `//history info 1`, `//history tp 1` (1 = newest). Edits restored from disk
after a restart are listed as "edit" with "? blocks" because only the blocks were saved; `[info]` still shows where
they are.

## Ageing builds: ruins and overgrowth

Both work on the selection, take `amount` (0-1, default 0.5) and `seed`, in order or as `name=value`, and are one
undoable edit. Without a seed a random one is used and shown in the message, so the same result can be rebuilt.
Amount 0 changes nothing.

| Command | What it does |
|---|---|
| `//ruin [amount] [seed] [-r] [-g]` (`//decay`) | Walls and roofs crumble in whole chunks (smooth noise), more towards the top and outer edges of the build, floors and roofs over open space more still; the foundations mostly stay. Anything no longer connected to the ground falls. What is left weathers: stone bricks crack and get mossy (stairs and slabs keep their facing), cobblestone gets mossy, planks rot away in patches, logs get stripped, glass mostly breaks, doors, torches, lanterns, beds, carpets and the like go, copper oxidises. Fallen blocks pile up at the foot of the walls as cobblestone, gravel or the block itself, smoothed with slabs (`-r`: no rubble). `-g` adds grass, dirt and moss patches on flat surfaces. |
| `//overgrow [amount] [seed] [-n] [-l]` (`//ivy`) | Vines hang down walls from where they start (more on north faces and in the shade under overhangs), leaf bushes gather at the foot of walls and in corners, moss carpet covers tops, grass, ferns and flowers grow on soil (`-n`: no flowers), hanging roots and cave vines hang under overhangs, glow lichen grows in dark corners (`-l`: none). Growth comes in clumps. Plants only fill air; above amount 0.6 some exposed stone and cobblestone tops become moss blocks. |

The ground is the natural blocks (dirt, grass, stone, sand...) from the bottom of the selection up; everything above
is the build, so include the ground under the build in the selection. Natural blocks are never knocked out.

```
//ruin
//ruin 0.8 seed=12 -g
//overgrow 0.7 -n
```

The logic is in plain classes (`function/generator/age`: `RuinGenerator`, `OvergrowGenerator`, `AgeMaterials`)
working on a small block interface (`AgeWorld`).

## New commands

| Command | What it does |
|---|---|
| `//helix <pattern> <radius>[,<radiusZ>] <height> [turns] [thickness] [-h]` | Spiral climbing up from your position: radius at least 1, height 1-1024, turns 0.25-200, thickness 0 = one block. `//hhelix` is the hollow version. For several strands see `//shape helix`. Example: `//helix oak_planks 6 20 3`. |
| `//torus <pattern> <ring radius> <tube radius> [-h]` | Donut lying flat around your position; the tube radius must be smaller than the ring radius. `//htorus` is the hollow version. To tilt or stretch it see `//shape torus`. Example: `//torus stone 10 3`. |
| `//catenary <pattern> [sag] [thickness] [-h]` | Hanging rope/chain between pos1 and pos2 (or between convex-selection vertices). `sag 0` picks a sag automatically. Example: `//catenary chain 4`. |
| `//strata <p1,p2,...> [thickness] [tilt°] [direction°] [waviness]` | Fills the whole selection (air included) with repeating rock layers. Tilt is how steeply they tilt (0 = flat, 90 = upright), direction is which way, in degrees clockwise from north, waviness 0 (straight) to 5. Example: `//strata stone,andesite,granite 3 20 90 1`. |
| `//erode [passes]` | Wears down steep slopes: in each pass (1-50) the top block of a column 2+ blocks above a neighbour slides down to the lowest neighbouring column. Stops early when nothing more can slide. |
| `//weather [severity 0-1] [-m] [-c]` | Cracks stone/deepslate/blackstone bricks and deepslate tiles. `-m` also makes cobblestone and stone bricks (and their stairs, slabs, walls) mossy, `-c` ages unwaxed copper by one stage. Other blocks are left alone; if nothing changed it says what it works on. |
| `//autostairs <stair pattern> [slab pattern] [-u]` | Turns step edges in the selection into correctly facing stairs (and slabs). `-u` handles ceilings. A pattern without a `facing` property (e.g. `stone`) is refused. |
| `//roof [gable\|hip\|shed\|curved\|mansard] <pattern> [overhang] [pitch] [-h]` | Builds a roof over the selection footprint. Type and pattern may come in either order (type defaults to gable); an unknown type or a missing pattern is reported. Overhang 0-32 blocks; pitch up to 10 (1 = 45 degrees). On a flat selection (1-2 blocks high) the roof sits on top and rises `pitch` blocks per block inwards; on a taller selection it starts at the bottom and is `pitch` times the selection's height. Example: `//roof hip spruce_planks 1 0.5`. |
| `//insetfaces [margin] <pattern> [depth]` | Recessed panels on the selection's six faces, with a `margin`-wide border; `depth` 0 = flush. A margin too big for the selection is refused with the largest that fits. |
| `//measure [x,y,z]` | Size, distances, angles, volume and surface area of the selection (pos1 to pos2), or from pos1 to a point. Sizes count both end blocks in both modes (like `//size`); distances are between block centres. |
| `//copy -k <name>`, `//paste -k <name>` | Copy into or paste from a named clipboard. |
| `//clipboards [list\|save\|load\|delete] [name]` | Manage named clipboards (saved to disk, so they survive restarts). |

## New patterns

* `#gradient[axis][palette]` or `#gradient[axis,min,max][palette]`: dithered blend along `x`, `y` or `z`.
  Without bounds it uses your current selection. Example: `//set #gradient[y][stone,andesite,cobblestone]`
* `#voronoi[scale][palette]`: cellular, crazy-paving-style pattern. Example: `//set #voronoi[4][stone,andesite,tuff]`

## New masks

* `#slope`, `#slope[min,max]`: surface blocks whose terrain slope is within the angle range in degrees. The default is 35-90.
* `#adjacent[mask]`, `#adjacent[count,mask]`, `#adjacent[min,max,mask]`: blocks with the given number of touching
  neighbours matching `mask`. Example: `//replace #adjacent[water] sand`
* `#exposed[up|down|north|south|east|west|horizontal]`: like `#exposed` but only checks the given side(s).

## Permissions

On servers with a permissions plugin, the new nodes are:

* **Shapes and generators:** `worldedit.generation.shape.named` (`//shape`, `//shapes`),
  `worldedit.generation.shape.preset` (saving and deleting presets with `//shapedef`),
  `worldedit.generation.helix` (`//helix`), `worldedit.generation.torus` (`//torus`),
  `worldedit.generation.combine` (`//combine`), `worldedit.brush.shape` (`/brush shape`)
* **Building parts:** `worldedit.generation.build` (`//build`, `//builds`), `worldedit.generation.text` (`//text`),
  `worldedit.generation.image` (`//image`), `worldedit.generation.skyscraper` (`//skyscraper`, `//style list|info`),
  `worldedit.generation.skyscraper.style` (`//style save|delete`)
* **Modelling:** `worldedit.generation.lathe` (`//lathe`), `worldedit.generation.extrude` (`//extrude`),
  `worldedit.generation.sweep` (`//sweep`), `worldedit.region.warp` (`//warp`, `//warps`)
* **Nature:** `worldedit.generation.rock` (`//rock`), `worldedit.generation.crystals` (`//crystals`),
  `worldedit.generation.giantmushroom` (`//giantmushroom`), `worldedit.generation.roots` (`//roots`),
  `worldedit.generation.coral` (`//coral`), `worldedit.generation.ptree` (`//ptree`)
* **Landscape:** `worldedit.generation.path` (`//path`), `worldedit.generation.river` (`//river`),
  `worldedit.generation.scatter` (`//scatter`), `worldedit.region.strata` (`//strata`), `worldedit.region.erode`
  (`//erode`), `worldedit.region.texture` (`//texture`), `worldedit.heightmap.import` and
  `worldedit.heightmap.export` (`//heightmap`)
* **Region tools:** `worldedit.region.catenary` (`//catenary`), `worldedit.region.weather` (`//weather`),
  `worldedit.region.autostairs` (`//autostairs`), `worldedit.region.roof` (`//roof`), `worldedit.region.insetfaces`
  (`//insetfaces`), `worldedit.region.retheme` (`//retheme`), `worldedit.region.lightfill` (`//lightfill`),
  `worldedit.region.ruin` (`//ruin`), `worldedit.region.overgrow` (`//overgrow`), `worldedit.symmetry`
  (`//symmetry`), `worldedit.find` (`//find`)
* **Clipboard:** `worldedit.clipboard.list` (`//clipboards`), `worldedit.clipboard.scale` (`//scale`),
  `worldedit.clipboard.radial` (`//radial`), `worldedit.clipboard.stackpath` (`//stackpath`),
  `worldedit.clipboard.grid` (`//grid`); `//paste -c` uses the normal `worldedit.clipboard.paste`
* **Selection:** `worldedit.selection.shape` (`//selshape`), `worldedit.selection.connected` (`//selconnected`),
  `worldedit.selection.build` (`//selbuild`), `worldedit.selection.polywalk` (`//polywalk`); `//measure` uses the
  normal `worldedit.selection.size`
* **History, comparing and jobs:** `worldedit.history.list` (`//history`; undoing and redoing from it use the normal
  undo and redo permissions), `worldedit.history.tp` (`[tp]` in `//history`), `worldedit.diff` (`//diff`),
  `worldedit.diff.restore` (`//diff restore`), `worldedit.preview` (`//preview`), `worldedit.jobs` (`//jobs`),
  `worldedit.jobs.others` (cancelling or finishing other players' jobs)
* **Saved patterns and masks:** `worldedit.pattern.saved` (`//pattern list|info`), `worldedit.pattern.saved.edit`
  (`//pattern save|delete`), `worldedit.mask.saved` (`//mask list|info`), `worldedit.mask.saved.edit`
  (`//mask save|delete`)
* **No permission needed:** using a saved `#name` pattern or mask, and the layout patterns

In single-player, and for operators, everything is allowed.
