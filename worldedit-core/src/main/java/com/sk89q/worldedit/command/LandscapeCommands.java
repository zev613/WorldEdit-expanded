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
import com.sk89q.worldedit.command.util.CrosshairPlacement;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.generator.landscape.Corridor;
import com.sk89q.worldedit.function.generator.landscape.ExtentTerrainSampler;
import com.sk89q.worldedit.function.generator.landscape.HeightmapPathFinder;
import com.sk89q.worldedit.function.generator.landscape.ParametricTree;
import com.sk89q.worldedit.function.generator.landscape.PathGrader;
import com.sk89q.worldedit.function.generator.landscape.PoissonDiskSampler;
import com.sk89q.worldedit.function.generator.landscape.RiverChannel;
import com.sk89q.worldedit.function.generator.landscape.RiverTracer;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.FlatRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;
import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;
import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;

/**
 * Commands that shape landscapes: roads, rivers, scattered objects and trees.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class LandscapeCommands {

    private static final int PATH_MARGIN = 24;
    private static final int PATH_MAX_AREA = 512 * 512;
    private static final int PATH_HEADROOM = 3;
    private static final int PATH_PILLAR_SPACING = 6;
    private static final int PATH_MAX_PILLAR = 96;
    private static final int SCAN_MARGIN_Y = 64;

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public LandscapeCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
        name = "/path",
        aliases = { "/road" },
        desc = "Builds a terrain-following road between the selection points",
        descFooter = "Cuboid selections go from pos1 to pos2; polygonal and convex selections pass through "
            + "their points in order. The route avoids steep slopes and water, is graded so that each "
            + "step rises at most one block, tunnels through hills and is bridged with pillars over gaps."
    )
    @CommandPermissions("worldedit.generation.path")
    @Logging(REGION)
    public int path(Actor actor, World world, EditSession editSession,
                    @Selection Region region,
                    @Arg(desc = "The pattern of blocks to build the road with")
                        Pattern pattern,
                    @Arg(desc = "The width of the road", def = "3")
                        int width,
                    @Arg(desc = "The steepest rise per block that does not need grading", def = "1")
                        double maxSlope,
                    @ArgFlag(name = 's', desc = "Place stairs of this pattern on one-block height changes")
                        Pattern stairs) throws WorldEditException {
        checkCommandArgument(width >= 1, "Width must be at least 1");
        checkCommandArgument(maxSlope > 0, "Maximum slope must be greater than 0");
        worldEdit.checkMaxRadius(width / 2.0);

        List<BlockVector3> waypoints = new ArrayList<>();
        if (region instanceof CuboidRegion cuboid) {
            waypoints.add(cuboid.getPos1());
            waypoints.add(cuboid.getPos2());
        } else if (region instanceof Polygonal2DRegion polygon) {
            int y = polygon.getMaximumY();
            for (BlockVector2 point : polygon.getPoints()) {
                waypoints.add(point.toBlockVector3(y));
            }
        } else if (region instanceof ConvexPolyhedralRegion convex) {
            waypoints.addAll(convex.getVertices());
        } else {
            actor.printError(TextComponent.of("//path requires a cuboid, polygonal or convex selection"));
            return 0;
        }
        List<int[]> columns = new ArrayList<>();
        for (BlockVector3 waypoint : waypoints) {
            int[] column = {waypoint.x(), waypoint.z()};
            if (columns.isEmpty() || columns.getLast()[0] != column[0] || columns.getLast()[1] != column[1]) {
                columns.add(column);
            }
        }
        checkCommandArgument(columns.size() >= 2, "The selection points must be at least two different columns");

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] column : columns) {
            minX = Math.min(minX, column[0]);
            minZ = Math.min(minZ, column[1]);
            maxX = Math.max(maxX, column[0]);
            maxZ = Math.max(maxZ, column[1]);
        }
        minX -= PATH_MARGIN;
        minZ -= PATH_MARGIN;
        maxX += PATH_MARGIN;
        maxZ += PATH_MARGIN;
        long area = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        checkCommandArgument(area <= PATH_MAX_AREA,
            "The path search area is too large (" + area + " columns, maximum " + PATH_MAX_AREA
                + "). Use selection points that are closer together.");

        BlockVector3 regionMin = region.getMinimumPoint();
        BlockVector3 regionMax = region.getMaximumPoint();
        int scanMinY = Math.max(world.getMinY(), regionMin.y() - SCAN_MARGIN_Y);
        int scanMaxY = Math.min(world.getMaxY(), regionMax.y() + SCAN_MARGIN_Y);
        ExtentTerrainSampler terrain = new ExtentTerrainSampler(editSession, scanMinY, scanMaxY);

        HeightmapPathFinder finder = new HeightmapPathFinder(terrain, minX, minZ, maxX, maxZ, maxSlope);
        List<int[]> route = new ArrayList<>();
        for (int i = 0; i + 1 < columns.size(); i++) {
            int[] from = columns.get(i);
            int[] to = columns.get(i + 1);
            List<int[]> leg = finder.findPath(from[0], from[1], to[0], to[1]);
            if (leg == null) {
                actor.printError(TextComponent.of("No route could be found between " + from[0] + "," + from[1]
                    + " and " + to[0] + "," + to[1] + "."));
                return 0;
            }
            route.addAll(route.isEmpty() ? leg : leg.subList(1, leg.size()));
        }

        int[] raw = new int[route.size()];
        for (int i = 0; i < route.size(); i++) {
            raw[i] = HeightmapPathFinder.pathHeight(terrain, route.get(i)[0], route.get(i)[1]);
        }
        int[] graded = PathGrader.grade(raw, 2, 1);

        double radius = (width - 1) / 2.0 + 0.3;
        Map<BlockVector2, Corridor.Column> band = Corridor.rasterize(route, radius);
        BlockState air = BlockTypes.AIR.getDefaultState();
        int changed = 0;
        int pillars = 0;
        for (Map.Entry<BlockVector2, Corridor.Column> entry : band.entrySet()) {
            int x = entry.getKey().x();
            int z = entry.getKey().z();
            int index = entry.getValue().nearestIndex();
            int h = graded[index];
            for (int y = h + 1; y <= h + PATH_HEADROOM; y++) {
                if (editSession.setBlock(BlockVector3.at(x, y, z), air)) {
                    changed++;
                }
            }
            if (editSession.setBlock(BlockVector3.at(x, h, z), pattern)) {
                changed++;
            }

            int ground = terrain.groundBelow(x, z, h - 1, Math.max(world.getMinY(), h - PATH_MAX_PILLAR));
            int gap = h - 1 - ground;
            boolean pillar = entry.getValue().distance() <= 0.5 && index % PATH_PILLAR_SPACING == 0;
            if (gap > 0 && (gap <= 2 || pillar)) {
                if (gap > 2) {
                    pillars++;
                }
                for (int y = ground + 1; y < h; y++) {
                    if (editSession.setBlock(BlockVector3.at(x, y, z), pattern)) {
                        changed++;
                    }
                }
            }

            if (stairs != null) {
                int[] here = route.get(index);
                int[] towards = null;
                if (index + 1 < route.size() && graded[index + 1] == h + 1) {
                    towards = route.get(index + 1);
                } else if (index > 0 && graded[index - 1] == h + 1) {
                    towards = route.get(index - 1);
                }
                if (towards != null) {
                    BlockVector3 stairPos = BlockVector3.at(x, h + 1, z);
                    BaseBlock stair = withProperty(stairs.applyBlock(stairPos), "facing",
                        facing(towards[0] - here[0], towards[1] - here[1]));
                    if (editSession.setBlock(stairPos, stair)) {
                        changed++;
                    }
                }
            }
        }

        actor.printInfo(TextComponent.of("Built a path " + route.size() + " blocks long with " + pillars
            + " support pillar columns; " + changed + " blocks changed."));
        return changed;
    }

    @Command(
        name = "/river",
        desc = "Carves a river that flows downhill from your placement position",
        descFooter = "Follows the steepest descent until it reaches existing water, the maximum length, or a pit "
            + "(where it ends in a small pond). The water level never rises downstream; low banks get levees."
    )
    @CommandPermissions("worldedit.generation.river")
    @Logging(PLACEMENT)
    public int river(Actor actor, World world, LocalSession session, EditSession editSession,
                     @Arg(desc = "The width of the channel", def = "5")
                         int width,
                     @Arg(desc = "The water depth on the centre line", def = "3")
                         int depth,
                     @ArgFlag(name = 'l', desc = "The maximum length of the river", def = "256")
                         int maxLength,
                     @ArgFlag(name = 'b', desc = "The pattern for the river bed and levees", def = "sand")
                         Pattern bed,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        checkCommandArgument(width >= 1, "Width must be at least 1");
        checkCommandArgument(depth >= 1, "Depth must be at least 1");
        checkCommandArgument(maxLength >= 1 && maxLength <= 4096, "Maximum length must be between 1 and 4096");
        worldEdit.checkMaxRadius(width / 2.0);
        worldEdit.checkMaxRadius(depth);
        worldEdit.checkMaxRadius(maxLength / 2.0);

        BlockVector3 start = CrosshairPlacement.placement(session, actor, atCrosshair);
        int scanMaxY = Math.min(world.getMaxY(), start.y() + 32);
        ExtentTerrainSampler terrain = new ExtentTerrainSampler(editSession, world.getMinY(), scanMaxY);
        if (terrain.isWater(start.x(), start.z())) {
            actor.printError(TextComponent.of("The river must start on dry land."));
            return 0;
        }

        RiverTracer.Result course = new RiverTracer(terrain, maxLength, 4096).trace(start.x(), start.z());
        boolean pond = course.reason() == RiverTracer.StopReason.PIT;
        RiverChannel.Plan plan = RiverChannel.plan(terrain, course.points(), width, depth, pond);

        BlockState water = BlockTypes.WATER.getDefaultState();
        BlockState air = BlockTypes.AIR.getDefaultState();
        int changed = 0;
        for (RiverChannel.ChannelColumn column : plan.channel()) {
            for (int y = column.bedBottomY(); y <= column.bedY(); y++) {
                if (editSession.setBlock(BlockVector3.at(column.x(), y, column.z()), bed)) {
                    changed++;
                }
            }
            for (int y = column.bedY() + 1; y <= column.waterTopY(); y++) {
                if (editSession.setBlock(BlockVector3.at(column.x(), y, column.z()), water)) {
                    changed++;
                }
            }
            for (int y = column.waterTopY() + 1; y <= column.clearTopY(); y++) {
                if (editSession.setBlock(BlockVector3.at(column.x(), y, column.z()), air)) {
                    changed++;
                }
            }
        }
        for (RiverChannel.LeveeColumn levee : plan.levees()) {
            for (int y = levee.fromY(); y <= levee.toY(); y++) {
                if (editSession.setBlock(BlockVector3.at(levee.x(), y, levee.z()), bed)) {
                    changed++;
                }
            }
        }

        String ending = switch (course.reason()) {
            case WATER -> "joined existing water";
            case MAX_LENGTH -> "reached the maximum length";
            case PIT -> "ended in a pond";
        };
        actor.printInfo(TextComponent.of("Carved a river " + course.points().size() + " blocks long that "
            + ending + "; " + changed + " blocks changed."));
        return changed;
    }

    @Command(
        name = "/scatter",
        desc = "Scatters copies of the clipboard over the surface of the selection",
        descFooter = "Positions keep a minimum spacing (0 = clipboard footprint). Each copy is randomly rotated "
            + "by a multiple of 90 degrees and its lowest layer is set on the surface."
    )
    @CommandPermissions("worldedit.generation.scatter")
    @Logging(REGION)
    public int scatter(Actor actor, LocalSession session, EditSession editSession,
                       @Selection Region region,
                       @Arg(desc = "The number of copies to place")
                           int count,
                       @Arg(desc = "The minimum distance between copies (0 = clipboard footprint)", def = "0")
                           double minSpacing,
                       @ArgFlag(name = 'k', desc = "Use this named clipboard", def = "")
                           String clipName,
                       @ArgFlag(name = 'o', desc = "Vertical offset of each copy", def = "0")
                           int verticalOffset,
                       @ArgFlag(name = 'r', desc = "Random seed", def = "")
                           String seed,
                       @Switch(name = 'n', desc = "Do not rotate the copies")
                           boolean noRotate,
                       @Switch(name = 'a', desc = "Skip air blocks")
                           boolean ignoreAirBlocks) throws WorldEditException {
        checkCommandArgument(count >= 1 && count <= 1000, "Count must be between 1 and 1000");
        checkCommandArgument(minSpacing >= 0, "Minimum spacing must not be negative");

        ClipboardHolder holder = clipName.isEmpty() ? session.getClipboard() : session.getNamedClipboard(clipName);
        Clipboard clipboard = holder.getClipboard();
        Transform baseTransform = holder.getTransform();
        BlockVector3 clipMin = clipboard.getRegion().getMinimumPoint().subtract(clipboard.getOrigin());
        BlockVector3 clipMax = clipboard.getRegion().getMaximumPoint().subtract(clipboard.getOrigin());
        double spacing = minSpacing > 0 ? minSpacing
            : Math.max(clipMax.x() - clipMin.x() + 1, clipMax.z() - clipMin.z() + 1);

        long seedValue = parseSeed(seed);
        Random random = new Random(seedValue);

        BlockVector3 regionMin = region.getMinimumPoint();
        BlockVector3 regionMax = region.getMaximumPoint();
        List<int[]> candidates = new ArrayList<>();
        if (region instanceof FlatRegion flat) {
            for (BlockVector2 column : flat.asFlatRegion()) {
                candidates.add(new int[] {column.x(), column.z()});
            }
        } else {
            int midY = (regionMin.y() + regionMax.y()) / 2;
            for (int x = regionMin.x(); x <= regionMax.x(); x++) {
                for (int z = regionMin.z(); z <= regionMax.z(); z++) {
                    if (region.contains(BlockVector3.at(x, midY, z))) {
                        candidates.add(new int[] {x, z});
                    }
                }
            }
        }

        ExtentTerrainSampler terrain = new ExtentTerrainSampler(editSession, regionMin.y(), regionMax.y());
        Map<BlockVector2, Boolean> usable = new HashMap<>();
        List<int[]> picked = PoissonDiskSampler.sample(candidates, count, spacing, random, 30,
            c -> usable.computeIfAbsent(BlockVector2.at(c[0], c[1]), _ -> {
                int y = terrain.surfaceY(c[0], c[1]);
                return !terrain.isWater(c[0], c[1])
                    && ExtentTerrainSampler.isGround(editSession.getBlock(BlockVector3.at(c[0], y, c[1])))
                    && region.contains(BlockVector3.at(c[0], y, c[1]));
            }));

        int placed = 0;
        for (int[] point : picked) {
            Transform transform = baseTransform;
            if (!noRotate) {
                transform = transform.combine(new AffineTransform().rotateY(90 * random.nextInt(4)));
            }
            double lowX = Double.POSITIVE_INFINITY;
            double lowY = Double.POSITIVE_INFINITY;
            double lowZ = Double.POSITIVE_INFINITY;
            double highX = Double.NEGATIVE_INFINITY;
            double highZ = Double.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 8; corner++) {
                Vector3 c = transform.apply(Vector3.at(
                    (corner & 1) == 0 ? clipMin.x() : clipMax.x(),
                    (corner & 2) == 0 ? clipMin.y() : clipMax.y(),
                    (corner & 4) == 0 ? clipMin.z() : clipMax.z()));
                lowX = Math.min(lowX, c.x());
                lowY = Math.min(lowY, c.y());
                lowZ = Math.min(lowZ, c.z());
                highX = Math.max(highX, c.x());
                highZ = Math.max(highZ, c.z());
            }
            int surface = terrain.surfaceY(point[0], point[1]);
            BlockVector3 to = BlockVector3.at(
                point[0] - Math.floor((lowX + highX) / 2 + 0.5),
                surface + 1 + verticalOffset - (int) Math.floor(lowY + 0.5),
                point[1] - Math.floor((lowZ + highZ) / 2 + 0.5));

            ClipboardHolder copy = new ClipboardHolder(clipboard);
            copy.setTransform(transform);
            Operation operation = copy.createPaste(editSession)
                .to(to)
                .ignoreAirBlocks(ignoreAirBlocks)
                .build();
            Operations.completeLegacy(operation);
            placed++;
        }

        String summary = "Placed " + placed + " of " + count + " copies (seed " + seedValue + ").";
        if (placed < count) {
            summary += " Not enough room for more; try a smaller spacing or a larger selection.";
        }
        actor.printInfo(TextComponent.of(summary));
        return placed;
    }

    @Command(
        name = "/ptree",
        aliases = { "/paramtree" },
        desc = "Generates a parametric tree at your placement position",
        descFooter = "A trunk with branches from its upper 60% that end in leaf blobs, plus a crown. "
            + "Logs are oriented along their segment when the log block has an axis property."
    )
    @CommandPermissions("worldedit.generation.ptree")
    @Logging(PLACEMENT)
    public int ptree(Actor actor, LocalSession session, EditSession editSession,
                     @Arg(desc = "The pattern of blocks for the trunk and branches")
                         Pattern logPattern,
                     @Arg(desc = "The pattern of blocks for the leaves")
                         Pattern leavesPattern,
                     @Arg(desc = "The height of the trunk", def = "12")
                         int height,
                     @Arg(desc = "The number of branches", def = "4")
                         int branches,
                     @Arg(desc = "The radius of the leaf blobs", def = "3")
                         double leafRadius,
                     @ArgFlag(name = 'l', desc = "Lean of the trunk in degrees (0-60)", def = "0")
                         double lean,
                     @ArgFlag(name = 't', desc = "Trunk thickness (1 or 2)", def = "1")
                         int thickness,
                     @ArgFlag(name = 'r', desc = "Random seed", def = "")
                         String seed,
                     @Switch(name = 'd', desc = "Let the leaves decay naturally (by default they are made persistent)")
                         boolean decay,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        checkCommandArgument(height >= 3 && height <= 256, "Height must be between 3 and 256");
        checkCommandArgument(branches >= 0 && branches <= 64, "Branches must be between 0 and 64");
        checkCommandArgument(leafRadius >= 1 && leafRadius <= 32, "Leaf radius must be between 1 and 32");
        checkCommandArgument(lean >= 0 && lean <= 60, "Lean must be between 0 and 60 degrees");
        checkCommandArgument(thickness == 1 || thickness == 2, "Thickness must be 1 or 2");
        worldEdit.checkMaxRadius(height / 2.0);
        worldEdit.checkMaxRadius(leafRadius);

        long seedValue = parseSeed(seed);
        Random random = new Random(seedValue);
        ParametricTree.Skeleton skeleton = ParametricTree.generate(random, height, branches, leafRadius, lean, thickness);
        ParametricTree.Voxels voxels = ParametricTree.rasterize(skeleton, random);

        BlockVector3 base = CrosshairPlacement.placement(session, actor, atCrosshair);
        int changed = 0;
        for (Map.Entry<BlockVector3, ParametricTree.Axis> log : voxels.logs().entrySet()) {
            BlockVector3 pos = base.add(log.getKey());
            BaseBlock block = withProperty(logPattern.applyBlock(pos), "axis",
                log.getValue().name().toLowerCase(Locale.ROOT));
            if (editSession.setBlock(pos, block)) {
                changed++;
            }
        }
        for (BlockVector3 leaf : voxels.leaves()) {
            BlockVector3 pos = base.add(leaf);
            BlockState existing = editSession.getBlock(pos);
            if (!existing.getBlockType().getMaterial().isAir()
                && !existing.getBlockType().getMaterial().isReplacedDuringPlacement()
                && !existing.getBlockType().id().endsWith("_leaves")) {
                continue;
            }
            BaseBlock leafBlock = leavesPattern.applyBlock(pos);
            if (!decay) {
                // Generated leaves can be far from a log, so stop them from decaying
                leafBlock = withProperty(leafBlock, "persistent", "true");
            }
            if (editSession.setBlock(pos, leafBlock)) {
                changed++;
            }
        }

        actor.printInfo(TextComponent.of("Parametric tree generated (seed " + seedValue + "); "
            + changed + " blocks changed."));
        return changed;
    }

    private static long parseSeed(String seed) {
        if (seed == null || seed.isEmpty()) {
            return new Random().nextLong();
        }
        try {
            return Long.parseLong(seed);
        } catch (NumberFormatException _) {
            return seed.hashCode();
        }
    }

    private static String facing(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? "east" : "west";
        }
        return dz >= 0 ? "south" : "north";
    }

    /**
     * Sets a property on a block if the block has it and the value is valid.
     */
    @SuppressWarnings("unchecked")
    private static BaseBlock withProperty(BaseBlock block, String name, String value) {
        Property<Object> property = (Property<Object>) block.getBlockType().getPropertyMap().get(name);
        if (property == null) {
            return block;
        }
        Object parsed;
        try {
            parsed = property.getValueFor(value);
        } catch (IllegalArgumentException _) {
            return block;
        }
        if (parsed == null || !property.values().contains(parsed)) {
            return block;
        }
        return block.with(property, parsed);
    }
}
