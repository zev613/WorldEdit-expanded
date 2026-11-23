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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.sk89q.util.StringUtil;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.InputExtent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.GroundFunction;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.function.RegionMaskingFilter;
import com.sk89q.worldedit.function.block.ApplySideEffect;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.generator.FloraGenerator;
import com.sk89q.worldedit.function.generator.architecture.InsetFaces;
import com.sk89q.worldedit.function.generator.architecture.Roof;
import com.sk89q.worldedit.function.generator.shape.ShapeFlags;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.NoiseFilter2D;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.util.ChangeLimitingFilter;
import com.sk89q.worldedit.function.visitor.LayerVisitor;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.internal.annotation.Offset;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.internal.util.TransformUtil;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.convolution.GaussianKernel;
import com.sk89q.worldedit.math.convolution.HeightMap;
import com.sk89q.worldedit.math.convolution.HeightMapFilter;
import com.sk89q.worldedit.math.convolution.SnowHeightMap;
import com.sk89q.worldedit.math.noise.RandomNoise;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.formatting.component.TextUtils;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.generation.TreeType;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.ArrayList;
import java.util.List;

import static com.sk89q.worldedit.command.util.Logging.LogMode.ALL;
import static com.sk89q.worldedit.command.util.Logging.LogMode.ORIENTATION_REGION;
import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;
import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;
import static com.sk89q.worldedit.regions.Regions.asFlatRegion;
import static com.sk89q.worldedit.regions.Regions.maximumBlockY;
import static com.sk89q.worldedit.regions.Regions.minimumBlockY;

