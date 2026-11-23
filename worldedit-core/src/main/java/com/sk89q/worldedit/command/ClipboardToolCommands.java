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
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.clipboard.ClipboardScaler;
import com.sk89q.worldedit.function.clipboard.PathSampler;
import com.sk89q.worldedit.function.clipboard.RadialLayout;
import com.sk89q.worldedit.function.clipboard.ScaleResampler;
import com.sk89q.worldedit.function.clipboard.TransformedClipboardPlacer;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.NamedClipboardStore;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.ArrayList;
import java.util.List;

import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;

/**
 * Clipboard tools: scale by any factor, paste in a ring, and repeat along a path.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class ClipboardToolCommands {

    private static final int MAX_PATH_COPIES = 10_000;

    private static ClipboardHolder holder(LocalSession session, Actor actor, String clipName) throws WorldEditException {
        return clipName != null && !clipName.isEmpty()
            ? NamedClipboardStore.getDefault().getNamedClipboard(session, actor, clipName)
            : session.getClipboard();
    }

    @Command(
        name = "/scale",
        aliases = { "/resize" },
        desc = "Scales the clipboard by any factor, e.g. 2.5, 0.5 or 1,2,1 (x,y,z)",
        descFooter = "Scales around the clipboard origin, so //paste lands where it did before, just bigger or "
            + "smaller. Enlarging fills every block (no holes). When shrinking, a block becomes solid when at "
            + "least a quarter of what it covers is solid (change with -a, e.g. -a 0.5), using the most common "
            + "block there, so thin walls survive. Block states (stairs, logs) are kept; chest contents and "
            + "sign text are kept on one copy only. Any //rotate or //flip is applied first."
    )
    @CommandPermissions("worldedit.clipboard.scale")
    public void scale(Actor actor, LocalSession session,
                      @Arg(desc = "Factor, or x,y,z factors")
                          String factor,
                      @ArgFlag(name = 'a', desc = "When shrinking, how much of a block must be solid (0-1)", def = "0.25")
                          double solidFraction,
                      @ArgFlag(name = 'k', desc = "Scale a named clipboard instead", def = "")
                          String clipName) throws WorldEditException {
        ClipboardHolder holder = holder(session, actor, clipName);
        Clipboard scaled;
        try {
            double[] factors = ScaleResampler.parseFactor(factor);
            scaled = ClipboardScaler.scale(TransformedClipboardPlacer.flatten(holder), factors,
                solidFraction, ScaleResampler.MAX_VOLUME);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return;
        }
        ClipboardHolder result = new ClipboardHolder(scaled);
        if (clipName != null && !clipName.isEmpty()) {
            session.setNamedClipboard(clipName, result);
        }
        session.setClipboard(result);
        BlockVector3 size = scaled.getDimensions();
        actor.printInfo(TextComponent.of("Clipboard scaled by " + factor + " to "
            + size.x() + "x" + size.y() + "x" + size.z() + " blocks"
            + (clipName != null && !clipName.isEmpty() ? " (named clipboard '" + clipName + "' updated)" : "")
            + ". Use //paste to place it."));
    }

    @Command(
        name = "/radial",
        aliases = { "/ring" },
        desc = "Pastes the clipboard several times evenly around you, each copy turned to face outward",
        descFooter = "Copy number k is the normal //paste turned k*360/count degrees clockwise around your "
            + "position, like spokes of a wheel; the first copy is exactly where //paste would put it. "
            + "Without a radius each copy keeps the distance it has from you when pasting; with a radius "
            + "the centre of each copy is moved to that many blocks from you. Copies at angles that are "
            + "not multiples of 90 degrees turn stairs and other directional blocks to the nearest "
            + "direction they support. One //undo removes all copies."
    )
    @CommandPermissions("worldedit.clipboard.radial")
    @Logging(PLACEMENT)
    public void radial(Actor actor, LocalSession session, EditSession editSession,
                       @Arg(desc = "Number of copies")
                           int count,
                       @Arg(desc = "Distance from you to the centre of each copy (default: as pasted)", def = "")
                           Double radius,
                       @Switch(name = 'a', desc = "Skip air blocks")
                           boolean ignoreAir,
                       @ArgFlag(name = 'k', desc = "Use a named clipboard", def = "")
                           String clipName) throws WorldEditException {
        if (radius != null) {
            WorldEdit.getInstance().checkMaxRadius(radius);
        }
        Clipboard clipboard = TransformedClipboardPlacer.flatten(holder(session, actor, clipName));
        BlockVector3 origin = clipboard.getOrigin();
        Vector3 center = clipboard.getRegion().getMinimumPoint().add(clipboard.getRegion().getMaximumPoint())
            .toVector3().divide(2).subtract(origin.toVector3());
        RadialLayout layout;
        try {
            layout = new RadialLayout(count, center, radius, yawOf(actor));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return;
        }
        BlockVector3 to = session.getPlacementPosition(actor);
        int changed = 0;
        for (int i = 0; i < layout.getCount(); i++) {
            changed += new TransformedClipboardPlacer(clipboard, layout.transform(i), ignoreAir).place(editSession, to);
        }
        actor.printInfo(TextComponent.of("Pasted " + count + " copies around " + to
            + " (" + changed + " blocks)."));
    }

    @Command(
        name = "/stackpath",
        aliases = { "/pathstack" },
        desc = "Repeats the clipboard along a smooth path through the selection points",
        descFooter = "The path goes through the points of a //sel poly or //sel convex selection in the order "
            + "you clicked them (a cuboid goes from pos1 to pos2), smoothed like //curve. A copy is pasted "
            + "every <spacing> blocks along it, with the clipboard origin on the path, like //paste there. "
            + "-r turns each copy to follow the path (the first copy is not turned; later ones turn as much "
            + "as the path does). -g drops (or lifts) each copy so it sits on the ground under it. "
            + "One //undo removes all copies."
    )
    @CommandPermissions("worldedit.clipboard.stackpath")
    @Logging(PLACEMENT)
    public void stackPath(Actor actor, LocalSession session, EditSession editSession,
                          @Selection Region region,
                          @Arg(desc = "Blocks of path length between copies")
                              double spacing,
                          @Switch(name = 'a', desc = "Skip air blocks")
                              boolean ignoreAir,
                          @Switch(name = 'r', desc = "Turn each copy to follow the path")
                              boolean followPath,
                          @Switch(name = 'g', desc = "Put each copy on the ground beneath it")
                              boolean toGround,
                          @ArgFlag(name = 'k', desc = "Use a named clipboard", def = "")
                              String clipName) throws WorldEditException {
        List<Vector3> nodes = pathNodes(region);
        if (nodes == null) {
            actor.printError(TextComponent.of("//stackpath needs a cuboid, //sel poly or //sel convex selection."));
            return;
        }
        List<PathSampler.PathPoint> points;
        try {
            points = PathSampler.sample(nodes, spacing, MAX_PATH_COPIES);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return;
        }
        Clipboard clipboard = TransformedClipboardPlacer.flatten(holder(session, actor, clipName));
        int lowest = TransformedClipboardPlacer.lowestSolidOffset(clipboard);
        double firstHeading = points.getFirst().heading();

        // work out every placement first, so ground checks see the world as it was
        List<TransformedClipboardPlacer> placers = new ArrayList<>();
        List<BlockVector3> targets = new ArrayList<>();
        for (PathSampler.PathPoint point : points) {
            AffineTransform transform = followPath
                ? RadialLayout.clockwise(point.heading() - firstHeading)
                : new AffineTransform();
            TransformedClipboardPlacer placer = new TransformedClipboardPlacer(clipboard, transform, ignoreAir);
            Vector3 p = point.position();
            BlockVector3 to = BlockVector3.at(Math.floor(p.x() + 0.5), Math.floor(p.y() + 0.5), Math.floor(p.z() + 0.5));
            if (toGround) {
                to = onGround(editSession, placer, to, lowest);
            }
            placers.add(placer);
            targets.add(to);
        }
        int changed = 0;
        for (int i = 0; i < placers.size(); i++) {
            changed += placers.get(i).place(editSession, targets.get(i));
        }
        actor.printInfo(TextComponent.of("Pasted " + placers.size() + " copies along the path ("
            + changed + " blocks)."));
    }

    private static double yawOf(Actor actor) {
        if (actor instanceof Player player) {
            return player.getLocation().getYaw();
        }
        return 0;
    }

    private static List<Vector3> pathNodes(Region region) {
        List<Vector3> nodes = new ArrayList<>();
        if (region instanceof ConvexPolyhedralRegion convex) {
            for (BlockVector3 vertex : convex.getVertices()) {
                nodes.add(vertex.toVector3());
            }
        } else if (region instanceof Polygonal2DRegion poly) {
            int y = poly.getMinimumY();
            for (BlockVector2 point : poly.getPoints()) {
                nodes.add(Vector3.at(point.x(), y, point.z()));
            }
        } else if (region instanceof CuboidRegion cuboid) {
            nodes.add(cuboid.getPos1().toVector3());
            nodes.add(cuboid.getPos2().toVector3());
        } else {
            return null;
        }
        return nodes;
    }

    private static boolean isGround(BlockState state) {
        BlockMaterial material = state.getBlockType().getMaterial();
        return !material.isAir() && (material.isSolid() || material.isLiquid());
    }

    /**
     * Move a copy up or down so its lowest solid layer rests on the highest ground under its
     * footprint. The search starts at the top of the copy, so a copy that is partly buried is lifted.
     */
    private static BlockVector3 onGround(EditSession editSession, TransformedClipboardPlacer placer,
                                         BlockVector3 to, int lowestOffset) {
        BlockVector3 min = to.add(placer.getMinimum());
        BlockVector3 max = to.add(placer.getMaximum());
        int floor = editSession.getWorld().getMinY();
        int top = Math.min(max.y(), editSession.getWorld().getMaxY());
        int ground = Integer.MIN_VALUE;
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                for (int y = top; y >= floor && y > ground; y--) {
                    if (isGround(editSession.getBlock(BlockVector3.at(x, y, z)))) {
                        ground = y;
                        break;
                    }
                }
            }
        }
        if (ground == Integer.MIN_VALUE) {
            return to;
        }
        return to.withY(ground + 1 - lowestOffset);
    }
}
