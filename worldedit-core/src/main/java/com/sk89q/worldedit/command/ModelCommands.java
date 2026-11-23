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
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.generator.model.CrossSection;
import com.sk89q.worldedit.function.generator.model.DrawingModeller;
import com.sk89q.worldedit.function.generator.model.FlatDrawing;
import com.sk89q.worldedit.function.generator.model.SweepPath;
import com.sk89q.worldedit.function.generator.model.SweepProfile;
import com.sk89q.worldedit.function.generator.model.Sweeper;
import com.sk89q.worldedit.function.generator.shape.ShapeFlags;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BaseBlock;
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
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;
import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;

/**
 * Model 3D shapes from flat drawings made of blocks: spin them, pull them out, or drag a cross-section
 * along a path. No formulas.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class ModelCommands {

    /**
     * The largest selection read as a drawing.
     */
    private static final long MAX_DRAWING_VOLUME = 1024 * 1024;

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public ModelCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
        name = "/lathe",
        aliases = { "/spin" },
        desc = "Spins a flat drawing in the selection around a vertical axis: vases, domes, towers, fountains",
        descFooter = "Draw half (or all) of the outline of a vase, dome or tower as an upright, flat picture of blocks "
            + "(1 or 2 thick, any materials), select it, stand where the middle of the finished model should be, "
            + "and run //lathe. The axis is the vertical line through where you stand, in the plane of the drawing. "
            + "-c puts the axis on the edge of the drawing nearest to you instead, so you can stand anywhere on "
            + "that side. Each block keeps its material, so a gold rim stays gold. "
            + "Give an angle (e.g. 180) for part of a turn; it turns clockwise seen from above. "
            + "The drawing stays unless you add -r."
    )
    @CommandPermissions("worldedit.generation.lathe")
    @Logging(REGION)
    public int lathe(Actor actor, LocalSession session, EditSession editSession,
                     @Selection Region region,
                     @Arg(desc = "How far to spin it, in degrees", def = "360")
                         double degrees,
                     @Switch(name = 'c', desc = "Put the axis on the edge of the drawing nearest to you")
                         boolean nearestEdge,
                     @Switch(name = 'r', desc = "Remove the original drawing")
                         boolean removeDrawing) throws WorldEditException {
        Map<BlockVector3, BaseBlock> blocks;
        FlatDrawing<BaseBlock> drawing;
        Map<BlockVector3, BaseBlock> model;
        int axisU;
        try {
            blocks = readDrawing(editSession, region);
            drawing = FlatDrawing.of(blocks, null, false);
            BlockVector3 position = session.getPlacementPosition(actor);
            int standing = drawing.getUAxis().of(position);
            axisU = standing;
            if (nearestEdge) {
                axisU = Math.abs(standing - drawing.getMinU()) <= Math.abs(standing - drawing.getMaxU())
                    ? drawing.getMinU() : drawing.getMaxU();
            }
            int radius = Math.max(Math.abs(drawing.getMaxU() - axisU), Math.abs(drawing.getMinU() - axisU));
            worldEdit.checkMaxRadius(radius);
            model = DrawingModeller.revolve(drawing, axisU, degrees);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        int changed = 0;
        if (removeDrawing) {
            changed += clear(editSession, blocks);
        }
        changed += place(editSession, model);
        String axis = drawing.getUAxis().label() + "=" + axisU + ", " + drawing.getThinAxis().label() + "="
            + drawing.getMinW();
        actor.printInfo(TextComponent.of("Spun the drawing " + formatDegrees(degrees) + " around the vertical line at "
            + axis + ": " + changed + " blocks changed."));
        return changed;
    }

    @Command(
        name = "/extrude",
        aliases = { "/pull" },
        desc = "Pulls a flat drawing in the selection straight out, optionally twisting and tapering it",
        descFooter = "Draw a flat picture of blocks (1 or 2 thick, any materials), select it and run "
            + "//extrude <length>. It is pulled out along the axis it is thin in: away from you for an upright "
            + "drawing, and upwards for a drawing on the ground. -d north/south/east/west/up/down (or me) picks the "
            + "direction instead. Twist is how many degrees the far end is turned; taper is how big the far end "
            + "is (0.5 = half size, 2 = double). Example: //extrude 20 90 0.3 makes a twisted spire."
    )
    @CommandPermissions("worldedit.generation.extrude")
    @Logging(REGION)
    public int extrude(Actor actor, LocalSession session, EditSession editSession,
                       @Selection Region region,
                       @Arg(desc = "How many blocks to pull it out")
                           int length,
                       @Arg(desc = "Degrees the far end is turned", def = "0")
                           double twist,
                       @Arg(desc = "How big the far end is (1 = same size)", def = "1")
                           double taper,
                       @ArgFlag(name = 'd', desc = "The direction: north, south, east, west, up, down or me", def = "")
                           String directionName) throws WorldEditException {
        Map<BlockVector3, BaseBlock> model;
        FlatDrawing<BaseBlock> drawing;
        String towards;
        try {
            if (length < 1) {
                throw new IllegalArgumentException("The length must be at least 1.");
            }
            worldEdit.checkMaxRadius(length);
            Map<BlockVector3, BaseBlock> blocks = readDrawing(editSession, region);
            int sign;
            if (directionName != null && !directionName.isBlank()) {
                BlockVector3 direction = parseDirection(actor, directionName);
                FlatDrawing.Axis axis = direction.x() != 0 ? FlatDrawing.Axis.X
                    : direction.y() != 0 ? FlatDrawing.Axis.Y : FlatDrawing.Axis.Z;
                sign = axis.of(direction);
                drawing = FlatDrawing.of(blocks, axis, true);
            } else {
                drawing = FlatDrawing.of(blocks, null, true);
                sign = defaultSign(actor, session, drawing);
            }
            towards = directionWord(drawing.getThinAxis(), sign);
            model = DrawingModeller.extrude(drawing, sign, length, twist, taper);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        int changed = place(editSession, model);
        actor.printInfo(TextComponent.of("Pulled the drawing " + length + " blocks " + towards
            + (twist != 0 ? ", twisting " + formatDegrees(twist) : "")
            + (taper != 1 ? ", tapering to " + taper + "x" : "") + ": " + changed + " blocks changed."));
        return changed;
    }

    @Command(
        name = "/sweep",
        desc = "Drags a cross-section (circle, square, arch, ring, rail or your own drawing) along a smooth path",
        descFooter = "The path runs through the selection's points: pos1 to pos2 for a cuboid, or every point in order "
            + "for //sel poly or //sel convex, smoothed into a curve. Profiles: circle (tube), square (beam; height=), "
            + "arch (tunnel or aqueduct standing on the path, open at the bottom; height=, thickness=), ring (pipe; "
            + "thickness=), rail (two rails on sleepers; spacing=). The size is the width in blocks. "
            + "-h makes it hollow with open ends. -k (or the profile name 'clipboard') uses the flat drawing in your "
            + "clipboard instead, keeping its blocks; the path runs through the spot you stood on when you copied it. "
            + "Examples: //sweep arch stone_bricks 9 height=8, //sweep ring glass 5, //sweep clipboard"
    )
    @CommandPermissions("worldedit.generation.sweep")
    @Logging(PLACEMENT)
    public int sweep(Actor actor, LocalSession session, EditSession editSession,
                     @Selection Region region,
                     @Arg(desc = "The profile: circle, square, arch, ring, rail or clipboard")
                         String profileName,
                     @Arg(desc = "The pattern of blocks to build with", def = "stone")
                         Pattern pattern,
                     @Arg(desc = "The width in blocks", def = "5")
                         double size,
                     @Arg(desc = "Profile settings: numbers in order, or name=value", def = "", variable = true)
                         List<String> settings,
                     @Switch(name = 'k', desc = "Use the flat drawing in your clipboard as the profile")
                         boolean useClipboard,
                     @Switch(name = 'h', desc = "Make it hollow (open at the ends)")
                         boolean hollow) throws WorldEditException {
        int changed;
        String what;
        try {
            ShapeFlags flags = ShapeFlags.parse(settings, "kh", "");
            useClipboard |= flags.has('k');
            hollow |= flags.has('h');
            useClipboard |= profileName.equalsIgnoreCase("clipboard") || profileName.equalsIgnoreCase("drawing");
            SweepPath path = SweepPath.through(waypoints(region));
            if (useClipboard) {
                Clipboard clipboard = session.getClipboard().getClipboard();
                FlatDrawing<BaseBlock> drawing = FlatDrawing.of(readDrawing(clipboard, clipboard.getRegion()), null, false);
                BlockVector3 origin = clipboard.getOrigin();
                CrossSection<BaseBlock> section = CrossSection.of(drawing, drawing.getUAxis().of(origin),
                    drawing.getVAxis().of(origin));
                worldEdit.checkMaxRadius(section.reach());
                changed = place(editSession, Sweeper.sweep(path, section, hollow));
                what = "your drawing";
            } else {
                Optional<SweepProfile> found = SweepProfile.find(profileName);
                if (found.isEmpty()) {
                    throw new IllegalArgumentException("Unknown profile '" + profileName + "'. Use one of: "
                        + SweepProfile.names() + ", or clipboard.");
                }
                SweepProfile profile = found.get();
                ShapeParameters parameters = ShapeParameters.parse(profile.getParameters(), flags.remaining());
                CrossSection<Boolean> section = profile.create(size, parameters);
                worldEdit.checkMaxRadius(section.reach());
                Map<BlockVector3, Boolean> blocks = Sweeper.sweep(path, section, hollow && !profile.isOpenAlready());
                changed = 0;
                for (BlockVector3 block : blocks.keySet()) {
                    if (editSession.setBlock(block, pattern)) {
                        changed++;
                    }
                }
                what = profile.label();
            }
            actor.printInfo(TextComponent.of("Swept " + what + " along a " + Math.round(path.length())
                + "-block path: " + changed + " blocks changed."));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        return changed;
    }

    /**
     * The points of the path through a selection, in order.
     */
    private static List<BlockVector3> waypoints(Region region) {
        List<BlockVector3> waypoints = new ArrayList<>();
        if (region instanceof CuboidRegion cuboid) {
            waypoints.add(cuboid.getPos1());
            waypoints.add(cuboid.getPos2());
        } else if (region instanceof Polygonal2DRegion polygon) {
            int y = polygon.getMinimumY();
            for (BlockVector2 point : polygon.getPoints()) {
                waypoints.add(point.toBlockVector3(y));
            }
        } else if (region instanceof ConvexPolyhedralRegion convex) {
            waypoints.addAll(convex.getVertices());
        } else {
            throw new IllegalArgumentException("//sweep needs a cuboid (pos1 to pos2), polygon or convex selection "
                + "for its path.");
        }
        return waypoints;
    }

    private static Map<BlockVector3, BaseBlock> readDrawing(Extent extent, Region region) {
        if (region.getVolume() > MAX_DRAWING_VOLUME) {
            throw new IllegalArgumentException("The selection is too big to be a drawing: select just the drawing.");
        }
        Map<BlockVector3, BaseBlock> blocks = new HashMap<>();
        for (BlockVector3 position : region) {
            BaseBlock block = extent.getFullBlock(position);
            if (!block.getBlockType().getMaterial().isAir()) {
                blocks.put(position, block);
            }
        }
        return blocks;
    }

    private static int place(EditSession editSession, Map<BlockVector3, BaseBlock> blocks) throws WorldEditException {
        int changed = 0;
        for (Map.Entry<BlockVector3, BaseBlock> entry : blocks.entrySet()) {
            if (editSession.setBlock(entry.getKey(), entry.getValue())) {
                changed++;
            }
        }
        return changed;
    }

    private static int clear(EditSession editSession, Map<BlockVector3, BaseBlock> blocks) throws WorldEditException {
        int changed = 0;
        for (BlockVector3 position : blocks.keySet()) {
            if (editSession.setBlock(position, BlockTypes.AIR.getDefaultState())) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Away from the player for an upright drawing, up for a drawing on the ground.
     */
    private static int defaultSign(Actor actor, LocalSession session, FlatDrawing<?> drawing) {
        FlatDrawing.Axis axis = drawing.getThinAxis();
        if (axis == FlatDrawing.Axis.Y) {
            return 1;
        }
        int standing;
        try {
            standing = axis.of(session.getPlacementPosition(actor));
        } catch (WorldEditException _) {
            return 1;
        }
        if (standing < drawing.getMinW()) {
            return 1;
        }
        if (standing > drawing.getMaxW()) {
            return -1;
        }
        if (actor instanceof Player player) {
            // standing in the drawing: go the way the player looks
            BlockVector3 facing = player.getCardinalDirection().toBlockVector();
            int along = axis.of(facing);
            if (along != 0) {
                return along;
            }
        }
        return 1;
    }

    private static BlockVector3 parseDirection(Actor actor, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        Direction direction = switch (lower) {
            case "north", "n" -> Direction.NORTH;
            case "south", "s" -> Direction.SOUTH;
            case "east", "e" -> Direction.EAST;
            case "west", "w" -> Direction.WEST;
            case "up", "u" -> Direction.UP;
            case "down", "d" -> Direction.DOWN;
            case "me", "forward", "f" -> {
                if (actor instanceof Player player && player.getCardinalDirection() != null) {
                    BlockVector3 facing = player.getCardinalDirection().toBlockVector();
                    int axes = (facing.x() != 0 ? 1 : 0) + (facing.y() != 0 ? 1 : 0) + (facing.z() != 0 ? 1 : 0);
                    if (axes == 1) {
                        yield player.getCardinalDirection();
                    }
                }
                throw new IllegalArgumentException("Could not tell which way you are facing; use north, south, "
                    + "east, west, up or down.");
            }
            default -> throw new IllegalArgumentException("Unknown direction '" + name
                + "'. Use north, south, east, west, up, down or me.");
        };
        return direction.toBlockVector();
    }

    private static String directionWord(FlatDrawing.Axis axis, int sign) {
        return switch (axis) {
            case X -> sign > 0 ? "east" : "west";
            case Y -> sign > 0 ? "up" : "down";
            case Z -> sign > 0 ? "south" : "north";
        };
    }

    private static String formatDegrees(double degrees) {
        return (degrees == Math.rint(degrees) ? Long.toString((long) degrees) : Double.toString(degrees)) + " degrees";
    }
}
