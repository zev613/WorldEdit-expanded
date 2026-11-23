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
import com.sk89q.worldedit.command.util.ShapeLibraryHolder;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.shape.BuiltinShapes;
import com.sk89q.worldedit.function.generator.shape.NamedShape;
import com.sk89q.worldedit.function.generator.shape.PresetShape;
import com.sk89q.worldedit.function.generator.shape.ShapeArray;
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
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;

/**
 * Commands for generating shapes by name, with plain number settings instead of formulas.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class ShapeCommands {

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public ShapeCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    /**
     * Turn the size argument into x, y and z radii.
     *
     * @param size one or three numbers
     * @return the radii
     */
    static Vector3 parseSize(List<Double> size) {
        return switch (size.size()) {
            case 1 -> Vector3.at(size.get(0), size.get(0), size.get(0));
            case 3 -> Vector3.at(size.get(0), size.get(1), size.get(2));
            default -> throw new IllegalArgumentException("Give one size, or three sizes as x,y,z (east-west, up-down, north-south)");
        };
    }

    @Command(
        name = "/shape",
        aliases = { "/namedshape" },
        desc = "Generates a shape by name, such as heart, star, torus, knot or gyroid",
        descFooter = "Use //shapes to see every shape and //shapes <name> for its settings. "
            + "The size is the radius in blocks, or x,y,z radii to stretch it (e.g. 10,20,10). "
            + "Settings are plain numbers, in order or as name=value, e.g. //shape star gold_block 12 points=6. "
            + "Copies: -a 5 -l 30 (a line of 5, 30 blocks apart, the way you face), -a 8 -c 40 (a ring of 8), "
            + "-a 12 -c 20 -s 4 (a spiral rising 4 blocks per copy). "
            + "-m stone only changes stone; use air as the pattern to carve the shape out of terrain."
    )
    @CommandPermissions("worldedit.generation.shape.named")
    @Logging(PLACEMENT)
    public int shape(Actor actor, LocalSession session, EditSession editSession,
                     @Arg(desc = "The shape (see //shapes)")
                         NamedShape shape,
                     @Arg(desc = "The pattern of blocks to build with")
                         Pattern pattern,
                     @Arg(desc = "The radius, or x,y,z radii")
                     @Radii(3)
                         List<Double> size,
                     @Arg(desc = "Shape settings: numbers in order, or name=value", def = "", variable = true)
                         List<String> settings,
                     @Switch(name = 'h', desc = "Make it hollow")
                         boolean hollow,
                     @Switch(name = 'b', desc = "Put the bottom of the shape at your position instead of its middle")
                         boolean bottom,
                     @Switch(name = 'f', desc = "Turn the shape so its front faces you")
                         boolean faceMe,
                     @ArgFlag(name = 'r', desc = "Turn the shape clockwise by this many degrees", def = "0")
                         double turn,
                     @ArgFlag(name = 't', desc = "Tilt the shape by this many degrees (90 = on its side)", def = "0")
                         double tilt,
                     @ArgFlag(name = 'a', desc = "Build this many copies (in a line, or a ring with -c)", def = "0")
                         double copies,
                     @ArgFlag(name = 'l', desc = "Distance between copies in a line, the way you face", def = "0")
                         double spacing,
                     @ArgFlag(name = 'c', desc = "Put the copies in a ring of this radius around you", def = "0")
                         double ringRadius,
                     @ArgFlag(name = 's', desc = "Raise each copy by this many blocks (a spiral with -c)", def = "0")
                         double rise,
                     @ArgFlag(name = 'm', desc = "Only change blocks matching this mask, e.g. stone")
                         Mask mask,
                       @Switch(name = 'x', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        Vector3 radii;
        ShapeParameters parameters;
        ShapeArray array;
        try {
            // flags typed after the settings end up in the settings list, so pick them out here
            ShapeFlags flags = ShapeFlags.parse(settings, "hbfx", "rtalcs", "m");
            hollow |= flags.has('h');
            bottom |= flags.has('b');
            faceMe |= flags.has('f');
            atCrosshair |= flags.has('x');
            turn = flags.value('r', turn);
            tilt = flags.value('t', tilt);
            copies = flags.value('a', copies);
            spacing = flags.value('l', spacing);
            ringRadius = flags.value('c', ringRadius);
            rise = flags.value('s', rise);
            Optional<String> maskInput = flags.text('m');
            if (maskInput.isPresent()) {
                mask = parseMask(actor, session, editSession, maskInput.get());
            }
            radii = parseSize(size);
            parameters = ShapeParameters.parse(shape.getParameters(), flags.remaining());
            double width = 2 * Math.max(radii.x(), Math.max(radii.y(), radii.z()));
            array = ShapeArray.of(copies, spacing, ringRadius, rise, Math.ceil(width) + 2);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        if (radii.x() < 1 || radii.y() < 1 || radii.z() < 1) {
            actor.printError(TextComponent.of("Sizes must be at least 1"));
            return 0;
        }
        worldEdit.checkMaxRadius(Math.max(radii.x(), Math.max(radii.y(), radii.z())));

        VoxelGrid voxels = voxelize(actor, shape, parameters, radii, hollow, faceMe, turn, tilt);
        if (voxels == null) {
            return 0;
        }
        BlockVector3 position = CrosshairPlacement.placement(session, actor, atCrosshair);
        List<BlockVector3> positions = new ArrayList<>();
        for (BlockVector3 offset : array.offsets(facing(actor))) {
            positions.add(position.add(offset));
        }
        int affected = ShapeGenerator.build(editSession, positions, pattern, voxels, bottom, mask);
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        String what = array.count() == 1 ? shape.getName() : array.count() + " x " + shape.getName();
        actor.printInfo(TextComponent.of("Generated " + what + ": " + affected + " blocks changed."));
        return affected;
    }

    @Command(
        name = "/selshape",
        aliases = { "/shapesel" },
        desc = "Selects a named shape (heart, star, torus...) so //set, //replace, //count etc. work inside it",
        descFooter = "Same size, settings and flags as //shape, without a pattern, e.g. //selshape heart 12 -f. "
            + "Wand clicks (or //pos1) move the shape to the clicked block. "
            + "The selection can be moved with //shift or //move -s, but not expanded."
    )
    @CommandPermissions("worldedit.selection.shape")
    public void selectShape(Actor actor, World world, LocalSession session,
                            @Arg(desc = "The shape (see //shapes)")
                                NamedShape shape,
                            @Arg(desc = "The radius, or x,y,z radii")
                            @Radii(3)
                                List<Double> size,
                            @Arg(desc = "Shape settings: numbers in order, or name=value", def = "", variable = true)
                                List<String> settings,
                            @Switch(name = 'h', desc = "Select only the outer layer")
                                boolean hollow,
                            @Switch(name = 'b', desc = "Put the bottom of the shape at your position instead of its middle")
                                boolean bottom,
                            @Switch(name = 'f', desc = "Turn the shape so its front faces you")
                                boolean faceMe,
                            @ArgFlag(name = 'r', desc = "Turn the shape clockwise by this many degrees", def = "0")
                                double turn,
                            @ArgFlag(name = 't', desc = "Tilt the shape by this many degrees (90 = on its side)", def = "0")
                                double tilt,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        Vector3 radii;
        ShapeParameters parameters;
        List<String> settingWords;
        try {
            ShapeFlags flags = ShapeFlags.parse(settings, "hbfc", "rt");
            hollow |= flags.has('h');
            bottom |= flags.has('b');
            faceMe |= flags.has('f');
            atCrosshair |= flags.has('c');
            turn = flags.value('r', turn);
            tilt = flags.value('t', tilt);
            radii = parseSize(size);
            settingWords = flags.remaining();
            parameters = ShapeParameters.parse(shape.getParameters(), settingWords);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return;
        }
        if (radii.x() < 1 || radii.y() < 1 || radii.z() < 1) {
            actor.printError(TextComponent.of("Sizes must be at least 1"));
            return;
        }
        VoxelGrid voxels = voxelize(actor, shape, parameters, radii, hollow, faceMe, turn, tilt);
        if (voxels == null) {
            return;
        }
        if (voxels.count() == 0) {
            actor.printError(TextComponent.of("That shape has no blocks at this size; try making it bigger."));
            return;
        }
        BlockVector3 position = CrosshairPlacement.placement(session, actor, atCrosshair);
        BlockVector3 anchorOffset = bottom ? BlockVector3.at(0, -voxels.lowestY(), 0) : BlockVector3.ZERO;
        String description = shape.getName() + " " + formatSize(radii)
            + (settingWords.isEmpty() ? "" : " " + String.join(" ", settingWords));
        ShapeRegionSelector selector = new ShapeRegionSelector(world, voxels, position, anchorOffset, description);
        session.setRegionSelector(world, selector);
        session.dispatchCUISelection(actor);
        actor.printInfo(TextComponent.of("Selected " + shape.getName() + " at " + position + ": "
            + selector.getVolume() + " blocks. Wand clicks move it."));
    }

    @Nullable
    private static VoxelGrid voxelize(Actor actor, NamedShape shape, ShapeParameters parameters, Vector3 radii,
                                      boolean hollow, boolean faceMe, double turn, double tilt) {
        double yaw = turn;
        if (faceMe && actor instanceof Player player) {
            yaw += player.getLocation().getYaw();
        }
        ShapeOrientation orientation = new ShapeOrientation(yaw, tilt);
        try {
            return ShapeGenerator.voxelize(shape, parameters, radii, orientation, hollow,
                ShapeRasterizer.DEFAULT_MAX_VOLUME);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return null;
        }
    }

    private static Vector3 facing(Actor actor) {
        if (actor instanceof Player player) {
            return ShapeArray.facing(player.getLocation().getYaw(), player.getLocation().getPitch());
        }
        return ShapeArray.facing(Double.NaN, Double.NaN);
    }

    private static Mask parseMask(Actor actor, LocalSession session, EditSession editSession, String input) {
        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(editSession.getWorld());
        context.setExtent(editSession);
        context.setSession(session);
        context.setRestricted(true);
        try {
            return WorldEdit.getInstance().getMaskFactory().parseFromInput(input, context);
        } catch (InputParseException e) {
            throw new IllegalArgumentException("Bad mask '" + input + "': " + e.getMessage(), e);
        }
    }

    private static String formatSize(Vector3 radii) {
        if (radii.x() == radii.y() && radii.y() == radii.z()) {
            return ShapeParameter.format(radii.x());
        }
        return ShapeParameter.format(radii.x()) + "," + ShapeParameter.format(radii.y()) + ","
            + ShapeParameter.format(radii.z());
    }

    @Command(
        name = "/shapes",
        desc = "Lists the shapes for //shape, or shows the settings of one"
    )
    @CommandPermissions("worldedit.generation.shape.named")
    public void shapes(Actor actor,
                       @Arg(desc = "A shape to show the settings of", def = "")
                           String name) {
        if (name != null && !name.isEmpty()) {
            ShapeLibraryHolder.get().find(name).ifPresentOrElse(
                shape -> printInfo(actor, shape),
                () -> actor.printError(TextComponent.of("Unknown shape '" + name + "'. Use //shapes to see them all.")));
            return;
        }
        Map<String, List<NamedShape>> byCategory = new LinkedHashMap<>();
        for (NamedShape shape : ShapeLibraryHolder.get().getBuiltins()) {
            byCategory.computeIfAbsent(shape.getCategory(), _ -> new ArrayList<>()).add(shape);
        }
        List<PresetShape> presets = ShapeLibraryHolder.get().getPresets();
        if (!presets.isEmpty()) {
            byCategory.put("Your presets", new ArrayList<>(presets));
        }
        actor.printInfo(TextComponent.of("Shapes for //shape <name> <pattern> <size> [settings] (click one for details):"));
        for (Map.Entry<String, List<NamedShape>> entry : byCategory.entrySet()) {
            TextComponent.Builder line = TextComponent.builder(entry.getKey() + ": ", TextColor.GRAY);
            boolean first = true;
            for (NamedShape shape : entry.getValue()) {
                if (!first) {
                    line.append(TextComponent.of(", ", TextColor.GRAY));
                }
                first = false;
                line.append(TextComponent.of(shape.getName(), TextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("//shapes " + shape.getName()))
                    .hoverEvent(HoverEvent.showText(TextComponent.of(shape.getDescription()))));
            }
            actor.print(line.build());
        }
    }

    private static void printInfo(Actor actor, NamedShape shape) {
        actor.printInfo(TextComponent.of(shape.getName(), TextColor.YELLOW)
            .append(TextComponent.of(" - " + shape.getDescription(), TextColor.WHITE)));
        if (!shape.getAliases().isEmpty()) {
            actor.print(TextComponent.of("Also called: " + String.join(", ", shape.getAliases()), TextColor.GRAY));
        }
        if (shape.getParameters().isEmpty()) {
            actor.print(TextComponent.of("No settings.", TextColor.GRAY));
        } else {
            actor.print(TextComponent.of("Settings (in this order, or as name=value):", TextColor.GRAY));
            for (ShapeParameter parameter : shape.getParameters()) {
                actor.print(TextComponent.of("  " + parameter.name(), TextColor.AQUA)
                    .append(TextComponent.of(" = " + ShapeParameter.format(parameter.defaultValue()), TextColor.WHITE))
                    .append(TextComponent.of("  " + parameter.description() + " ("
                        + ShapeParameter.format(parameter.min()) + " to "
                        + ShapeParameter.format(parameter.max()) + ")", TextColor.GRAY)));
            }
        }
        String example = example(shape);
        actor.print(TextComponent.of("Example: ", TextColor.GRAY)
            .append(TextComponent.of(example, TextColor.LIGHT_PURPLE)
                .clickEvent(ClickEvent.suggestCommand(example))
                .hoverEvent(HoverEvent.showText(TextComponent.of("Click to put this in your chat box")))));
    }

    private static String example(NamedShape shape) {
        NamedShape base = shape instanceof PresetShape preset ? preset.getBase() : shape;
        String size = switch (base.getCategory()) {
            case BuiltinShapes.NATURE -> base.getName().equals("galaxy") ? "30,10,30" : "40,25,40";
            case BuiltinShapes.FRACTALS -> "27";
            default -> base.getName().equals("dna") || base.getName().equals("helix") ? "6,25,6" : "12";
        };
        String block = switch (base.getName()) {
            case "heart" -> "red_wool";
            case "star" -> "gold_block";
            case "mountain", "volcano" -> "stone";
            case "ripple" -> "water";
            default -> "stone";
        };
        StringBuilder builder = new StringBuilder("//shape ").append(shape.getName()).append(' ')
            .append(block).append(' ').append(size);
        if (!shape.getParameters().isEmpty() && !shape.isCustom()) {
            ShapeParameter first = shape.getParameters().get(0);
            double value = first.defaultValue() + 1 <= first.max() ? first.defaultValue() + 1 : first.defaultValue();
            if (first.max() - first.min() <= 1.0001) {
                value = Math.min(first.max(), Math.max(first.min(), first.defaultValue() == first.max()
                    ? first.max() / 2 : (first.defaultValue() + first.max()) / 2));
            }
            builder.append(' ').append(first.name().toLowerCase(Locale.ROOT)).append('=').append(ShapeParameter.format(value));
        }
        if (base.getName().equals("heart") || base.getName().equals("star")) {
            builder.append(" -f");
        }
        return builder.toString();
    }
}
