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
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.argument.HintedArguments;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.shape.ShapeAxis;
import com.sk89q.worldedit.function.generator.shape.ShapeFlags;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.generator.warp.BuiltinWarps;
import com.sk89q.worldedit.function.generator.warp.NamedWarp;
import com.sk89q.worldedit.function.generator.warp.WarpBounds;
import com.sk89q.worldedit.function.generator.warp.WarpMap;
import com.sk89q.worldedit.function.generator.warp.Warper;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.Switch;

import java.util.List;

import static com.sk89q.worldedit.command.util.Logging.LogMode.REGION;

/**
 * Commands that bend, twist and otherwise reshape the selection by name, with plain number settings
 * instead of formulas.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class WarpCommands {

    @Command(
        name = "/warp",
        aliases = { "/namedwarp", "/reshape" },
        desc = "Reshapes the selection by name: twist, bend, taper, wave, shear, spherize, inflate or melt",
        descFooter = "Use //warps to see every warp and //warps <name> for its settings. "
            + "Settings are plain numbers, in order or as name=value; angles are in degrees, "
            + "and axis settings take x, y, z or auto. Example: //warp twist 180. "
            + "Blocks that would come from outside the selection become air unless you use -c, "
            + "so leave some room around what you warp."
    )
    @CommandPermissions("worldedit.region.warp")
    @Logging(REGION)
    public int warp(Actor actor, EditSession editSession,
                    @Selection Region region,
                    @Arg(desc = "The warp (see //warps)")
                        NamedWarp warp,
                    @Arg(desc = "Warp settings: numbers in order, or name=value", def = "", variable = true)
                        List<HintedArguments.SettingWord> settingWords,
                    @Switch(name = 'c', desc = "Pull in blocks from outside the selection instead of air")
                        boolean copyOutside) throws WorldEditException {
        List<String> settings = HintedArguments.strings(settingWords, HintedArguments.SettingWord::value);
        WarpMap map;
        try {
            // flags typed after the settings end up in the settings list, so pick them out here
            ShapeFlags flags = ShapeFlags.parse(settings, "c", "");
            copyOutside |= flags.has('c');
            ShapeParameters values = warp.parseSettings(flags.remaining());
            map = warp.create(values, new WarpBounds(region.getMinimumPoint(), region.getMaximumPoint()));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }

        BaseBlock air = BlockTypes.AIR.getDefaultState().toBaseBlock();
        int affected;
        try {
            affected = Warper.warp(region, map, editSession::getFullBlock,
                copyOutside ? editSession::getFullBlock : _ -> air, air,
                (position, block) -> editSession.setBlock(position, block));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TextComponent.of("Warped with " + warp.getName() + ": " + affected + " blocks changed."));
        return affected;
    }

    @Command(
        name = "/warps",
        desc = "Lists the warps for //warp, or shows the settings of one"
    )
    @CommandPermissions("worldedit.region.warp")
    public void warps(Actor actor,
                      @Arg(desc = "A warp to show the settings of", def = "")
                          String name) {
        if (name != null && !name.isEmpty()) {
            BuiltinWarps.find(name).ifPresentOrElse(
                warp -> printInfo(actor, warp),
                () -> actor.printError(TextComponent.of("Unknown warp '" + name + "'. Use //warps to see them all.")));
            return;
        }
        actor.printInfo(TextComponent.of("Warps for //warp <name> [settings] (click one for details):"));
        for (NamedWarp warp : BuiltinWarps.all()) {
            actor.print(TextComponent.of("  " + warp.getName(), TextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("//warps " + warp.getName()))
                .hoverEvent(HoverEvent.showText(TextComponent.of("Click for settings")))
                .append(TextComponent.of(" - " + warp.getDescription(), TextColor.GRAY)));
        }
    }

    private static void printInfo(Actor actor, NamedWarp warp) {
        actor.printInfo(TextComponent.of(warp.getName(), TextColor.YELLOW)
            .append(TextComponent.of(" - " + warp.getDescription(), TextColor.WHITE)));
        if (!warp.getAliases().isEmpty()) {
            actor.print(TextComponent.of("Also called: " + String.join(", ", warp.getAliases()), TextColor.GRAY));
        }
        actor.print(TextComponent.of("Settings (in this order, or as name=value):", TextColor.GRAY));
        for (ShapeParameter parameter : warp.getParameters()) {
            String range = parameter.name().equals(ShapeAxis.NAME) ? "auto, x, y or z"
                : ShapeParameter.format(parameter.min()) + " to " + ShapeParameter.format(parameter.max());
            actor.print(TextComponent.of("  " + parameter.name(), TextColor.AQUA)
                .append(TextComponent.of(" = " + ShapeAxis.format(parameter, parameter.defaultValue()), TextColor.WHITE))
                .append(TextComponent.of("  " + parameter.description() + " (" + range + ")", TextColor.GRAY)));
        }
        String example = example(warp);
        actor.print(TextComponent.of("Example: ", TextColor.GRAY)
            .append(TextComponent.of(example, TextColor.LIGHT_PURPLE)
                .clickEvent(ClickEvent.suggestCommand(example))
                .hoverEvent(HoverEvent.showText(TextComponent.of("Click to put this in your chat box")))));
    }

    private static String example(NamedWarp warp) {
        return switch (warp.getName()) {
            case "twist" -> "//warp twist 180";
            case "bend" -> "//warp bend 90 vertical=1";
            case "taper" -> "//warp taper 0.3";
            case "wave" -> "//warp wave 4 20 x";
            case "shear" -> "//warp shear 20 z";
            case "spherize" -> "//warp spherize 0.7";
            case "inflate" -> "//warp inflate 2";
            case "melt" -> "//warp melt 0.6 noise=0.8";
            default -> "//warp " + warp.getName();
        };
    }
}
