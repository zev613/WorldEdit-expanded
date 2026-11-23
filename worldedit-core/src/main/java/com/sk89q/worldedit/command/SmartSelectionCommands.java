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

import com.google.common.base.Splitter;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.shape.ShapeFlags;
import com.sk89q.worldedit.function.mask.AbstractMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskUnion;
import com.sk89q.worldedit.function.mask.SolidBlockMask;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import com.sk89q.worldedit.regions.selector.RegionSelectorType;
import com.sk89q.worldedit.regions.selector.ShapeRegionSelector;
import com.sk89q.worldedit.regions.smart.BlockPosSet;
import com.sk89q.worldedit.regions.smart.ConnectedBlockSearch;
import com.sk89q.worldedit.regions.smart.FootprintOutline;
import com.sk89q.worldedit.regions.smart.NaturalBlocks;
import com.sk89q.worldedit.regions.smart.PolyWalkManager;
import com.sk89q.worldedit.regions.smart.PolyWalkPath;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.LongRangeTrace;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Selections that follow what is in the world: connected blocks (a magic wand), whole builds, and
 * polygons recorded by walking around a build.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class SmartSelectionCommands {

    private static final int TRACE_DISTANCE = 512;

    private final WorldEdit worldEdit;

    public SmartSelectionCommands(WorldEdit worldEdit) {
        this.worldEdit = worldEdit;
    }

    @Command(
        name = "/selconnected",
        aliases = { "/magicwand", "/selc" },
        desc = "Select the blocks matching a mask that are connected to the block under your feet or that you look at",
        descFooter = "Like a paint program's magic wand: e.g. //selc gravel selects a gravel path, "
            + "//selc stone_bricks,stone_brick_stairs a wall with its stairs. -d also follows blocks that only "
            + "touch at edges or corners (zig-zag lines, diagonal stairs). The selection is exactly those blocks, "
            + "so //set, //replace, //copy and //count affect only them. -p makes a //sel poly outline instead."
    )
    @CommandPermissions("worldedit.selection.connected")
    public void selectConnected(Actor actor, World world, LocalSession session,
                                @Arg(desc = "Which blocks to select, e.g. gravel or stone_bricks,stone_brick_stairs")
                                    String maskInput,
                                @Arg(desc = "How far from the start block to spread, in blocks (default: the "
                                    + "server's limit)", def = "")
                                    Integer maxDistance,
                                @Switch(name = 'd', desc = "Also connect blocks that only touch at edges or corners")
                                    boolean diagonal,
                                @Switch(name = 'p', desc = "Select a polygon (//sel poly) around the blocks instead")
                                    boolean poly) throws WorldEditException {
        Mask mask;
        try {
            mask = parseMask(actor, session, world, maskInput);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return;
        }
        int distance = distanceLimit(actor, maxDistance);
        if (distance <= 0) {
            return;
        }
        Start start;
        if (actor instanceof Player player) {
            BlockVector3 feet = player.getBlockOn().toVector().toBlockPoint();
            if (inWorld(world, feet) && mask.test(feet)) {
                start = new Start(feet, "the block under your feet");
            } else {
                Mask stop = new MaskUnion(new SolidBlockMask(world), mask);
                LongRangeTrace.Hit hit = trace(player, world, stop);
                if (hit == null) {
                    actor.printError(TextComponent.of("You are not standing on or looking at " + maskInput + "."));
                    return;
                }
                if (!mask.test(hit.position())) {
                    actor.printError(TextComponent.of("The block you are looking at ("
                        + blockName(world, hit.position()) + ") is not " + maskInput
                        + ", and neither is the block under your feet."));
                    return;
                }
                start = new Start(hit.position(), "the block you are looking at");
            }
        } else {
            BlockVector3 position = session.getPlacementPosition(actor);
            if (!mask.test(position)) {
                actor.printError(TextComponent.of("The block at your placement position " + position + " ("
                    + blockName(world, position) + ") is not " + maskInput + ". Set //pos1 on one first."));
                return;
            }
            start = new Start(position, "your placement position (pos1)");
        }
        ConnectedBlockSearch.Result result = ConnectedBlockSearch.search(start.position(),
            (x, y, z) -> y >= world.getMinY() && y <= world.getMaxY() && mask.test(BlockVector3.at(x, y, z)),
            diagonal, distance, blockLimit());
        select(actor, world, session, result, start, "Connected blocks",
            maskInput + (diagonal ? " (with diagonals)" : ""),
            "of " + maskInput + (diagonal ? ", with diagonals," : ""), distance, poly);
    }

    @Command(
        name = "/selbuild",
        desc = "Select the build you are looking at, with its decorations but without the ground around it",
        descFooter = "Spreads through all non-air blocks touching each other (also at edges and corners), but not into "
            + "natural terrain: grass, dirt, stone, deepslate, sand, gravel, clay, water, lava, plants, flowers, ores, "
            + "snow blocks... (leaves, logs, sandstone, cobblestone and snow layers count as part of the build). "
            + "-g also selects the one layer of ground touching the build. -p makes a //sel poly outline instead."
    )
    @CommandPermissions("worldedit.selection.build")
    public void selectBuild(Actor actor, World world, LocalSession session,
                            @Arg(desc = "How far from the start block to spread, in blocks (default: the "
                                + "server's limit)", def = "")
                                Integer maxDistance,
                            @Switch(name = 'g', desc = "Also select the layer of ground touching the build")
                                boolean ground,
                            @Switch(name = 'p', desc = "Select a polygon (//sel poly) around the build instead")
                                boolean poly) throws WorldEditException {
        int distance = distanceLimit(actor, maxDistance);
        if (distance <= 0) {
            return;
        }
        Start start;
        if (actor instanceof Player player) {
            Mask stop = new AbstractMask() {
                @Override
                public boolean test(BlockVector3 vector) {
                    BlockState state = world.getBlock(vector);
                    return !NaturalBlocks.isAir(state)
                        && (state.getBlockType().getMaterial().isSolid() || !isNatural(state));
                }
            };
            LongRangeTrace.Hit hit = trace(player, world, stop);
            if (hit == null) {
                actor.printError(TextComponent.of("You are not looking at anything. Look at a block of the build."));
                return;
            }
            start = new Start(hit.position(), "the block you are looking at");
        } else {
            start = new Start(session.getPlacementPosition(actor), "your placement position (pos1)");
        }
        BlockState startBlock = world.getBlock(start.position());
        if (NaturalBlocks.isAir(startBlock) || isNatural(startBlock)) {
            actor.printError(TextComponent.of("The start block (" + start.description() + ", "
                + blockName(world, start.position()) + ") is "
                + (NaturalBlocks.isAir(startBlock) ? "air" : "natural terrain")
                + ". " + (actor instanceof Player ? "Look at" : "Set //pos1 on") + " a block of the build."));
            return;
        }
        ConnectedBlockSearch.Result result = ConnectedBlockSearch.search(start.position(),
            (x, y, z) -> {
                if (y < world.getMinY() || y > world.getMaxY()) {
                    return false;
                }
                BlockState state = world.getBlock(BlockVector3.at(x, y, z));
                return !NaturalBlocks.isAir(state) && !isNatural(state);
            },
            true, distance, blockLimit());
        if (ground) {
            BlockPosSet withGround = ConnectedBlockSearch.withTouchingLayer(result.blocks(), (x, y, z) -> {
                if (y < world.getMinY() || y > world.getMaxY()) {
                    return false;
                }
                BlockState state = world.getBlock(BlockVector3.at(x, y, z));
                return isNatural(state) && !NaturalBlocks.isFluid(state.getBlockType().id());
            }, blockLimit());
            result = new ConnectedBlockSearch.Result(withGround, result.reachedDistanceLimit(),
                result.reachedBlockLimit() || withGround.size() >= blockLimit());
        }
        select(actor, world, session, result, start, "Build",
            "from " + start.position() + (ground ? " with its ground layer" : ""),
            "of the build" + (ground ? " with its ground layer," : ""), distance, poly);
    }

    @Command(
        name = "/polywalk",
        desc = "Make a polygon selection by walking around a build",
        descFooter = "//polywalk (or //polywalk start [spacing]) starts recording: every few blocks you walk "
            + "(default 4) a point is added where you stand, and your //sel poly selection grows as you go. "
            + "//polywalk point adds a point right here (for corners), undo removes the last one, "
            + "stop finishes (closing the loop and choosing the height from the ground you walked on to the top of "
            + "the build; -y min,max sets it yourself) and cancel throws it away. "
            + "From the console, //polywalk point x,y,z adds points at explicit positions."
    )
    @CommandPermissions("worldedit.selection.polywalk")
    public void polyWalk(Actor actor, World world, LocalSession session,
                         @Arg(desc = "start, point, undo, stop or cancel", def = "start")
                             String action,
                         @Arg(desc = "For start: the spacing in blocks. For point: an optional x,y,z position",
                             def = "", variable = true)
                             List<String> arguments,
                         @ArgFlag(name = 'y', desc = "For stop: the height range as min,max", def = "")
                             String heightRange) throws WorldEditException {
        List<String> rest;
        try {
            ShapeFlags flags = ShapeFlags.parse(arguments, "", "", "y");
            heightRange = flags.text('y', heightRange == null ? "" : heightRange);
            rest = flags.remaining();
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return;
        }
        PolyWalkManager manager = PolyWalkManager.getInstance();
        String verb = action.toLowerCase(Locale.ROOT);
        if (isNumber(verb)) {
            rest = List.of(verb);
            verb = "start";
        }
        switch (verb) {
            case "start", "begin" -> startWalk(actor, world, manager, rest);
            case "point", "add", "here" -> addPoint(actor, session, manager, rest);
            case "undo", "back" -> undoPoint(actor, manager);
            case "cancel", "discard" -> {
                if (manager.remove(actor) == null) {
                    actor.printError(TextComponent.of("You are not recording a //polywalk."));
                } else {
                    actor.printInfo(TextComponent.of("//polywalk cancelled; nothing new was selected."));
                }
            }
            case "stop", "finish", "done", "end" -> stopWalk(actor, manager, heightRange);
            default -> actor.printError(TextComponent.of("Unknown //polywalk action '" + action
                + "'. Use start, point, undo, stop or cancel."));
        }
    }

    private void startWalk(Actor actor, World world, PolyWalkManager manager, List<String> rest) {
        double spacing = 4;
        if (!rest.isEmpty()) {
            try {
                spacing = Double.parseDouble(rest.getFirst());
            } catch (NumberFormatException _) {
                actor.printError(TextComponent.of("The spacing must be a number of blocks, not '" + rest.getFirst() + "'."));
                return;
            }
            if (spacing < 1 || spacing > 256) {
                actor.printError(TextComponent.of("The spacing must be between 1 and 256 blocks."));
                return;
            }
        }
        boolean restarted = manager.get(actor) != null;
        PolyWalkManager.Walk walk = manager.start(actor, world, spacing);
        if (actor instanceof Player player) {
            actor.printInfo(TextComponent.of((restarted ? "Restarted" : "Started") + " //polywalk: walk around the "
                + "build; a point is added every " + format(spacing) + " blocks. Use //polywalk point at corners, "
                + "//polywalk stop when you are back at the start."));
            walk.getPath().offer(player.getLocation().toVector());
            manager.pointAdded(actor, walk);
        } else {
            actor.printInfo(TextComponent.of((restarted ? "Restarted" : "Started") + " //polywalk. Nothing walks "
                + "here, so add points with //polywalk point x,y,z (or at your placement position with "
                + "//polywalk point), then //polywalk stop."));
        }
    }

    private void addPoint(Actor actor, LocalSession session, PolyWalkManager manager, List<String> rest) {
        PolyWalkManager.Walk walk = manager.get(actor);
        if (walk == null) {
            actor.printError(TextComponent.of("You are not recording a //polywalk; start one with //polywalk."));
            return;
        }
        Vector3 position;
        if (!rest.isEmpty()) {
            List<String> parts = Splitter.on(Pattern.compile("[,\\s]+")).omitEmptyStrings()
                .splitToList(String.join(",", rest));
            if (parts.size() != 3) {
                actor.printError(TextComponent.of("Give the point as x,y,z."));
                return;
            }
            try {
                position = Vector3.at(Double.parseDouble(parts.get(0)), Double.parseDouble(parts.get(1)),
                    Double.parseDouble(parts.get(2)));
            } catch (NumberFormatException _) {
                actor.printError(TextComponent.of("Give the point as x,y,z numbers."));
                return;
            }
        } else if (actor instanceof Player player) {
            position = player.getLocation().toVector();
        } else {
            try {
                position = session.getPlacementPosition(actor).toVector3();
            } catch (WorldEditException _) {
                actor.printError(TextComponent.of("Set //pos1 or give the point as x,y,z."));
                return;
            }
        }
        if (!walk.getPath().addManualPoint(position)) {
            actor.printInfo(TextComponent.of("There is already a point here; it will be kept as a corner."));
            return;
        }
        manager.pointAdded(actor, walk);
    }

    private void undoPoint(Actor actor, PolyWalkManager manager) {
        PolyWalkManager.Walk walk = manager.get(actor);
        if (walk == null) {
            actor.printError(TextComponent.of("You are not recording a //polywalk."));
            return;
        }
        PolyWalkPath.Point removed = walk.getPath().undo();
        if (removed == null) {
            actor.printError(TextComponent.of("There are no points to remove."));
            return;
        }
        actor.printInfo(TextComponent.of("Removed the point at " + removed.position().x() + ", "
            + removed.position().z() + "; " + walk.getPath().size() + " left. Walk on from there."));
        if (walk.getPath().size() > 0) {
            // the next automatic point is measured from the new last point
            manager.showSelection(actor, walk);
        }
    }

    private void stopWalk(Actor actor, PolyWalkManager manager, String heightRange) {
        PolyWalkManager.Walk walk = manager.get(actor);
        if (walk == null) {
            actor.printError(TextComponent.of("You are not recording a //polywalk."));
            return;
        }
        int[] yRange = null;
        if (!heightRange.isEmpty()) {
            List<String> parts = Splitter.on(',').trimResults().splitToList(heightRange);
            try {
                if (parts.size() != 2) {
                    throw new NumberFormatException();
                }
                yRange = new int[] {Integer.parseInt(parts.get(0)), Integer.parseInt(parts.get(1))};
            } catch (NumberFormatException _) {
                actor.printError(TextComponent.of("-y needs the height range as min,max, e.g. -y 60,90."));
                return;
            }
        }
        long distinct = walk.getPath().getColumns().stream().distinct().count();
        if (distinct < 3) {
            actor.printError(TextComponent.of("A polygon needs at least 3 points; you have " + distinct
                + ". Keep walking (or add points with //polywalk point), or //polywalk cancel."));
            return;
        }
        PolyWalkManager.Finished finished = manager.finish(actor, walk, yRange);
        BlockVector3 min = finished.selector().getIncompleteRegion().getMinimumPoint();
        BlockVector3 max = finished.selector().getIncompleteRegion().getMaximumPoint();
        actor.printInfo(TextComponent.of("Selected a polygon with " + finished.outline().size() + " points (from "
            + finished.recordedPoints() + " recorded), y " + finished.minY() + " to " + finished.maxY() + ", size "
            + size(min, max) + ", " + finished.selector().getVolume() + " blocks."));
    }

    private static boolean isNumber(String text) {
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    private record Start(BlockVector3 position, String description) {
    }

    private void select(Actor actor, World world, LocalSession session, ConnectedBlockSearch.Result result,
                        Start start, String kind, String label, String summary, int distance, boolean poly) {
        BlockPosSet blocks = result.blocks();
        BlockVector3 min = blocks.getMinimum();
        BlockVector3 max = blocks.getMaximum();
        RegionSelector selector;
        String extra = "";
        if (poly) {
            FootprintOutline footprint = FootprintOutline.of(blocks);
            int limit = PolyWalkManager.pointLimit(actor);
            List<BlockVector2> outline = FootprintOutline.atLeastThreePoints(
                FootprintOutline.simplify(footprint.trace(), limit));
            selector = new Polygonal2DRegionSelector(world, outline, min.y(), max.y());
            long outside = footprint.countOutside(outline);
            extra = String.format(Locale.ROOT, " Polygon with %,d points, %,d blocks in all.", outline.size(),
                selector.getVolume())
                + (outside > 0 ? String.format(Locale.ROOT, " %,d %s at the edge did not fit inside the outline "
                + "with only %,d points.", outside, outside == 1 ? "column" : "columns", limit) : "");
        } else {
            if (!blocks.fitsVoxelGrid()) {
                actor.printError(TextComponent.of("Those blocks are spread over too large an area to select "
                    + "exactly (" + size(min, max) + "). Use -p for a polygon, or a smaller distance."));
                return;
            }
            // clicking afterwards starts a fresh selection of the kind the player was using
            RegionSelectorType previous = ShapeRegionSelector.typeOf(session.getRegionSelector(world));
            selector = new ShapeRegionSelector(world, blocks.toVoxelGrid(start.position()), start.position(),
                BlockVector3.ZERO, kind, label).switchOnClick(previous);
        }
        session.setRegionSelector(world, selector);
        session.dispatchCUISelection(actor);
        actor.printInfo(TextComponent.of(String.format(Locale.ROOT, "Selected %,d %s %s starting at %s, "
            + "size %s.%s", blocks.size(), blocks.size() == 1 ? "block" : "blocks", summary,
            start.description(), size(min, max), extra)));
        if (result.reachedBlockLimit()) {
            actor.printError(TextComponent.of(String.format(Locale.ROOT, "Stopped at the limit of %,d blocks; "
                + "the selection is incomplete.", blockLimit())));
        }
        if (result.reachedDistanceLimit()) {
            actor.printError(TextComponent.of(String.format(Locale.ROOT, "More matching blocks continue beyond %,d",
                distance)
                + " blocks from the start; they were not selected."));
        }
    }

    /**
     * The distance to search, or 0 (after printing why) if the requested distance is not allowed.
     */
    private int distanceLimit(Actor actor, @Nullable Integer requested) {
        LocalConfiguration config = worldEdit.getConfiguration();
        int cap = Math.max(1, Math.min(ConnectedBlockSearch.MAX_DISTANCE, config.maxConnectedSelectionDistance));
        if (requested == null) {
            return cap;
        }
        if (requested <= 0) {
            actor.printError(TextComponent.of("The distance must be at least 1 block (leave it out to use the "
                + "server's limit of " + cap + ")."));
            return 0;
        }
        if (requested > cap) {
            actor.printInfo(TextComponent.of("The distance is limited to " + cap + " blocks on this server."));
            return cap;
        }
        return requested;
    }

    private long blockLimit() {
        return Math.max(1, worldEdit.getConfiguration().maxConnectedSelectionBlocks);
    }

    private static boolean isNatural(BlockState state) {
        return NaturalBlocks.isNatural(state.getBlockType().id());
    }

    private static boolean inWorld(World world, BlockVector3 position) {
        return position.y() >= world.getMinY() && position.y() <= world.getMaxY();
    }

    @Nullable
    private static LongRangeTrace.Hit trace(Player player, World world, Mask stop) {
        Location location = player.getLocation();
        return new LongRangeTrace(world, stop).trace(location.toVector().add(0, LongRangeTrace.EYE_HEIGHT, 0),
            location.getDirection(), TRACE_DISTANCE);
    }

    private static String blockName(World world, BlockVector3 position) {
        String id = world.getBlock(position).getBlockType().id();
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static String size(BlockVector3 min, BlockVector3 max) {
        BlockVector3 size = max.subtract(min).add(1, 1, 1);
        return size.x() + "x" + size.y() + "x" + size.z();
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static Mask parseMask(Actor actor, LocalSession session, World world, String input) {
        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(world);
        context.setExtent(world);
        context.setSession(session);
        context.setRestricted(true);
        try {
            return WorldEdit.getInstance().getMaskFactory().parseFromInput(input, context);
        } catch (InputParseException e) {
            throw new IllegalArgumentException("Bad mask '" + input + "': " + e.getMessage(), e);
        }
    }
}
