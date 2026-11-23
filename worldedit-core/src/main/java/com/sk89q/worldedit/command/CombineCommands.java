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
import com.sk89q.worldedit.function.generator.model.CompoundShape;
import com.sk89q.worldedit.function.generator.model.CompoundShape.Operation;
import com.sk89q.worldedit.function.generator.shape.NamedShape;
import com.sk89q.worldedit.function.generator.shape.ShapeFlags;
import com.sk89q.worldedit.function.generator.shape.ShapeGenerator;
import com.sk89q.worldedit.function.generator.shape.ShapeOrientation;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.generator.shape.ShapeRasterizer;
import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Radii;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.selector.ShapeRegionSelector;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.List;
import java.util.UUID;

import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;

/**
 * {@code //combine}: build compound shapes from named shapes, step by step. The compound is kept per player in
 * memory only, so it is lost when the server restarts.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class CombineCommands {

    private static final String PERMISSION = "worldedit.generation.combine";

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public CombineCommands(WorldEdit worldEdit) {
        this.worldEdit = worldEdit;
    }

    private static CompoundShape compound(Actor actor) {
        UUID id = actor.getUniqueId();
        return CompoundShape.of(id);
    }

    @Command(
        name = "add",
        aliases = { "union" },
        desc = "Add a shape to your compound shape",
        descFooter = "Same shapes, sizes and settings as //shape. -o x,y,z moves it that many blocks from the "
            + "middle of the first shape (x east, y up, z south). -r turns it, -t tilts it (degrees). "
            + "The first shape is placed where you stand now. Example: //combine add sphere 6, then "
            + "//combine add cylinder 3,8,3 -o 0,8,0"
    )
    @CommandPermissions(PERMISSION)
    public void add(Actor actor, LocalSession session,
                    @Arg(desc = "The shape (see //shapes)")
                        NamedShape shape,
                    @Arg(desc = "The radius, or x,y,z radii")
                    @Radii(3)
                        List<Double> size,
                    @Arg(desc = "Shape settings: numbers in order, or name=value", def = "", variable = true)
                        List<String> settings,
                    @ArgFlag(name = 'o', desc = "Offset from the first shape's middle, as x,y,z", def = "")
                        String offset,
                    @ArgFlag(name = 'r', desc = "Turn the shape clockwise by this many degrees", def = "0")
                        double turn,
                    @ArgFlag(name = 't', desc = "Tilt the shape by this many degrees", def = "0")
                        double tilt) throws WorldEditException {
        addStep(actor, session, Operation.ADD, shape, size, settings, offset, turn, tilt);
    }

    @Command(
        name = "cut",
        aliases = { "subtract" },
        desc = "Carve a shape out of your compound shape",
        descFooter = "Same as //combine add, but removes the shape's blocks. Example: //combine cut sphere 5 -o 0,2,0"
    )
    @CommandPermissions(PERMISSION)
    public void cut(Actor actor, LocalSession session,
                    @Arg(desc = "The shape (see //shapes)")
                        NamedShape shape,
                    @Arg(desc = "The radius, or x,y,z radii")
                    @Radii(3)
                        List<Double> size,
                    @Arg(desc = "Shape settings: numbers in order, or name=value", def = "", variable = true)
                        List<String> settings,
                    @ArgFlag(name = 'o', desc = "Offset from the first shape's middle, as x,y,z", def = "")
                        String offset,
                    @ArgFlag(name = 'r', desc = "Turn the shape clockwise by this many degrees", def = "0")
                        double turn,
                    @ArgFlag(name = 't', desc = "Tilt the shape by this many degrees", def = "0")
                        double tilt) throws WorldEditException {
        addStep(actor, session, Operation.CUT, shape, size, settings, offset, turn, tilt);
    }

    @Command(
        name = "keep",
        aliases = { "intersect" },
        desc = "Keep only the part of your compound shape that is inside a shape",
        descFooter = "Same as //combine add, but keeps only the overlap. Example: //combine keep cube 5"
    )
    @CommandPermissions(PERMISSION)
    public void keep(Actor actor, LocalSession session,
                     @Arg(desc = "The shape (see //shapes)")
                         NamedShape shape,
                     @Arg(desc = "The radius, or x,y,z radii")
                     @Radii(3)
                         List<Double> size,
                     @Arg(desc = "Shape settings: numbers in order, or name=value", def = "", variable = true)
                         List<String> settings,
                     @ArgFlag(name = 'o', desc = "Offset from the first shape's middle, as x,y,z", def = "")
                         String offset,
                     @ArgFlag(name = 'r', desc = "Turn the shape clockwise by this many degrees", def = "0")
                         double turn,
                     @ArgFlag(name = 't', desc = "Tilt the shape by this many degrees", def = "0")
                         double tilt) throws WorldEditException {
        addStep(actor, session, Operation.KEEP, shape, size, settings, offset, turn, tilt);
    }

    private void addStep(Actor actor, LocalSession session, Operation operation, NamedShape shape, List<Double> size,
                         List<String> settings, String offset, double turn, double tilt) throws WorldEditException {
        CompoundShape compound = compound(actor);
        try {
            ShapeFlags flags = ShapeFlags.parse(settings, "", "rt", "o");
            turn = flags.value('r', turn);
            tilt = flags.value('t', tilt);
            String offsetText = flags.text('o', offset == null ? "" : offset);
            BlockVector3 shift = parseOffset(offsetText);
            Vector3 radii = ShapeCommands.parseSize(size);
            if (radii.x() < 1 || radii.y() < 1 || radii.z() < 1) {
                throw new IllegalArgumentException("Sizes must be at least 1");
            }
            worldEdit.checkMaxRadius(Math.max(radii.x(), Math.max(radii.y(), radii.z())));
            worldEdit.checkMaxRadius(Math.max(Math.abs(shift.x()), Math.max(Math.abs(shift.y()), Math.abs(shift.z()))));
            ShapeParameters parameters = ShapeParameters.parse(shape.getParameters(), flags.remaining());
            VoxelGrid voxels = ShapeGenerator.voxelize(shape, parameters, radii, new ShapeOrientation(turn, tilt), false,
                ShapeRasterizer.DEFAULT_MAX_VOLUME);
            StringBuilder description = new StringBuilder(shape.getName()).append(' ').append(formatSize(radii));
            for (String setting : flags.remaining()) {
                description.append(' ').append(setting);
            }
            if (!shift.equals(BlockVector3.ZERO)) {
                description.append(" -o ").append(shift.x()).append(',').append(shift.y()).append(',').append(shift.z());
            }
            if (turn != 0) {
                description.append(" -r ").append(ShapeParameter.format(turn));
            }
            if (tilt != 0) {
                description.append(" -t ").append(ShapeParameter.format(tilt));
            }
            BlockVector3 position = compound.getSteps().isEmpty() ? session.getPlacementPosition(actor) : BlockVector3.ZERO;
            compound.add(new CompoundShape.Step(operation, voxels, shift, description.toString()), position);
            int count = compound.getSteps().size();
            actor.printInfo(TextComponent.of("Step " + count + ": " + operation.label() + " " + description
                + (count == 1 ? " (centred at " + compound.getAnchor() + ")" : "")
                + ". Next: //combine add/cut/keep, //combine smooth, //combine build <pattern>."));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
        }
    }

    @Command(
        name = "smooth",
        aliases = { "blend" },
        desc = "Blend the joins between shapes into rounded fillets",
        descFooter = "The number is roughly how many blocks wide the blends are; 0 makes the joins sharp again. "
            + "Example: //combine smooth 4"
    )
    @CommandPermissions(PERMISSION)
    public void smooth(Actor actor,
                       @Arg(desc = "Blend size in blocks (0 = sharp)")
                           double blocks) {
        try {
            compound(actor).setSmoothness(blocks);
            actor.printInfo(TextComponent.of(blocks == 0 ? "Joins are sharp." : "Joins are blended over about "
                + ShapeParameter.format(blocks) + " blocks."));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
        }
    }

    @Command(
        name = "list",
        aliases = { "info" },
        desc = "List the steps of your compound shape"
    )
    @CommandPermissions(PERMISSION)
    public void list(Actor actor) {
        CompoundShape compound = compound(actor);
        List<CompoundShape.Step> steps = compound.getSteps();
        if (steps.isEmpty()) {
            actor.printInfo(TextComponent.of("Your compound shape is empty. Start with //combine add <shape> <size>."));
            return;
        }
        actor.printInfo(TextComponent.of("Compound shape centred at " + compound.getAnchor() + ", joins "
            + (compound.getSmoothness() == 0 ? "sharp" : "blended over " + ShapeParameter.format(compound.getSmoothness())
            + " blocks") + ":"));
        for (int i = 0; i < steps.size(); i++) {
            CompoundShape.Step step = steps.get(i);
            actor.print(TextComponent.of((i + 1) + ". ", TextColor.GRAY)
                .append(TextComponent.of(step.operation().label() + " ", TextColor.YELLOW))
                .append(TextComponent.of(step.description(), TextColor.WHITE)));
        }
    }

    @Command(
        name = "undo",
        desc = "Remove the last step of your compound shape"
    )
    @CommandPermissions(PERMISSION)
    public void undo(Actor actor) {
        CompoundShape.Step removed = compound(actor).undo();
        if (removed == null) {
            actor.printError(TextComponent.of("There are no steps to remove."));
        } else {
            actor.printInfo(TextComponent.of("Removed: " + removed.operation().label() + " " + removed.description()));
        }
    }

    @Command(
        name = "clear",
        aliases = { "reset" },
        desc = "Start your compound shape over"
    )
    @CommandPermissions(PERMISSION)
    public void clear(Actor actor) {
        compound(actor).clear();
        actor.printInfo(TextComponent.of("Compound shape cleared."));
    }

    @Command(
        name = "build",
        aliases = { "set", "make" },
        desc = "Build your compound shape",
        descFooter = "It is built where you stood when you added the first shape. -h builds only the outer layer, "
            + "-m stone only changes stone. Use air as the pattern to carve it out of terrain."
    )
    @CommandPermissions(PERMISSION)
    @Logging(PLACEMENT)
    public int build(Actor actor, EditSession editSession,
                     @Arg(desc = "The pattern of blocks to build with")
                         Pattern pattern,
                     @Switch(name = 'h', desc = "Build only the outer layer")
                         boolean hollow,
                     @ArgFlag(name = 'm', desc = "Only change blocks matching this mask")
                         Mask mask) throws WorldEditException {
        CompoundShape compound = compound(actor);
        VoxelGrid voxels;
        BlockVector3 anchor;
        try {
            voxels = compound.evaluate();
            anchor = compound.getAnchor();
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        if (voxels.count() == 0 || anchor == null) {
            actor.printError(TextComponent.of("The compound shape has no blocks: the cuts removed everything."));
            return 0;
        }
        if (hollow) {
            voxels = voxels.shell(1);
        }
        int changed = ShapeGenerator.build(editSession, List.of(anchor), pattern, voxels, false, mask);
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TextComponent.of("Built the compound shape at " + anchor + ": " + changed + " blocks changed."));
        return changed;
    }

    @Command(
        name = "select",
        aliases = { "sel" },
        desc = "Select your compound shape so //set, //replace, //count etc. work inside it",
        descFooter = "Wand clicks (or //pos1) move the selection."
    )
    @CommandPermissions(PERMISSION)
    public void select(Actor actor, World world, LocalSession session) {
        CompoundShape compound = compound(actor);
        VoxelGrid voxels;
        BlockVector3 anchor;
        try {
            voxels = compound.evaluate();
            anchor = compound.getAnchor();
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return;
        }
        if (voxels.count() == 0 || anchor == null) {
            actor.printError(TextComponent.of("The compound shape has no blocks: the cuts removed everything."));
            return;
        }
        ShapeRegionSelector selector = new ShapeRegionSelector(world, voxels, anchor, BlockVector3.ZERO,
            "compound of " + compound.getSteps().size() + " shapes");
        session.setRegionSelector(world, selector);
        session.dispatchCUISelection(actor);
        actor.printInfo(TextComponent.of("Selected the compound shape at " + anchor + ": " + selector.getVolume()
            + " blocks."));
    }

    private static BlockVector3 parseOffset(String text) {
        if (text == null || text.isBlank()) {
            return BlockVector3.ZERO;
        }
        String[] parts = text.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("The offset needs three numbers, x,y,z (e.g. -o 0,5,-3)");
        }
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            try {
                values[i] = (int) Math.round(Double.parseDouble(parts[i].trim()));
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException("'" + parts[i] + "' in the offset is not a number");
            }
        }
        return BlockVector3.at(values[0], values[1], values[2]);
    }

    private static String formatSize(Vector3 radii) {
        if (radii.x() == radii.y() && radii.y() == radii.z()) {
            return ShapeParameter.format(radii.x());
        }
        return ShapeParameter.format(radii.x()) + "," + ShapeParameter.format(radii.y()) + ","
            + ShapeParameter.format(radii.z());
    }
}