/**
 * Commands that operate on regions.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class RegionCommands {

    /**
     * Create a new instance.
     */
    public RegionCommands() {
    }

    @Command(
        name = "/set",
        desc = "Sets all the blocks in the region"
    )
    @CommandPermissions("worldedit.region.set")
    @Logging(REGION)
    public int set(Actor actor, EditSession editSession,
                   @Selection Region region,
                   @Arg(desc = "The pattern of blocks to set")
                       Pattern pattern) {
        RegionFunction set = new BlockReplace(editSession, pattern);
        RegionVisitor visitor = new RegionVisitor(region, set);

        Operations.completeBlindly(visitor);
        List<Component> messages = Lists.newArrayList(visitor.getStatusMessages());
        if (messages.isEmpty()) {
            actor.printInfo(TranslatableComponent.of("worldedit.set.done"));
        } else {
            actor.printInfo(TranslatableComponent.of("worldedit.set.done.verbose", TextUtils.join(messages, TextComponent.of(", "))));
        }

        return visitor.getAffected();
    }

    @Command(
        name = "/line",
        desc = "Draws line segments between cuboid selection corners or convex polyhedral selection vertices",
        descFooter = "Can only be used with a cuboid selection or a convex polyhedral selection"
    )
    @CommandPermissions("worldedit.region.line")
    @Logging(REGION)
    public int line(Actor actor, EditSession editSession,
                    @Selection Region region,
                    @Arg(desc = "The pattern of blocks to place")
                        Pattern pattern,
                    @Arg(desc = "The thickness of the line", def = "0")
                        double thickness,
                    @Switch(name = 'h', desc = "Generate only a shell")
                        boolean shell) throws WorldEditException {
        if (!((region instanceof CuboidRegion) || (region instanceof ConvexPolyhedralRegion))) {
            actor.printError(TranslatableComponent.of("worldedit.line.invalid-type"));
            return 0;
        }
        checkCommandArgument(thickness >= 0, "Thickness must be >= 0");
        WorldEdit.getInstance().checkMaxRadius(thickness);

        List<BlockVector3> vectors;

        if (region instanceof CuboidRegion cuboidRegion) {
            vectors = ImmutableList.of(cuboidRegion.getPos1(), cuboidRegion.getPos2());
        } else {
            ConvexPolyhedralRegion convexRegion = (ConvexPolyhedralRegion) region;
            vectors = ImmutableList.copyOf(convexRegion.getVertices());
        }

        int blocksChanged = editSession.drawLine(pattern, vectors, thickness, !shell);

        actor.printInfo(TranslatableComponent.of("worldedit.line.changed", TextComponent.of(blocksChanged)));
        return blocksChanged;
    }

    @Command(
        name = "/catenary",
        aliases = { "/cat" },
        desc = "Hangs a rope or chain between pos1 and pos2 that sags in the middle",
        descFooter = "Works with a cuboid selection (pos1 to pos2) or a convex selection (through its points "
            + "in order). Sag is how many blocks the middle droops (0 = pick a natural sag); thickness makes "
            + "the rope fatter (0 = one block); -h makes a hollow tube. Example: //catenary chain 4"
    )
    @CommandPermissions("worldedit.region.catenary")
    @Logging(REGION)
    public int catenary(Actor actor, EditSession editSession,
                        @Selection Region region,
                        @Arg(desc = "The pattern of blocks to place")
                            Pattern pattern,
                        @Arg(desc = "How many blocks the middle droops (0 = pick a natural sag)", def = "0")
                            double sag,
                        @Arg(desc = "How fat the rope is (0 = one block)", def = "0")
                            double thickness,
                        @Switch(name = 'h', desc = "Make a hollow tube")
                            boolean shell) throws WorldEditException {
        if (!((region instanceof CuboidRegion) || (region instanceof ConvexPolyhedralRegion))) {
            actor.printError(TranslatableComponent.of("worldedit.line.invalid-type"));
            return 0;
        }
        checkCommandArgument(thickness >= 0 && thickness <= 32, "Thickness must be between 0 and 32");
        checkCommandArgument(sag >= 0 && sag <= 256, "Sag must be between 0 and 256 blocks");
        WorldEdit.getInstance().checkMaxRadius(thickness);

        List<BlockVector3> vectors;

        if (region instanceof CuboidRegion cuboidRegion) {
            vectors = ImmutableList.of(cuboidRegion.getPos1(), cuboidRegion.getPos2());
        } else {
            ConvexPolyhedralRegion convexRegion = (ConvexPolyhedralRegion) region;
            vectors = ImmutableList.copyOf(convexRegion.getVertices());
        }

        int blocksChanged = editSession.drawCatenary(pattern, vectors, sag, thickness, !shell);

        actor.printInfo(TextComponent.of("Built a catenary: " + blocksChanged + " blocks changed."));
        return blocksChanged;
    }

    @Command(
        name = "/curve",
        desc = "Draws a spline through selected points",
        descFooter = "Can only be used with a convex polyhedral selection"
    )
    @CommandPermissions("worldedit.region.curve")
    @Logging(REGION)
    public int curve(Actor actor, EditSession editSession,
                     @Selection Region region,
                     @Arg(desc = "The pattern of blocks to place")
                         Pattern pattern,
                     @Arg(desc = "The thickness of the curve", def = "0")
                         double thickness,
                     @Switch(name = 'h', desc = "Generate only a shell")
                         boolean shell) throws WorldEditException {
        if (!(region instanceof ConvexPolyhedralRegion cpregion)) {
            actor.printError(TranslatableComponent.of("worldedit.curve.invalid-type"));
            return 0;
        }
        checkCommandArgument(thickness >= 0, "Thickness must be >= 0");
        WorldEdit.getInstance().checkMaxRadius(thickness);

        List<BlockVector3> vectors = new ArrayList<>(cpregion.getVertices());

        int blocksChanged = editSession.drawSpline(pattern, vectors, 0, 0, 0, 10, thickness, !shell);

        actor.printInfo(TranslatableComponent.of("worldedit.curve.changed", TextComponent.of(blocksChanged)));
        return blocksChanged;
    }

    @Command(
        name = "/replace",
        aliases = { "/re", "/rep" },
        desc = "Replace all blocks in the selection with another"
    )
    @CommandPermissions("worldedit.region.replace")
    @Logging(REGION)
    public int replace(Actor actor, EditSession editSession, @Selection Region region,
                       @Arg(desc = "The mask representing blocks to replace", def = "")
                           Mask from,
                       @Arg(desc = "The pattern of blocks to replace with")
                           Pattern to) throws WorldEditException {
        if (from == null) {
            from = new ExistingBlockMask(editSession);
        }
        int affected = editSession.replaceBlocks(region, from, to);
        actor.printInfo(TranslatableComponent.of("worldedit.replace.replaced", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/overlay",
        desc = "Set a block on top of blocks in the region"
    )
    @CommandPermissions("worldedit.region.overlay")
    @Logging(REGION)
    public int overlay(Actor actor, EditSession editSession, @Selection Region region,
                       @Arg(desc = "The pattern of blocks to overlay")
                           Pattern pattern) throws WorldEditException {
        int affected = editSession.overlayCuboidBlocks(region, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.overlay.overlaid", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/center",
        aliases = { "/middle" },
        desc = "Set the center block(s)"
    )
    @Logging(REGION)
    @CommandPermissions("worldedit.region.center")
    public int center(Actor actor, EditSession editSession, @Selection Region region,
                      @Arg(desc = "The pattern of blocks to set")
                          Pattern pattern) throws WorldEditException {
        int affected = editSession.center(region, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.center.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/naturalize",
        desc = "3 layers of dirt on top then rock below"
    )
    @CommandPermissions("worldedit.region.naturalize")
    @Logging(REGION)
    public int naturalize(Actor actor, EditSession editSession, @Selection Region region) throws WorldEditException {
        int affected = editSession.naturalizeCuboidBlocks(region);
        actor.printInfo(TranslatableComponent.of("worldedit.naturalize.naturalized", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/walls",
        desc = "Build the four sides of the selection"
    )
    @CommandPermissions("worldedit.region.walls")
    @Logging(REGION)
    public int walls(Actor actor, EditSession editSession, @Selection Region region,
                     @Arg(desc = "The pattern of blocks to set")
                         Pattern pattern) throws WorldEditException {
        int affected = editSession.makeWalls(region, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.walls.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/faces",
        aliases = { "/outline" },
        desc = "Build the walls, ceiling, and floor of a selection"
    )
    @CommandPermissions("worldedit.region.faces")
    @Logging(REGION)
    public int faces(Actor actor, EditSession editSession, @Selection Region region,
                     @Arg(desc = "The pattern of blocks to set")
                         Pattern pattern) throws WorldEditException {
        int affected = editSession.makeFaces(region, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.faces.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/smooth",
        desc = "Smooth the elevation in the selection",
        descFooter = "Example: '//smooth 1 grass_block,dirt,stone' would only smooth natural surface terrain."
    )
    @CommandPermissions("worldedit.region.smooth")
    @Logging(REGION)
    public int smooth(Actor actor, EditSession editSession, @Selection Region region,
                      @Arg(desc = "# of iterations to perform", def = "1")
                          int iterations,
                      @Arg(desc = "The mask of blocks to use as the height map", def = "")
                          Mask mask) throws WorldEditException {
        HeightMap heightMap = new HeightMap(editSession, region, mask);
        HeightMapFilter filter = new HeightMapFilter(new GaussianKernel(5, 1.0));
        int affected = heightMap.applyFilter(filter, iterations);
        actor.printInfo(TranslatableComponent.of("worldedit.smooth.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/snowsmooth",
        desc = "Smooth the elevation in the selection with snow layers",
        descFooter = "Example: '//snowsmooth 1 -m snow_block,snow' would only smooth snow terrain."
    )
    @CommandPermissions("worldedit.region.snowsmooth")
    @Logging(REGION)
    public int snowSmooth(Actor actor, EditSession editSession, @Selection Region region,
                          @Arg(desc = "# of iterations to perform", def = "1")
                              int iterations,
                          @ArgFlag(name = 'l', desc = "Set the amount of snow blocks under the snow", def = "1")
                              int snowBlockCount,
                          @ArgFlag(name = 'm', desc = "The mask of blocks to use as the height map")
                              Mask mask) throws WorldEditException {
        SnowHeightMap heightMap = new SnowHeightMap(editSession, region, mask);
        HeightMapFilter filter = new HeightMapFilter(new GaussianKernel(5, 1.0));
        float[] changed = heightMap.applyFilter(filter, iterations);
        int affected = heightMap.applyChanges(changed, snowBlockCount);
        actor.printInfo(TranslatableComponent.of("worldedit.snowsmooth.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/strata",
        aliases = { "/layers" },
        desc = "Fills the selection with tilted rock layers, like a cliff face",
        descFooter = "List the layer blocks separated by commas; they repeat in that order. Then how thick each "
            + "layer is, how steeply the layers tilt (degrees: 0 = flat, 90 = standing upright), which way they "
            + "tilt (degrees clockwise from north: 0 = north, 90 = east) and how wavy the layers are (0 = "
            + "straight, up to 5). Every block in the selection is replaced, air included, so select solid rock. "
            + "Example: //strata stone,andesite,granite,tuff 3 20 90 1"
    )
    @CommandPermissions("worldedit.region.strata")
    @Logging(REGION)
    public int strata(Actor actor, World world, EditSession editSession, LocalSession session,
                      @Selection Region region,
                      @Arg(desc = "The layer blocks, separated by commas, e.g. stone,andesite,granite")
                          String patternListStr,
                      @Arg(desc = "How thick each layer is, in blocks", def = "4")
                          double layerThickness,
                      @Arg(desc = "How steeply the layers tilt, in degrees (0 = flat, 90 = upright)", def = "15")
                          double dipDegrees,
                      @Arg(desc = "Which way they tilt, in degrees clockwise from north (90 = east)", def = "45")
                          double azimuthDegrees,
                      @Arg(desc = "How wavy the layers are (0 = straight, up to 5)", def = "0")
                          double noiseFactor) throws WorldEditException {
        checkCommandArgument(layerThickness > 0 && layerThickness <= 256,
            "Layer thickness must be more than 0 and at most 256 blocks");
        checkCommandArgument(dipDegrees >= 0 && dipDegrees <= 90,
            "The tilt must be between 0 (flat) and 90 (upright) degrees");
        checkCommandArgument(Double.isFinite(azimuthDegrees),
            "The tilt direction must be a number of degrees clockwise from north, e.g. 90 for east");
        checkCommandArgument(noiseFactor >= 0 && noiseFactor <= 5, "Waviness must be between 0 (straight) and 5");

        Iterable<String> rawPatterns = StringUtil.splitOutsideBrackets(patternListStr, CharMatcher.is(','));
        List<Pattern> patterns = new ArrayList<>();
        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(world);
        context.setExtent(editSession);
        for (String raw : rawPatterns) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(WorldEdit.getInstance().getPatternFactory().parseFromInput(trimmed, context));
            }
        }
        checkCommandArgument(!patterns.isEmpty(), "At least one pattern must be specified");

        double dipRad = Math.toRadians(dipDegrees);
        double azRad = Math.toRadians(azimuthDegrees);
        double ux = Math.sin(dipRad) * Math.sin(azRad);
        double uy = Math.cos(dipRad);
        double uz = Math.sin(dipRad) * Math.cos(azRad);

        int affected = 0;
        for (BlockVector3 pt : region) {
            double d = pt.x() * ux + pt.y() * uy + pt.z() * uz;
            if (noiseFactor != 0.0) {
                d += noiseFactor * 3.0 * Math.sin(pt.x() * 0.15) * Math.cos(pt.z() * 0.15);
            }
            int layerIdx = (int) Math.floor(d / layerThickness);
            int patIdx = Math.floorMod(layerIdx, patterns.size());
            if (editSession.setBlock(pt, patterns.get(patIdx))) {
                affected++;
            }
        }

        actor.printInfo(TextComponent.of("Filled the selection with " + patterns.size() + " repeating rock layers: "
            + affected + " blocks changed."));
        return affected;
    }

    @Command(
        name = "/erode",
        desc = "Wears down steep slopes in the selection, as if loose rock had tumbled down",
        descFooter = "In each pass, the top block of every column that stands 2 or more blocks above a "
            + "neighbour slides down onto the lowest neighbouring column, so cliffs turn into rough slopes. "
            + "More passes give gentler slopes; it stops early when nothing more can slide. Example: //erode 5"
    )
    @CommandPermissions("worldedit.region.erode")
    @Logging(REGION)
    public int erode(Actor actor, EditSession editSession,
                     @Selection Region region,
                     @Arg(desc = "How many passes to make (1-50); more passes give gentler slopes", def = "1")
                         int iterations) throws WorldEditException {
        checkCommandArgument(iterations > 0 && iterations <= 50, "Passes must be between 1 and 50");
        int totalChanges = 0;
        int passes = 0;

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int minX = min.x();
        int minY = min.y();
        int minZ = min.z();
        int sizeX = max.x() - minX + 1;
        int sizeZ = max.z() - minZ + 1;
        int[][] offsets = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };

        for (int iter = 0; iter < iterations; iter++) {
            // Height map of the topmost solid block per column (only blocks inside the region count).
            // NO_COLUMN = column not part of the region at all, minY - 1 = column in region but empty.
            final int noColumn = Integer.MIN_VALUE;
            int[][] heights = new int[sizeX][sizeZ];
            BlockState[][] tops = new BlockState[sizeX][sizeZ];
            for (int dx = 0; dx < sizeX; dx++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    heights[dx][dz] = noColumn;
                    for (int y = max.y(); y >= minY; y--) {
                        BlockVector3 pt = BlockVector3.at(minX + dx, y, minZ + dz);
                        if (!region.contains(pt)) {
                            continue;
                        }
                        if (heights[dx][dz] == noColumn) {
                            heights[dx][dz] = minY - 1;
                        }
                        BlockState state = editSession.getBlock(pt);
                        if (state.getBlockType().getMaterial().isSolid()) {
                            heights[dx][dz] = y;
                            tops[dx][dz] = state;
                            break;
                        }
                    }
                }
            }

            // A column may only give or receive one block per iteration, otherwise a column that
            // both loses its top and receives a block would end up with a floating block.
            boolean[][] touched = new boolean[sizeX][sizeZ];
            List<BlockVector3> toRemove = new ArrayList<>();
            List<BlockVector3> toAdd = new ArrayList<>();
            List<BlockState> addedStates = new ArrayList<>();

            for (int dx = 0; dx < sizeX; dx++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    int topY = heights[dx][dz];
                    if (topY == noColumn || tops[dx][dz] == null || touched[dx][dz]) {
                        continue;
                    }
                    int lowestY = topY;
                    int lowestDx = -1;
                    int lowestDz = -1;
                    for (int[] off : offsets) {
                        int ndx = dx + off[0];
                        int ndz = dz + off[1];
                        if (ndx < 0 || ndz < 0 || ndx >= sizeX || ndz >= sizeZ
                            || heights[ndx][ndz] == noColumn || touched[ndx][ndz]) {
                            continue;
                        }
                        if (heights[ndx][ndz] < lowestY) {
                            lowestY = heights[ndx][ndz];
                            lowestDx = ndx;
                            lowestDz = ndz;
                        }
                    }
                    if (lowestDx < 0 || topY - lowestY < 2) {
                        continue;
                    }
                    BlockVector3 target = BlockVector3.at(minX + lowestDx, lowestY + 1, minZ + lowestDz);
                    if (!region.contains(target)) {
                        continue;
                    }
                    touched[dx][dz] = true;
                    touched[lowestDx][lowestDz] = true;
                    toRemove.add(BlockVector3.at(minX + dx, topY, minZ + dz));
                    toAdd.add(target);
                    addedStates.add(tops[dx][dz]);
                }
            }

            if (toRemove.isEmpty()) {
                break;
            }
            passes++;
            for (BlockVector3 rem : toRemove) {
                if (editSession.setBlock(rem, BlockTypes.AIR.getDefaultState())) {
                    totalChanges++;
                }
            }
            for (int i = 0; i < toAdd.size(); i++) {
                if (editSession.setBlock(toAdd.get(i), addedStates.get(i))) {
                    totalChanges++;
                }
            }
        }

        if (passes == 0) {
            actor.printInfo(TextComponent.of("Nothing to erode: no column in the selection stands 2 or more blocks "
                + "above a neighbour."));
        } else {
            actor.printInfo(TextComponent.of("Eroded the selection in " + passes + (passes == 1 ? " pass" : " passes")
                + (passes < iterations ? " (then nothing more could slide)" : "") + ": " + totalChanges
                + " blocks changed."));
        }
        return totalChanges;
    }

    @Command(
        name = "/weather",
        desc = "Ages the blocks in the selection: cracks bricks, and with -m adds moss, with -c ages copper",
        descFooter = "Cracks stone bricks, deepslate bricks and tiles, and polished blackstone bricks. -m also "
            + "makes cobblestone and stone bricks (and their stairs, slabs and walls) mossy. -c moves copper "
            + "blocks one step along exposed, weathered and oxidized (waxed copper is left alone). Other blocks "
            + "are not changed. Severity is how many of the blocks change, from 0 to 1. "
            + "Example: //weather 0.6 -m"
    )
    @CommandPermissions("worldedit.region.weather")
    @Logging(REGION)
    public int weather(Actor actor, EditSession editSession,
                       @Selection Region region,
                       @Arg(desc = "How many of the blocks change, from 0 (none) to 1 (as many as possible)",
                           def = "0.5")
                           double severity,
                       @Switch(name = 'm', desc = "Also make cobblestone and stone bricks mossy")
                           boolean moss,
                       @Switch(name = 'c', desc = "Also age copper by one step")
                           boolean copper) throws WorldEditException {
        checkCommandArgument(severity >= 0.0 && severity <= 1.0, "Severity must be between 0 and 1");
        int changed = 0;

        for (BlockVector3 pt : region) {
            BlockState state = editSession.getBlock(pt);
            BlockType type = state.getBlockType();
            String id = type.id();
            String targetId = null;

            double roll = pseudoRandom(pt.x(), pt.y(), pt.z());

            if (id.equals("minecraft:stone_bricks")) {
                if (roll < severity * 0.35) {
                    targetId = "minecraft:cracked_stone_bricks";
                } else if (moss && roll < severity * 0.7) {
                    targetId = "minecraft:mossy_stone_bricks";
                }
            } else if (id.equals("minecraft:deepslate_bricks") && roll < severity * 0.4) {
                targetId = "minecraft:cracked_deepslate_bricks";
            } else if (id.equals("minecraft:deepslate_tiles") && roll < severity * 0.4) {
                targetId = "minecraft:cracked_deepslate_tiles";
            } else if (id.equals("minecraft:polished_blackstone_bricks") && roll < severity * 0.4) {
                targetId = "minecraft:cracked_polished_blackstone_bricks";
            } else if (moss) {
                if (id.equals("minecraft:cobblestone") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_cobblestone";
                } else if (id.equals("minecraft:cobblestone_stairs") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_cobblestone_stairs";
                } else if (id.equals("minecraft:cobblestone_slab") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_cobblestone_slab";
                } else if (id.equals("minecraft:cobblestone_wall") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_cobblestone_wall";
                } else if (id.equals("minecraft:stone_brick_stairs") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_stone_brick_stairs";
                } else if (id.equals("minecraft:stone_brick_slab") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_stone_brick_slab";
                } else if (id.equals("minecraft:stone_brick_wall") && roll < severity * 0.5) {
                    targetId = "minecraft:mossy_stone_brick_wall";
                }
            }

            if (copper && targetId == null && roll < severity
                && id.startsWith("minecraft:") && id.contains("copper") && !id.contains("waxed")) {
                // Advance one oxidation stage. Targets that do not exist (e.g. copper_ore) are skipped below.
                String path = id.substring("minecraft:".length());
                if (path.startsWith("exposed_")) {
                    targetId = "minecraft:weathered_" + path.substring("exposed_".length());
                } else if (path.startsWith("weathered_")) {
                    targetId = "minecraft:oxidized_" + path.substring("weathered_".length());
                } else if (path.equals("copper_block")) {
                    targetId = "minecraft:exposed_copper";
                } else if (!path.startsWith("oxidized_")) {
                    targetId = "minecraft:exposed_" + path;
                }
            }

            if (targetId != null) {
                BlockType targetType = BlockTypes.get(targetId);
                if (targetType != null) {
                    BlockState newState = transferBlockProperties(state, targetType);
                    if (editSession.setBlock(pt, newState)) {
                        changed++;
                    }
                }
            }
        }

        if (changed == 0) {
            actor.printInfo(TextComponent.of("Nothing was weathered. //weather cracks stone, deepslate and blackstone "
                + "bricks and deepslate tiles; add -m for mossy cobblestone and stone bricks, -c to age copper"
                + (severity == 0 ? ", and use a severity above 0." : ".")));
        } else {
            actor.printInfo(TextComponent.of("Weathered the selection: " + changed + " blocks changed."));
        }
        return changed;
    }

    private static BlockState transferBlockProperties(BlockState original, BlockType newType) {
        BlockState result = newType.getDefaultState();
        for (com.sk89q.worldedit.registry.state.Property<?> prop : original.getStates().keySet()) {
            com.sk89q.worldedit.registry.state.Property<?> targetProp = newType.getPropertyMap().get(prop.name());
            if (targetProp != null) {
                result = applyPropertyUnchecked(result, targetProp, original.getState(prop));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <V> BlockState applyPropertyUnchecked(BlockState state, com.sk89q.worldedit.registry.state.Property<V> prop, Object value) {
        try {
            return state.with(prop, (V) value);
        } catch (Exception _) {
            // Property not compatible with target block state, return original
            return state;
        }
    }

    private static double pseudoRandom(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 326648991;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    @Command(
        name = "/autostairs",
        aliases = { "/autostair" },
        desc = "Turns the step edges in the selection into stairs that face the right way",
        descFooter = "Every block with air above it and a higher neighbour (or a drop next to it) becomes a stair "
            + "facing up the slope, with corners where two slopes meet. Give a slab block too to put slabs on "
            + "single gentle steps. -u does the same for ceilings (upside-down stairs). "
            + "Example: //autostairs stone_brick_stairs stone_brick_slab"
    )
    @CommandPermissions("worldedit.region.autostairs")
    @Logging(REGION)
    public int autoStairs(Actor actor, EditSession editSession,
                          @Selection Region region,
                          @Arg(desc = "The stair block, e.g. oak_stairs")
                              Pattern stairPattern,
                          @Arg(desc = "A slab block for single gentle steps, e.g. oak_slab", def = "")
                              Pattern slabPattern,
                          @Switch(name = 'u', desc = "Work on ceilings, with upside-down stairs")
                              boolean upsideDown) throws WorldEditException {
        BlockType stairType = stairPattern.applyBlock(region.getMinimumPoint()).getBlockType();
        if (!stairType.getPropertyMap().containsKey("facing")) {
            actor.printError(TextComponent.of(stairType.id() + " is not a stair block, so it cannot face up the "
                + "slope. Use a stair block, e.g. //autostairs stone_brick_stairs"));
            return 0;
        }
        int vDir = upsideDown ? -1 : 1;
        int changed = 0;

        for (BlockVector3 pt : region) {
            BlockState current = editSession.getBlock(pt);
            if (current.getBlockType().getMaterial().isAir()) {
                continue;
            }
            BlockVector3 above = pt.add(0, vDir, 0);
            if (!editSession.getBlock(above).getBlockType().getMaterial().isAir()) {
                continue;
            }

            boolean higherNorth = !editSession.getBlock(pt.add(0, vDir, -1)).getBlockType().getMaterial().isAir();
            boolean higherSouth = !editSession.getBlock(pt.add(0, vDir, 1)).getBlockType().getMaterial().isAir();
            boolean higherEast = !editSession.getBlock(pt.add(1, vDir, 0)).getBlockType().getMaterial().isAir();
            boolean higherWest = !editSession.getBlock(pt.add(-1, vDir, 0)).getBlockType().getMaterial().isAir();

            boolean lowerNorth = editSession.getBlock(pt.add(0, 0, -1)).getBlockType().getMaterial().isAir();
            boolean lowerSouth = editSession.getBlock(pt.add(0, 0, 1)).getBlockType().getMaterial().isAir();
            boolean lowerEast = editSession.getBlock(pt.add(1, 0, 0)).getBlockType().getMaterial().isAir();
            boolean lowerWest = editSession.getBlock(pt.add(-1, 0, 0)).getBlockType().getMaterial().isAir();

            int higherCount = (higherNorth ? 1 : 0) + (higherSouth ? 1 : 0) + (higherEast ? 1 : 0) + (higherWest ? 1 : 0);
            int lowerCount = (lowerNorth ? 1 : 0) + (lowerSouth ? 1 : 0) + (lowerEast ? 1 : 0) + (lowerWest ? 1 : 0);

            if (higherCount == 0 && lowerCount == 0) {
                continue;
            }

            if (slabPattern != null && higherCount == 0 && lowerCount == 1) {
                BlockState slabState = slabPattern.applyBlock(pt).toImmutableState();
                var typeProp = slabState.getBlockType().getPropertyMap().get("type");
                if (typeProp != null) {
                    slabState = applyPropertyString(slabState, typeProp, upsideDown ? "top" : "bottom");
                }
                if (editSession.setBlock(pt, slabState)) {
                    changed++;
                }
                continue;
            }

            String facing;
            String shape = "straight";

            if (higherNorth && !higherSouth) {
                facing = "south";
                if (higherWest) {
                    shape = "inner_right";
                } else if (higherEast) {
                    shape = "inner_left";
                } else if (lowerWest) {
                    shape = "outer_right";
                } else if (lowerEast) {
                    shape = "outer_left";
                }
            } else if (higherSouth && !higherNorth) {
                facing = "north";
                if (higherEast) {
                    shape = "inner_right";
                } else if (higherWest) {
                    shape = "inner_left";
                } else if (lowerEast) {
                    shape = "outer_right";
                } else if (lowerWest) {
                    shape = "outer_left";
                }
            } else if (higherWest && !higherEast) {
                facing = "east";
                if (higherSouth) {
                    shape = "inner_right";
                } else if (higherNorth) {
                    shape = "inner_left";
                } else if (lowerSouth) {
                    shape = "outer_right";
                } else if (lowerNorth) {
                    shape = "outer_left";
                }
            } else if (higherEast && !higherWest) {
                facing = "west";
                if (higherNorth) {
                    shape = "inner_right";
                } else if (higherSouth) {
                    shape = "inner_left";
                } else if (lowerNorth) {
                    shape = "outer_right";
                } else if (lowerSouth) {
                    shape = "outer_left";
                }
            } else if (lowerNorth && !lowerSouth) {
                facing = "north";
                if (lowerEast) {
                    shape = "outer_right";
                } else if (lowerWest) {
                    shape = "outer_left";
                }
            } else if (lowerSouth && !lowerNorth) {
                facing = "south";
                if (lowerWest) {
                    shape = "outer_right";
                } else if (lowerEast) {
                    shape = "outer_left";
                }
            } else if (lowerEast && !lowerWest) {
                facing = "east";
                if (lowerSouth) {
                    shape = "outer_right";
                } else if (lowerNorth) {
                    shape = "outer_left";
                }
            } else if (lowerWest && !lowerEast) {
                facing = "west";
                if (lowerNorth) {
                    shape = "outer_right";
                } else if (lowerSouth) {
                    shape = "outer_left";
                }
            } else {
                continue;
            }

            BlockState targetState = stairPattern.applyBlock(pt).toImmutableState();
            var bType = targetState.getBlockType();
            var facingProp = bType.getPropertyMap().get("facing");
            var halfProp = bType.getPropertyMap().get("half");
            var shapeProp = bType.getPropertyMap().get("shape");

            if (facingProp != null) {
                targetState = applyPropertyString(targetState, facingProp, facing);
            }
            if (halfProp != null) {
                targetState = applyPropertyString(targetState, halfProp, upsideDown ? "top" : "bottom");
            }
            if (shapeProp != null) {
                targetState = applyPropertyString(targetState, shapeProp, shape);
            }

            if (editSession.setBlock(pt, targetState)) {
                changed++;
            }
        }

        actor.printInfo(TextComponent.of(changed == 0
            ? "No step edges found in the selection: select the slope including the air above it."
            : "Placed " + changed + " stairs and slabs along the step edges."));
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static <V> BlockState applyPropertyString(BlockState state, com.sk89q.worldedit.registry.state.Property<V> prop, String valStr) {
        try {
            V val = prop.getValueFor(valStr);
            if (val != null) {
                return state.with(prop, val);
            }
        } catch (Exception _) {
            // Ignored if property value is invalid
        }
        return state;
    }

    @Command(
        name = "/roof",
        desc = "Builds a roof (gable, hip, shed, curved or mansard) over the selection",
        descFooter = "Type the roof type and the pattern in either order (the type defaults to gable), then "
            + "optionally the overhang (blocks the roof reaches past the selection) and the pitch (how steep it "
            + "is: 1 = 45 degrees, 0.5 = gentle, 2 = steep). On a flat selection (1-2 blocks high) the roof "
            + "sits on top and rises with the pitch; on a taller selection it fills the selection's height, "
            + "and the pitch scales that height. -h builds only the roof surface. "
            + "Example: //roof hip spruce_planks 1 0.5"
    )
    @CommandPermissions("worldedit.region.roof")
    @Logging(REGION)
    public int roof(Actor actor, World world, LocalSession session, EditSession editSession,
                    @Selection Region region,
                    @Arg(desc = "The roof type (gable, hip, shed, curved, mansard) and the pattern, then "
                        + "the overhang and pitch", variable = true, def = "")
                        List<String> words,
                    @Switch(name = 'h', desc = "Build only the roof surface (hollow)")
                        boolean hollow) throws WorldEditException {
        Roof.Request request;
        try {
            ShapeFlags flags = ShapeFlags.parse(words, "h", "");
            hollow |= flags.has('h');
            request = Roof.parse(flags.remaining());
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(world);
        context.setExtent(editSession);
        context.setSession(session);
        Pattern pattern = WorldEdit.getInstance().getPatternFactory().parseFromInput(request.pattern(), context);
        int overhang = request.overhang();

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int minX = min.x() - overhang;
        int maxX = max.x() + overhang;
        int minZ = min.z() - overhang;
        int maxZ = max.z() + overhang;
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        int selectionHeight = max.y() - min.y() + 1;
        int baseY = Roof.fillsSelectionHeight(selectionHeight) ? min.y() : max.y();
        double rise = Roof.rise(sizeX, sizeZ, selectionHeight, request.pitch());
        checkCommandArgument(rise <= Roof.MAX_HEIGHT, "That roof would be " + (int) Math.round(rise)
            + " blocks high; the most is " + Roof.MAX_HEIGHT + ". Use a smaller pitch.");
        WorldEdit.getInstance().checkMaxRadius(Math.max(Math.max(sizeX, sizeZ) / 2.0, rise));

        int[][] heights = Roof.heights(request.type(), sizeX, sizeZ, rise);
        int changed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int topY = baseY + heights[x - minX][z - minZ];
                int startY = baseY;
                if (hollow) {
                    int minNeighbor = topY;
                    if (x > minX) {
                        minNeighbor = Math.min(minNeighbor, baseY + heights[x - minX - 1][z - minZ]);
                    }
                    if (x < maxX) {
                        minNeighbor = Math.min(minNeighbor, baseY + heights[x - minX + 1][z - minZ]);
                    }
                    if (z > minZ) {
                        minNeighbor = Math.min(minNeighbor, baseY + heights[x - minX][z - minZ - 1]);
                    }
                    if (z < maxZ) {
                        minNeighbor = Math.min(minNeighbor, baseY + heights[x - minX][z - minZ + 1]);
                    }
                    startY = Math.max(baseY, minNeighbor);
                }
                for (int y = startY; y <= topY; y++) {
                    if (editSession.setBlock(BlockVector3.at(x, y, z), pattern)) {
                        changed++;
                    }
                }
            }
        }

        actor.printInfo(TextComponent.of("Built a " + request.type().id() + " roof over " + sizeX + " x " + sizeZ
            + " blocks, " + ((int) Math.round(rise) + 1) + " high: " + changed + " blocks changed."));
        return changed;
    }

    @Command(
        name = "/insetfaces",
        aliases = { "/insetface", "/panelinset" },
        desc = "Sets recessed panels into the six faces of the selection, leaving a border around each",
        descFooter = "The margin is the width of the border left around each panel; the depth is how far the panel "
            + "is set back into the wall (0 = flush, the panel is just replaced). Example: //insetfaces 1 "
            + "polished_andesite 1 on a selected wall gives it a recessed panel with a one-block frame."
    )
    @CommandPermissions("worldedit.region.insetfaces")
    @Logging(REGION)
    public int insetFaces(Actor actor, EditSession editSession,
                          @Selection Region region,
                          @Arg(desc = "The width of the border around each panel, in blocks", def = "1")
                              int margin,
                          @Arg(desc = "The panel block")
                              Pattern pattern,
                          @Arg(desc = "How far the panel is set back into the wall (0 = flush)", def = "1")
                              int depth) throws WorldEditException {
        checkCommandArgument(margin >= 0, "The margin must be 0 or more");
        checkCommandArgument(depth >= 0 && depth <= 64, "The depth must be between 0 and 64");

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        BlockVector3 size = max.subtract(min).add(1, 1, 1);
        int maxMargin = InsetFaces.maxMargin(size.x(), size.y(), size.z());
        if (margin > maxMargin) {
            actor.printError(TextComponent.of("A margin of " + margin + " leaves no room for a panel on this "
                + size.x() + " x " + size.y() + " x " + size.z() + " selection; use a margin of at most "
                + maxMargin + "."));
            return 0;
        }
        int faces = InsetFaces.faceCount(size.x(), size.y(), size.z(), margin);
        int changed = 0;

        // North face (z = min.z())
        if (min.x() + margin <= max.x() - margin && min.y() + margin <= max.y() - margin) {
            changed += applyInsetFace(editSession, pattern,
                min.x() + margin, max.x() - margin,
                min.y() + margin, max.y() - margin,
                min.z(), BlockVector3.at(0, 0, 1), depth, 0);
        }

        // South face (z = max.z())
        if (max.z() != min.z() && min.x() + margin <= max.x() - margin && min.y() + margin <= max.y() - margin) {
            changed += applyInsetFace(editSession, pattern,
                min.x() + margin, max.x() - margin,
                min.y() + margin, max.y() - margin,
                max.z(), BlockVector3.at(0, 0, -1), depth, 0);
        }

        // West face (x = min.x())
        if (min.z() + margin <= max.z() - margin && min.y() + margin <= max.y() - margin) {
            changed += applyInsetFace(editSession, pattern,
                min.z() + margin, max.z() - margin,
                min.y() + margin, max.y() - margin,
                min.x(), BlockVector3.at(1, 0, 0), depth, 1);
        }

        // East face (x = max.x())
        if (max.x() != min.x() && min.z() + margin <= max.z() - margin && min.y() + margin <= max.y() - margin) {
            changed += applyInsetFace(editSession, pattern,
                min.z() + margin, max.z() - margin,
                min.y() + margin, max.y() - margin,
                max.x(), BlockVector3.at(-1, 0, 0), depth, 1);
        }

        // Down face (y = min.y())
        if (min.x() + margin <= max.x() - margin && min.z() + margin <= max.z() - margin) {
            changed += applyInsetFace(editSession, pattern,
                min.x() + margin, max.x() - margin,
                min.z() + margin, max.z() - margin,
                min.y(), BlockVector3.at(0, 1, 0), depth, 2);
        }

        // Up face (y = max.y())
        if (max.y() != min.y() && min.x() + margin <= max.x() - margin && min.z() + margin <= max.z() - margin) {
            changed += applyInsetFace(editSession, pattern,
                min.x() + margin, max.x() - margin,
                min.z() + margin, max.z() - margin,
                max.y(), BlockVector3.at(0, -1, 0), depth, 2);
        }

        actor.printInfo(TextComponent.of("Set panels into " + faces + (faces == 1 ? " face" : " faces")
            + " of the selection: " + changed + " blocks changed."));
        return changed;
    }

    private static int applyInsetFace(EditSession editSession, Pattern pattern,
                                      int minU, int maxU, int minV, int maxV,
                                      int fixedCoord, BlockVector3 inward, int depth, int planeType)
            throws WorldEditException {
        int count = 0;
        BlockType airType = com.sk89q.worldedit.world.block.BlockTypes.AIR;
        for (int u = minU; u <= maxU; u++) {
            for (int v = minV; v <= maxV; v++) {
                BlockVector3 surfacePt = switch (planeType) {
                    case 0 -> BlockVector3.at(u, v, fixedCoord);
                    case 1 -> BlockVector3.at(fixedCoord, v, u);
                    default -> BlockVector3.at(u, fixedCoord, v);
                };

                if (depth == 0) {
                    if (editSession.setBlock(surfacePt, pattern)) {
                        count++;
                    }
                } else {
                    for (int d = 0; d < depth; d++) {
                        BlockVector3 carvePt = surfacePt.add(inward.multiply(d));
                        if (airType != null && editSession.setBlock(carvePt, airType.getDefaultState())) {
                            count++;
                        }
                    }
                    BlockVector3 backPt = surfacePt.add(inward.multiply(depth));
                    if (editSession.setBlock(backPt, pattern)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Command(
        name = "/move",
        desc = "Move the contents of the selection"
    )
    @CommandPermissions("worldedit.region.move")
    @Logging(ORIENTATION_REGION)
    public int move(Actor actor, World world, EditSession editSession, LocalSession session,
                    @Selection Region region,
                    @Arg(desc = "number of times to apply the offset", def = "1")
                        int multiplier,
                    @Arg(desc = "The offset to move", def = Offset.FORWARD)
                    @Offset
                        BlockVector3 offset,
                    @Arg(desc = "The pattern of blocks to leave", def = "air")
                        Pattern replace,
                    @Switch(name = 's', desc = "Shift the selection to the target location")
                        boolean moveSelection,
                    @Switch(name = 'a', desc = "Ignore air blocks")
                        boolean ignoreAirBlocks,
                    @Switch(name = 'e', desc = "Also copy entities")
                        boolean copyEntities,
                    @Switch(name = 'b', desc = "Also copy biomes")
                        boolean copyBiomes,
                    @ArgFlag(name = 'm', desc = "Set the include mask, non-matching blocks become air")
                        Mask mask) throws WorldEditException {
        checkCommandArgument(multiplier >= 1, "Multiplier must be >= 1");

        Mask combinedMask;
        if (ignoreAirBlocks) {
            if (mask == null) {
                combinedMask = new ExistingBlockMask(editSession);
            } else {
                combinedMask = new MaskIntersection(mask, new ExistingBlockMask(editSession));
            }
        } else {
            combinedMask = mask;
        }

        int affected = editSession.moveRegion(region, offset, multiplier, copyEntities, copyBiomes, combinedMask, replace);

        if (moveSelection) {
            try {
                region.shift(offset.multiply(multiplier));

                session.getRegionSelector(world).learnChanges();
                session.getRegionSelector(world).explainRegionAdjust(actor, session);
            } catch (RegionOperationException e) {
                actor.printError(e.getRichMessage());
            }
        }

        actor.printInfo(TranslatableComponent.of("worldedit.move.moved", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/stack",
        desc = "Repeat the contents of the selection"
    )
    @CommandPermissions("worldedit.region.stack")
    @Logging(ORIENTATION_REGION)
    public int stack(Actor actor, World world, EditSession editSession, LocalSession session,
                     @Selection Region region,
                     @Arg(desc = "# of copies to stack", def = "1")
                         int count,
                     @Arg(desc = "How far to move the contents each stack", def = Offset.FORWARD)
                     @Offset
                         BlockVector3 offset,
                     @Switch(name = 's', desc = "Shift the selection to the last stacked copy")
                         boolean moveSelection,
                     @Switch(name = 'a', desc = "Ignore air blocks")
                         boolean ignoreAirBlocks,
                     @Switch(name = 'e', desc = "Also copy entities")
                         boolean copyEntities,
                     @Switch(name = 'b', desc = "Also copy biomes")
                         boolean copyBiomes,
                     @Switch(name = 'r', desc = "Use block units")
                        boolean blockUnits,
                     @ArgFlag(name = 'm', desc = "Set the include mask, non-matching blocks become air")
                         Mask mask) throws WorldEditException {
        checkCommandArgument(count >= 1, "Count must be >= 1");

        Mask combinedMask;
        if (ignoreAirBlocks) {
            if (mask == null) {
                combinedMask = new ExistingBlockMask(editSession);
            } else {
                combinedMask = new MaskIntersection(mask, new ExistingBlockMask(editSession));
            }
        } else {
            combinedMask = mask;
        }

        int affected;
        if (blockUnits) {
            affected = editSession.stackRegionBlockUnits(region, offset, count, copyEntities, copyBiomes, combinedMask);
        } else {
            affected = editSession.stackCuboidRegion(region, offset, count, copyEntities, copyBiomes, combinedMask);
        }

        if (moveSelection) {
            try {
                final BlockVector3 size = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(1, 1, 1);

                final BlockVector3 shiftSize = blockUnits ? offset : offset.multiply(size);
                final BlockVector3 shiftVector = shiftSize.multiply(count);
                region.shift(shiftVector);

                session.getRegionSelector(world).learnChanges();
                session.getRegionSelector(world).explainRegionAdjust(actor, session);
            } catch (RegionOperationException e) {
                actor.printError(e.getRichMessage());
            }
        }

        actor.printInfo(TranslatableComponent.of("worldedit.stack.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/regen",
        desc = "Regenerates the contents of the selection"
    )
    @CommandPermissions("worldedit.regen")
    @Logging(REGION)
    void regenerate(Actor actor, World world, LocalSession session, EditSession editSession,
                    @Selection Region region,
                    @Arg(desc = "The seed to regenerate with, otherwise uses world seed", def = "")
                        Long seed,
                    @Switch(name = 'b', desc = "Regenerate biomes as well")
                        boolean regenBiomes,
                    @Switch(name = 'c', desc = "Regenerate to the clipboard")
                        boolean toClipboard) throws WorldEditException {
        Mask mask = session.getMask();
        boolean success;
        try {
            session.setMask(null);
            RegenOptions options = RegenOptions.builder()
                .seed(seed)
                .regenBiomes(regenBiomes)
                .build();
            Extent outputExtent = editSession;
            BlockArrayClipboard clipboard = null;
            if (toClipboard) {
                clipboard = new BlockArrayClipboard(region);
                clipboard.setOrigin(session.getPlacementPosition(actor));
                outputExtent = clipboard;
            }
            success = world.regenerate(region, outputExtent, options);
            if (success && toClipboard) {
                session.setClipboard(new ClipboardHolder(clipboard));
            }
        } finally {
            session.setMask(mask);
        }
        if (success) {
            actor.printInfo(TranslatableComponent.of("worldedit.regen.regenerated"));
        } else {
            actor.printError(TranslatableComponent.of("worldedit.regen.failed"));
        }
    }

    @Command(
        name = "/deform",
        desc = "Deforms a selected region with an expression",
        descFooter =
            """
            The expression is executed for each block and is expected
            to modify the variables x, y and z to point to a new block
            to fetch. For details, see https://ehub.to/we/expr
            """
    )
    @CommandPermissions("worldedit.region.deform")
    @Logging(ALL)
    public int deform(Actor actor, LocalSession session, EditSession editSession,
                      @Selection Region region,
                      @Arg(desc = "The expression to use", variable = true)
                          List<String> expression,
                      @Switch(name = 'r', desc = "Use the game's coordinate origin")
                          boolean useRawCoords,
                      @Switch(name = 'o', desc = "Use the placement's coordinate origin")
                          boolean offsetPlacement,
                      @Switch(name = 'c', desc = "Use the selection's center as origin")
                          boolean offsetCenter,
                      @Switch(name = 'l', desc = "Fetch from the clipboard instead of the world")
                          boolean useClipboard) throws WorldEditException {
        final Transform targetTransform = TransformUtil.createTransformForExpressionCommand(actor, session, region, useRawCoords, offsetPlacement, offsetCenter);

        final InputExtent sourceExtent;
        final Transform sourceTransform;
        if (useClipboard) {
            final Clipboard clipboard = session.getClipboard().getClipboard();
            sourceExtent = clipboard;

            final Vector3 clipboardMin = clipboard.getMinimumPoint().toVector3();
            final Vector3 clipboardMax = clipboard.getMaximumPoint().toVector3();

            sourceTransform = TransformUtil.createTransformForExpressionCommand(useRawCoords, offsetPlacement, offsetCenter, clipboardMin, clipboardMax, clipboardMin);
        } else {
            sourceExtent = editSession.getWorld();
            sourceTransform = targetTransform;
        }

        try {
            final int affected = editSession.deformRegion(region, targetTransform, String.join(" ", expression), session.getTimeout(), sourceExtent, sourceTransform);
            if (actor instanceof Player player) {
                player.findFreePosition();
            }
            actor.printInfo(TranslatableComponent.of("worldedit.deform.deformed", TextComponent.of(affected)));
            return affected;
        } catch (ExpressionException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
    }

    @Command(
        name = "/hollow",
        desc = "Hollows out the object contained in this selection",
        descFooter = "Thickness is measured in manhattan distance."
    )
    @CommandPermissions("worldedit.region.hollow")
    @Logging(REGION)
    public int hollow(Actor actor, EditSession editSession,
                      @Selection Region region,
                      @Arg(desc = "Thickness of the shell to leave", def = "0")
                          int thickness,
                      @Arg(desc = "The pattern of blocks to replace the hollowed area with", def = "air")
                          Pattern pattern) throws WorldEditException {
        checkCommandArgument(thickness >= 0, "Thickness must be >= 0");

        int affected = editSession.hollowOutRegion(region, thickness, pattern);
        actor.printInfo(TranslatableComponent.of("worldedit.hollow.changed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/forest",
        desc = "Make a forest within the region"
    )
    @CommandPermissions("worldedit.region.forest")
    @Logging(REGION)
    public int forest(Actor actor, EditSession editSession, @Selection Region region,
                      @Arg(desc = "The type of tree to place", def = "tree")
                          TreeType type,
                      @Arg(desc = "The density of the forest", def = "5")
                          double density) throws WorldEditException {
        checkCommandArgument(0 <= density && density <= 100, "Density must be in [0, 100]");
        int affected = editSession.makeForest(region, density / 100, type);
        actor.printInfo(TranslatableComponent.of("worldedit.forest.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/flora",
        desc = "Make flora within the region"
    )
    @CommandPermissions("worldedit.region.flora")
    @Logging(REGION)
    public int flora(Actor actor, EditSession editSession, @Selection Region region,
                     @Arg(desc = "The density of the forest", def = "5")
                         double density) throws WorldEditException {
        checkCommandArgument(0 <= density && density <= 100, "Density must be in [0, 100]");
        density = density / 100;
        FloraGenerator generator = new FloraGenerator(editSession);
        GroundFunction ground = new GroundFunction(new ExistingBlockMask(editSession), generator);
        LayerVisitor visitor = new LayerVisitor(asFlatRegion(region), minimumBlockY(region), maximumBlockY(region), ground);
        visitor.setMask(new NoiseFilter2D(new RandomNoise(), density));
        Operations.completeLegacy(visitor);

        int affected = ground.getAffected();
        actor.printInfo(TranslatableComponent.of("worldedit.flora.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/update",
        desc = "Apply side effects to your selection"
    )
    @CommandPermissions("worldedit.update")
    void update(Actor actor, LocalSession session, World injectedWorld,
                @Arg(desc = "The side effects", def = "")
                SideEffectSet sideEffectSet) throws WorldEditException {
        if (sideEffectSet == null) {
            // Use defaults if none supplied.
            sideEffectSet = SideEffectSet.defaults();
        }
        RegionFunction apply = new ApplySideEffect(injectedWorld, sideEffectSet);
        if (session.getMask() != null) {
            apply = new RegionMaskingFilter(session.getMask(), apply);
        }
        if (session.getBlockChangeLimit() >= 0) {
            // Only apply limits if one is set.
            apply = new ChangeLimitingFilter(session.getBlockChangeLimit(), apply);
        }

        RegionVisitor visitor = new RegionVisitor(session.getSelection(injectedWorld), apply);
        Operations.complete(visitor);

        actor.printInfo(TranslatableComponent.of("worldedit.update"));
    }
}
