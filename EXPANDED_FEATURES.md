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
Conditions: `y>N`, `y<N`, `slope>D`, `slope<D` (degrees), `noise>F`, `noise<F` (0-1), and `>=`/`<=` also work.

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
| `//path <pattern> [width] [maxSlope] [-s stairs]` (alias `//road`) | Road that follows the terrain between your selection points (pos1 → pos2, or through the points of a polygon/convex selection). It finds a gentle route, grades it so each step changes by at most one block, tunnels through hills (3 blocks of headroom), and puts pillars under bridges. `-s` puts stairs on the steps. Width defaults to 3; use 2 or more. |
| `//river [width] [depth] [-l maxLength] [-b bed]` | Carves a river downhill from your position until it reaches water, `-l` blocks (default 256), or a dip, where it makes a pond. The water never flows uphill, and low banks get levees. The bed defaults to sand. |
| `//scatter <count> [minSpacing] [-k name] [-o yOffset] [-r seed] [-n] [-a]` | Places `count` randomly rotated copies of your clipboard (or named clipboard `-k`), spread out across the surface of the selection, e.g. rocks, ruins or bushes. `-n`: no rotation, `-a`: skip air, `-r`: repeatable result. The selection must reach down to the ground. |
| `//ptree <log> <leaves> [height] [branches] [leafRadius] [-l lean°] [-t 1\|2] [-r seed] [-d]` | A custom tree at your position with a leaning, curving trunk (`-t 2` for 2×2), branches ending in leaf clusters, and a crown. Leaves are made persistent unless you pass `-d`. |

## Building tools

| Command | What it does |
|---|---|
| `//retheme <from> <to> [-m mask]` | Swaps a whole material family in the selection and keeps stair facing, door hinges, sign text and so on. Examples: `//retheme oak spruce`, `//retheme oak crimson` (logs become stems, leaves become wart blocks), `//retheme cobblestone mossy_cobblestone`, `//retheme stone_brick deepslate_brick`. Blocks with no equivalent are left alone and listed. `oak` doesn't touch `dark_oak`/`pale_oak`. |
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

`//shape <name> <pattern> <size> [settings] [-h] [-b] [-f] [-r degrees] [-t degrees]` builds a shape around your
position by name. You never type a formula: every setting is a plain number with a sensible default.

* **size** is the radius in blocks (like `//sphere`), or `x,y,z` radii to stretch it, e.g. `10,25,10` for something tall.
* **settings** are numbers, either in order (`//shape star gold_block 10 6`) or by name (`//shape star gold_block 10 points=6`).
  Anything you leave out uses its default.
* `-h` hollow, `-b` puts the bottom of the shape at your feet instead of its middle, `-f` turns it to face you,
  `-r 45` turns it 45° clockwise, `-t 90` tilts it onto its side. Flags can go before or after the settings.
* `//shapes` lists every shape (click one), and `//shapes <name>` shows its settings, their ranges and an example.
* `/brush shape <name> <pattern> [radius] [settings]` stamps the shape wherever you click.
* `//undo` works as usual.

| Group | Shapes (aliases in brackets) |
|---|---|
| Solids | `heart`, `star`, `torus` (donut, ring), `egg`, `crescent` (moon), `superellipsoid` (roundcube), `tetrahedron`, `octahedron`, `dodecahedron`, `icosahedron` |
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

## New commands

| Command | What it does |
|---|---|
| `//helix <pattern> <radius>[,<radiusZ>] <height> [turns] [thickness] [-h]` | Spiral around your position. `//hhelix` is the hollow version. |
| `//torus <pattern> <major radius> <minor radius> [-h]` | Donut around your position. `//htorus` is the hollow version. |
| `//catenary <pattern> [sag] [thickness] [-h]` | Hanging rope/chain between pos1 and pos2 (or between convex-selection vertices). `sag 0` picks a sag automatically. |
| `//strata <p1,p2,...> [thickness] [dip°] [azimuth°] [waviness]` | Fills the selection with tilted rock layers. |
| `//erode [iterations]` | Thermal erosion: blocks on 2+ high drops slide down to the lowest neighbouring column. |
| `//weather [severity 0-1] [-m] [-c]` | Cracks stone/deepslate/blackstone bricks. `-m` adds moss, `-c` advances copper oxidation by one stage. |
| `//autostairs <stair pattern> [slab pattern] [-u]` | Turns step edges in the selection into correctly facing stairs (and slabs). `-u` handles ceilings. |
| `//roof [gable\|hip\|shed\|curved\|mansard] <pattern> [overhang] [pitch] [-h]` | Builds a roof over the selection footprint. |
| `//insetfaces [margin] <pattern> [depth]` | Recessed panels on the selection's six faces. |
| `//measure [x,y,z]` | Size, distances, angles, volume and surface area of the selection, or from pos1 to a point. |
| `//copy -k <name>`, `//paste -k <name>` | Copy into or paste from a named clipboard. |
| `//clipboards [list\|save\|load] [name]` | Manage named clipboards (they last for the current session only). |

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
`worldedit.generation.helix`, `worldedit.generation.torus`, `worldedit.region.catenary`, `worldedit.region.strata`,
`worldedit.region.erode`, `worldedit.region.weather`, `worldedit.region.autostairs`, `worldedit.region.roof`,
`worldedit.region.insetfaces`, `worldedit.clipboard.list`, `worldedit.preview`, `worldedit.jobs`
(plus `worldedit.jobs.others` to cancel/finish other players' jobs), `worldedit.region.texture`,
`worldedit.heightmap.import`, `worldedit.heightmap.export`, `worldedit.generation.path`, `worldedit.generation.river`,
`worldedit.generation.scatter`, `worldedit.generation.ptree`, `worldedit.region.retheme`, `worldedit.symmetry`,
`worldedit.region.lightfill`, `worldedit.find`, `worldedit.diff`, `worldedit.diff.restore`,
`worldedit.generation.shape.named` (`//shape`, `//shapes`), `worldedit.generation.shape.preset` (saving/deleting presets), `worldedit.brush.shape`,
`worldedit.region.warp` (`//warp`, `//warps`). The layout patterns need no extra permission. `//measure` uses `worldedit.selection.size`.
In single-player, and for operators, everything is allowed.
