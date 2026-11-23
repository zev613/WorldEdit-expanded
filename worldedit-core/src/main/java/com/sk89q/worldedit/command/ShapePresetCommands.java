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

import com.sk89q.worldedit.command.argument.HintedArguments;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.ShapeLibraryHolder;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.shape.NamedShape;
import com.sk89q.worldedit.function.generator.shape.PresetShape;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;

import java.util.List;

/**
 * Save shapes with your favourite settings under a new name. No formulas: a
 * preset is just a shape name plus numbers.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class ShapePresetCommands {

    @Command(
        name = "save",
        aliases = { "add", "set" },
        desc = "Save a shape and its settings under a new name",
        descFooter = "Example: //shapedef save bigknot knot loops=3 winds=5 tube=2, then //shape bigknot stone 25"
    )
    @CommandPermissions("worldedit.generation.shape.preset")
    public void save(Actor actor,
                     @Arg(desc = "The new name")
                         String name,
                     @Arg(desc = "The shape it is based on")
                         NamedShape shape,
                     @Arg(desc = "Settings: numbers in order, or name=value", def = "", variable = true)
                         List<HintedArguments.SettingWord> settingWords) {
        List<String> settings = HintedArguments.strings(settingWords, HintedArguments.SettingWord::value);
        try {
            PresetShape preset = ShapeLibraryHolder.get().savePreset(name, shape.getName(), settings);
            actor.printInfo(TextComponent.of("Saved preset " + preset.getName() + " (" + preset.getDescription()
                + "). Use it with //shape " + preset.getName() + " <pattern> <size>"));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
        }
    }

    @Command(
        name = "delete",
        aliases = { "remove", "del" },
        desc = "Delete a saved shape preset"
    )
    @CommandPermissions("worldedit.generation.shape.preset")
    public void delete(Actor actor,
                       @Arg(desc = "The preset name")
                           String name) {
        if (ShapeLibraryHolder.get().deletePreset(name)) {
            actor.printInfo(TextComponent.of("Deleted preset " + name));
        } else {
            actor.printError(TextComponent.of("There is no preset called " + name));
        }
    }

    @Command(
        name = "list",
        desc = "List saved shape presets"
    )
    @CommandPermissions("worldedit.generation.shape.named")
    public void list(Actor actor) {
        List<PresetShape> presets = ShapeLibraryHolder.get().getPresets();
        if (presets.isEmpty()) {
            actor.printInfo(TextComponent.of("No presets yet. Save one with //shapedef save <name> <shape> [settings]"));
            return;
        }
        actor.printInfo(TextComponent.of(presets.size() + " preset(s):"));
        for (PresetShape preset : presets) {
            actor.print(TextComponent.of(preset.getName(), TextColor.YELLOW)
                .clickEvent(ClickEvent.suggestCommand("//shape " + preset.getName() + " "))
                .append(TextComponent.of(" = " + preset.getDescription().substring("Preset: ".length()), TextColor.GRAY)));
        }
    }
}
