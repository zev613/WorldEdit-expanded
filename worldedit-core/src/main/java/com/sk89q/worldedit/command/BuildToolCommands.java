/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Locatable;
import com.sk89q.worldedit.extent.symmetry.SymmetryManager;
import com.sk89q.worldedit.extent.symmetry.SymmetrySettings;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.buildtools.FindResults;
import com.sk89q.worldedit.util.buildtools.LightFillSolver;
import com.sk89q.worldedit.util.buildtools.RethemeMapper;
import com.sk89q.worldedit.util.formatting.Counts;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;
import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;

/**
 * Building helper commands: material re-theming, symmetry mode, mob-proof
 * lighting and block search.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class BuildToolCommands {

    /**
     * Block light margin read around the selection so existing emitters
     * outside it are taken into account.
     */
    private static final int LIGHT_MARGIN = 15;
    /**
     * Maximum number of cells in the //lightfill grid (selection plus margin).
     */
    private static final long MAX_LIGHT_GRID = 4_000_000L;
    private static final int MAX_LIGHTS_PLACED = 4096;
    private static final long MAX_FIND_VOLUME = 16_777_216L;
    private static final int MAX_SKIPPED_LISTED = 15;

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public BuildToolCommands(WorldEdit worldEdit) {
        this.worldEdit = worldEdit;
        SymmetryManager.getInstance().registerOnce(worldEdit.getEventBus());
    }

    // ------------------------------------------------------------------
    // //retheme
    // ------------------------------------------------------------------

    @Command(
        name = "/retheme",
        desc = "Swap a whole material family in the selection (e.g. oak -> spruce), keeping block states",
        descFooter = "Every block with the first material in its name becomes the same block in the second "
            + "material, keeping which way it faces, door hinges, sign text and so on; blocks with no "
            + "equivalent are left alone and listed. Example: //retheme oak spruce"
    )
    @CommandPermissions("worldedit.region.retheme")
    @Logging(REGION)
    public int retheme(Actor actor, EditSession editSession,
                       @Selection Region region,
                       @Arg(desc = "The material to replace, e.g. oak or stone_brick")
                           String from,
                       @Arg(desc = "The replacement material, e.g. spruce or deepslate_brick")
                           String to,
                       @ArgFlag(name = 'm', desc = "Only change blocks matching this mask")
                           Mask mask) throws WorldEditException {
        String fromToken = from.toLowerCase(Locale.ROOT);
        String toToken = to.toLowerCase(Locale.ROOT);
        checkCommandArgument(RethemeMapper.isValidToken(fromToken),
            "Invalid material '" + from + "': use a block id fragment like oak or stone_brick (no namespace)");
        checkCommandArgument(RethemeMapper.isValidToken(toToken),
            "Invalid material '" + to + "': use a block id fragment like spruce or deepslate_brick (no namespace)");
        checkCommandArgument(!fromToken.equals(toToken), "The two materials must differ");
        List<String> blockIds = new ArrayList<>();
        for (BlockType type : BlockType.REGISTRY) {
            blockIds.add(type.id());
        }
        for (String token : List.of(fromToken, toToken)) {
            checkCommandArgument(RethemeMapper.isKnownMaterial(blockIds, token), "'" + token
                + "' is not a material I know: no block has it in its name. Try e.g. oak, spruce, birch, "
                + "dark_oak, cobblestone, stone_brick, deepslate_brick, sandstone or copper.");
        }

        Map<BlockType, Optional<BlockType>> mapping = new HashMap<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        Map<String, Integer> skippedCounts = new HashMap<>();
        int changed = 0;

        for (BlockVector3 pt : region) {
            if (mask != null && !mask.test(pt)) {
                continue;
            }
            BaseBlock original = editSession.getFullBlock(pt);
            BlockType type = original.getBlockType();
            Optional<BlockType> target = mapping.computeIfAbsent(type, t -> {
                List<String> candidates = RethemeMapper.candidates(t.id(), fromToken, toToken);
                for (String candidate : candidates) {
                    BlockType candidateType = BlockTypes.get(candidate);
                    if (candidateType != null) {
                        return Optional.of(candidateType);
                    }
                }
                if (!candidates.isEmpty()) {
                    skipped.put(t.id(), candidates.getFirst());
                }
                return Optional.empty();
            });
            if (target.isEmpty()) {
                if (skipped.containsKey(type.id())) {
                    skippedCounts.merge(type.id(), 1, Integer::sum);
                }
                continue;
            }
            BlockState newState = transferBlockProperties(original.toImmutableState(), target.get());
            BaseBlock newBlock = original.getNbtReference() == null
                ? newState.toBaseBlock()
                : newState.toBaseBlock(original.getNbtReference());
            if (editSession.setBlock(pt, newBlock)) {
                changed++;
            }
        }

        actor.printInfo(TextComponent.of(changed == 0 && skipped.isEmpty()
            ? "There are no " + fromToken + " blocks in the selection, so nothing changed."
            : "Rethemed " + fromToken + " to " + toToken + ": " + changed + " blocks changed."));
        if (!skipped.isEmpty()) {
            actor.printInfo(TextComponent.of("No " + toToken + " equivalent for (left unchanged):", TextColor.GRAY));
            int listed = 0;
            for (Map.Entry<String, String> entry : skipped.entrySet()) {
                if (listed++ >= MAX_SKIPPED_LISTED) {
                    actor.printInfo(TextComponent.of("  ... and " + (skipped.size() - MAX_SKIPPED_LISTED) + " more types",
                        TextColor.GRAY));
                    break;
                }
                actor.printInfo(TextComponent.of("  " + entry.getKey() + " -> " + entry.getValue()
                    + " (" + skippedCounts.getOrDefault(entry.getKey(), 0) + " blocks)", TextColor.GRAY));
            }
        }
        return changed;
    }

    private static BlockState transferBlockProperties(BlockState original, BlockType newType) {
        BlockState result = newType.getDefaultState();
        for (Map.Entry<Property<?>, Object> entry : original.getStates().entrySet()) {
            Property<?> targetProp = newType.getPropertyMap().get(entry.getKey().name());
            if (targetProp != null) {
                result = applyPropertyUnchecked(result, targetProp, entry.getValue());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <V> BlockState applyPropertyUnchecked(BlockState state, Property<V> prop, Object value) {
        try {
            return state.with(prop, (V) value);
        } catch (RuntimeException _) {
            // Value not valid for the target property (e.g. different enum values)
            return state;
        }
    }

    // ------------------------------------------------------------------
    // //symmetry
    // ------------------------------------------------------------------

    @Command(
        name = "/symmetry",
        aliases = { "/sym" },
        desc = "Mirror all of your edits across a plane through your position (or the selection centre)",
        descFooter = "Modes: x (mirror across the plane perpendicular to X), z, xz (4-way), off. "
            + "Without a mode, shows the current setting."
    )
    @CommandPermissions("worldedit.symmetry")
    public void symmetry(Player player, LocalSession session,
                         @Arg(desc = "x, z, xz or off", def = "")
                             String mode,
                         @Switch(name = 'p', desc = "Put the plane(s) through the selection centre instead of your position")
                             boolean selectionCentre) throws WorldEditException {
        SymmetryManager manager = SymmetryManager.getInstance();
        if (mode == null || mode.isEmpty()) {
            SymmetrySettings current = manager.getSettings(player.getUniqueId());
            if (current == null) {
                player.printInfo(TextComponent.of("Symmetry is off."));
            } else {
                player.printInfo(TextComponent.of("Symmetry: " + current.describe() + "."));
            }
            return;
        }
        String normalized = mode.toLowerCase(Locale.ROOT);
        if (normalized.equals("off") || normalized.equals("none")) {
            manager.setSettings(player.getUniqueId(), null);
            player.printInfo(TextComponent.of("Symmetry turned off."));
            return;
        }
        boolean mirrorX = normalized.equals("x") || normalized.equals("xz") || normalized.equals("zx");
        boolean mirrorZ = normalized.equals("z") || normalized.equals("xz") || normalized.equals("zx");
        checkCommandArgument(mirrorX || mirrorZ, "Unknown symmetry mode '" + mode + "'; use x, z, xz or off");

        World world = player.getWorld();
        int doubledX;
        int doubledZ;
        if (selectionCentre) {
            Region region = session.getSelection(world);
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            doubledX = min.x() + max.x();
            doubledZ = min.z() + max.z();
        } else {
            BlockVector3 pos = player.getBlockLocation().toVector().toBlockPoint();
            doubledX = pos.x() * 2;
            doubledZ = pos.z() * 2;
        }
        SymmetrySettings settings = new SymmetrySettings(mirrorX, mirrorZ, doubledX, doubledZ, world.getName());
        manager.setSettings(player.getUniqueId(), settings);
        player.printInfo(TextComponent.of("Symmetry on: " + settings.describe()
            + ". All your edits are now mirrored; use //symmetry off to stop."));
    }

    // ------------------------------------------------------------------
    // //lightfill
    // ------------------------------------------------------------------

    @Command(
        name = "/lightfill",
        desc = "Place light sources so no spot in the selection is dark enough for hostile mobs to spawn",
        descFooter = "Uses an approximation of block light (sky light is ignored). "
            + "Defaults to invisible light blocks (light[level=15]); use -b torch etc. for visible lights."
    )
    @CommandPermissions("worldedit.region.lightfill")
    @Logging(REGION)
    public int lightfill(Actor actor, World world, EditSession editSession,
                         @Selection Region region,
                         @Arg(desc = "Minimum block light every spawnable spot must have", def = "1")
                             int minLight,
                         @ArgFlag(name = 'b', desc = "The light block pattern to place (default light[level=15])")
                             Pattern pattern,
                         @ArgFlag(name = 's', desc = "Max light-steps between an unlit spot and the light placed for it "
                             + "(default: as far as the light reaches; lower = denser)")
                             Integer spacing) throws WorldEditException {
        checkCommandArgument(minLight >= 1 && minLight <= 15, "Minimum light must be between 1 and 15");
        checkCommandArgument(spacing == null || spacing >= 0, "Spacing must be >= 0");

        BlockVector3 regionMin = region.getMinimumPoint();
        BlockVector3 regionMax = region.getMaximumPoint();
        BaseBlock lightBlock;
        if (pattern == null) {
            BlockType lightType = BlockTypes.LIGHT;
            checkCommandArgument(lightType != null, "This platform has no minecraft:light block; use -b <pattern>");
            lightBlock = withLevel(lightType.getDefaultState(), 15).toBaseBlock();
        } else {
            lightBlock = pattern.applyBlock(regionMin);
        }
        int level = lightValueOf(lightBlock.toImmutableState());
        checkCommandArgument(level > 0, lightBlock.getBlockType().id() + " does not emit light");
        checkCommandArgument(level >= minLight,
            lightBlock.getBlockType().id() + " only emits light " + level + ", below the requested minimum " + minLight);

        BlockVector3 gridMin = BlockVector3.at(regionMin.x() - LIGHT_MARGIN,
            Math.max(world.getMinY(), regionMin.y() - LIGHT_MARGIN), regionMin.z() - LIGHT_MARGIN);
        BlockVector3 gridMax = BlockVector3.at(regionMax.x() + LIGHT_MARGIN,
            Math.min(world.getMaxY(), regionMax.y() + LIGHT_MARGIN), regionMax.z() + LIGHT_MARGIN);
        int sizeX = gridMax.x() - gridMin.x() + 1;
        int sizeY = gridMax.y() - gridMin.y() + 1;
        int sizeZ = gridMax.z() - gridMin.z() + 1;
        long cells = (long) sizeX * sizeY * sizeZ;
        checkCommandArgument(cells <= MAX_LIGHT_GRID, "Selection too large for //lightfill (" + cells
            + " blocks including the " + LIGHT_MARGIN + "-block margin; maximum " + MAX_LIGHT_GRID + ")");

        LightFillSolver solver = new LightFillSolver(sizeX, sizeY, sizeZ);
        // Pass 1: light attenuation and emission
        boolean[] solidFloor = new boolean[(int) cells];
        boolean[] openSpace = new boolean[(int) cells];
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockState state = editSession.getBlock(gridMin.add(x, y, z));
                    BlockMaterial material = state.getBlockType().getMaterial();
                    solver.setCell(x, y, z, attenuationOf(material), lightValueOf(state));
                    int i = solver.index(x, y, z);
                    solidFloor[i] = material.isSolid() && material.isFullCube() && !material.isLiquid();
                    openSpace[i] = !material.isSolid() && !material.isLiquid();
                }
            }
        }
        // Pass 2: spawnable spots inside the selection
        int spots = 0;
        for (int y = 1; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    int i = solver.index(x, y, z);
                    if (!openSpace[i] || !solidFloor[solver.index(x, y - 1, z)]) {
                        continue;
                    }
                    if (region.contains(gridMin.add(x, y, z))) {
                        solver.setSpawnable(x, y, z, true);
                        spots++;
                    }
                }
            }
        }
        solver.computeLight();
        int darkBefore = solver.countDark(minLight);
        int maxDistance = spacing == null ? level - minLight : spacing;
        List<int[]> placed = solver.placeLights(minLight, level, maxDistance, MAX_LIGHTS_PLACED);

        int set = 0;
        for (int[] cell : placed) {
            BlockVector3 pos = gridMin.add(cell[0], cell[1], cell[2]);
            BaseBlock block = pattern == null ? lightBlock : pattern.applyBlock(pos);
            if (editSession.setBlock(pos, block)) {
                set++;
            }
        }

        actor.printInfo(TextComponent.of("Placed " + Counts.of(set, "light source") + " (light " + level + "). "
            + darkBefore + " of " + spots + " spawnable spots were below light " + minLight + "."));
        int darkAfter = solver.countDark(minLight);
        if (darkAfter > 0) {
            actor.printError(TextComponent.of("Stopped after " + MAX_LIGHTS_PLACED + " lights; "
                + darkAfter + " spots are still dark. Run //lightfill again or use a smaller selection."));
        }
        return set;
    }

    /**
     * Approximate light attenuation of a block: full opaque cubes block
     * light; liquids and solid non-opaque blocks (glass, leaves, slabs,
     * stairs...) are treated as costing an extra level (conservative, since
     * vanilla lets light through most of them at cost 1); everything else
     * costs 1.
     */
    private static int attenuationOf(BlockMaterial material) {
        if (material.isOpaque() && material.isFullCube()) {
            return LightFillSolver.OPAQUE;
        }
        if (material.isLiquid() || (material.isSolid() && !material.isAir())) {
            return 2;
        }
        return 1;
    }

    private static int lightValueOf(BlockState state) {
        BlockType type = state.getBlockType();
        if (type == BlockTypes.LIGHT) {
            Property<?> levelProp = type.getPropertyMap().get("level");
            if (levelProp != null && state.getState(levelProp) instanceof Integer value) {
                return value;
            }
        }
        return Math.max(0, Math.min(15, type.getMaterial().getLightValue()));
    }

    @SuppressWarnings("unchecked")
    private static BlockState withLevel(BlockState state, int level) {
        Property<?> levelProp = state.getBlockType().getPropertyMap().get("level");
        if (levelProp == null) {
            return state;
        }
        try {
            return state.with((Property<Integer>) levelProp, level);
        } catch (RuntimeException _) {
            return state;
        }
    }

    // ------------------------------------------------------------------
    // //find
    // ------------------------------------------------------------------

    @Command(
        name = "/find",
        desc = "Find the nearest blocks matching a mask in the selection (or around you with -r)"
    )
    @CommandPermissions("worldedit.find")
    public int find(Actor actor, World world, LocalSession session, EditSession editSession,
                    @Arg(desc = "The mask of blocks to find")
                        Mask mask,
                    @Arg(desc = "How many of the nearest matches to list", def = "10")
                        int limit,
                    @ArgFlag(name = 'r', desc = "Search a cube of this radius around you instead of the selection")
                        Integer radius,
                    @Switch(name = 'c', desc = "Show match counts for the top 10 chunks instead")
                        boolean perChunk) throws WorldEditException {
        checkCommandArgument(limit >= 1 && limit <= 100, "Limit must be between 1 and 100");

        BlockVector3 playerPos = actor instanceof Locatable locatable
            ? locatable.getBlockLocation().toVector().toBlockPoint()
            : null;
        Region region;
        if (radius != null) {
            checkCommandArgument(playerPos != null, "-r can only be used by a player");
            checkCommandArgument(radius >= 0, "Radius must be >= 0");
            worldEdit.checkMaxRadius(radius);
            BlockVector3 min = BlockVector3.at(playerPos.x() - radius,
                Math.max(world.getMinY(), playerPos.y() - radius), playerPos.z() - radius);
            BlockVector3 max = BlockVector3.at(playerPos.x() + radius,
                Math.min(world.getMaxY(), playerPos.y() + radius), playerPos.z() + radius);
            region = new CuboidRegion(world, min, max);
        } else {
            region = session.getSelection(world);
        }
        long volume = region.getVolume();
        checkCommandArgument(volume <= MAX_FIND_VOLUME, "Search area too large (" + volume
            + " blocks; maximum " + MAX_FIND_VOLUME + "). Use a smaller selection or radius.");

        BlockVector3 origin = playerPos != null ? playerPos : region.getCenter().toBlockPoint();
        FindResults results = new FindResults(origin, perChunk ? 0 : limit, perChunk);
        for (BlockVector3 pt : region) {
            if (mask.test(pt)) {
                results.add(pt);
            }
        }

        if (results.getTotal() == 0) {
            actor.printInfo(TextComponent.of("No matching blocks found in " + volume + " searched blocks."));
            return 0;
        }

        if (perChunk) {
            actor.printInfo(TextComponent.of(results.getTotal() + " matching blocks in "
                + results.getChunkCount() + " chunks. Top chunks:"));
            for (Map.Entry<BlockVector2, Integer> entry : results.getTopChunks(10)) {
                BlockVector2 chunk = entry.getKey();
                int centerX = (chunk.x() << 4) + 8;
                int centerZ = (chunk.z() << 4) + 8;
                actor.printInfo(TextComponent.builder().content("")
                    .append(TextComponent.of("  chunk " + chunk.x() + ", " + chunk.z()
                        + " (blocks " + (chunk.x() << 4) + ", " + (chunk.z() << 4) + "): "))
                    .append(TextComponent.of(entry.getValue() + " blocks", TextColor.YELLOW))
                    .append(TextComponent.space())
                    .append(TextComponent.of("[tp]", TextColor.AQUA)
                        .clickEvent(ClickEvent.suggestCommand("/tp " + centerX + " ~ " + centerZ))
                        .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT,
                            TextComponent.of("Suggest a teleport to the chunk centre"))))
                    .build());
            }
            return (int) Math.min(Integer.MAX_VALUE, results.getTotal());
        }

        List<BlockVector3> nearest = results.getNearest();
        actor.printInfo(TextComponent.of(results.getTotal() + " matching blocks found; nearest "
            + nearest.size() + (playerPos != null ? " to you:" : " to the selection centre:")));
        int rank = 1;
        for (BlockVector3 pt : nearest) {
            String coords = pt.x() + ", " + pt.y() + ", " + pt.z();
            double distance = Math.sqrt((double) results.distanceSq(pt));
            String tpCommand = String.format(Locale.ROOT, "/tp %.1f %d %.1f", pt.x() + 0.5, pt.y() + 1, pt.z() + 0.5);
            actor.printInfo(TextComponent.builder().content("")
                .append(TextComponent.of(String.format(Locale.ROOT, "  %d. ", rank++)))
                .append(TextComponent.of("(" + coords + ")", TextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("//pos1 " + pt.x() + "," + pt.y() + "," + pt.z()))
                    .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Click to set pos1 here"))))
                .append(TextComponent.of(String.format(Locale.ROOT, " %.1fm ", distance), TextColor.GRAY))
                .append(TextComponent.of(editSession.getBlock(pt).getBlockType().id(), TextColor.GRAY))
                .append(TextComponent.space())
                .append(TextComponent.of("[tp]", TextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand(tpCommand))
                    .hoverEvent(HoverEvent.of(HoverEvent.Action.SHOW_TEXT,
                        TextComponent.of("Click to teleport on top of this block"))))
                .build());
        }
        return (int) Math.min(Integer.MAX_VALUE, results.getTotal());
    }
}
