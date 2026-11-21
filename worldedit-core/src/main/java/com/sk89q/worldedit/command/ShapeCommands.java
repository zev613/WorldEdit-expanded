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
import com.sk89q.worldedit.command.util.ShapeLibraryHolder;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.shape.BuiltinShapes;
import com.sk89q.worldedit.function.generator.shape.NamedShape;
import com.sk89q.worldedit.function.generator.shape.PresetShape;
import com.sk89q.worldedit.function.generator.shape.ShapeFlags;
import com.sk89q.worldedit.function.generator.shape.ShapeGenerator;
import com.sk89q.worldedit.function.generator.shape.ShapeOrientation;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.generator.shape.ShapeRasterizer;
import com.sk89q.worldedit.function.generator.shape.VoxelGrid;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Radii;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
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
            + "Settings are plain numbers, in order or as name=value, e.g. //shape star gold_block 12 points=6"
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
                         double tilt) throws WorldEditException {
        Vector3 radii;
        ShapeParameters parameters;
        try {
            // flags typed after the settings end up in the settings list, so pick them out here
            ShapeFlags flags = ShapeFlags.parse(settings, "hbf", "rt");
            hollow |= flags.has('h');
            bottom |= flags.has('b');
            faceMe |= flags.has('f');
            turn = flags.value('r', turn);
            tilt = flags.value('t', tilt);
            radii = parseSize(size);
            parameters = ShapeParameters.parse(shape.getParameters(), flags.remaining());
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        if (radii.x() < 1 || radii.y() < 1 || radii.z() < 1) {
            actor.printError(TextComponent.of("Sizes must be at least 1"));
            return 0;
        }
        worldEdit.checkMaxRadius(Math.max(radii.x(), Math.max(radii.y(), radii.z())));

        double yaw = turn;
        if (faceMe && actor instanceof Player player) {
            yaw += player.getLocation().getYaw();
        }
        ShapeOrientation orientation = new ShapeOrientation(yaw, tilt);

        VoxelGrid voxels;
        try {
            voxels = ShapeGenerator.voxelize(shape, parameters, radii, orientation, hollow,
                ShapeRasterizer.DEFAULT_MAX_VOLUME);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        BlockVector3 position = session.getPlacementPosition(actor);
        int affected = ShapeGenerator.build(editSession, position, pattern, voxels, bottom);
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TextComponent.of("Generated " + shape.getName() + ": " + affected + " blocks changed."));
        return affected;
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
