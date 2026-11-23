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
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.argument.HintedArguments;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.StyleLibraryHolder;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.style.BuildingStyle;
import com.sk89q.worldedit.function.generator.style.StyleLibrary;
import com.sk89q.worldedit.function.generator.style.StyleRole;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;

import java.util.List;

/**
 * Lists, shows, saves and deletes building styles for {@code //skyscraper}.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class StyleCommands {

    @Command(
        name = "list",
        desc = "List the building styles"
    )
    @CommandPermissions("worldedit.generation.skyscraper")
    public void list(Actor actor) {
        StyleLibrary library = StyleLibraryHolder.get();
        actor.printInfo(TextComponent.of("Building styles (click one for its blocks):"));
        printNames(actor, "Built in: ", library.getBuiltins());
        List<BuildingStyle> saved = library.getSaved();
        if (saved.isEmpty()) {
            actor.print(TextComponent.of("Save your own with //style save <name> <base style> role=block ...",
                TextColor.GRAY));
        } else {
            printNames(actor, "Saved: ", saved);
        }
    }

    private static void printNames(Actor actor, String label, List<BuildingStyle> styles) {
        TextComponent.Builder line = TextComponent.builder(label, TextColor.GRAY);
        boolean first = true;
        for (BuildingStyle style : styles) {
            if (!first) {
                line.append(TextComponent.of(", ", TextColor.GRAY));
            }
            first = false;
            line.append(TextComponent.of(style.name(), TextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("//style info " + style.name()))
                .hoverEvent(HoverEvent.showText(TextComponent.of(style.description()))));
        }
        actor.print(line.build());
    }

    @Command(
        name = "info",
        aliases = { "show" },
        desc = "Show the blocks of a building style"
    )
    @CommandPermissions("worldedit.generation.skyscraper")
    public void info(Actor actor,
                     @Arg(desc = "The style")
                         HintedArguments.StyleName styleName) {
        String name = styleName.value();
        StyleLibrary library = StyleLibraryHolder.get();
        BuildingStyle style = library.find(name).orElse(null);
        if (style == null) {
            actor.printError(TextComponent.of("Unknown style '" + name + "'. Use //style list to see them."));
            return;
        }
        String base = library.isBuiltin(style.name()) ? "built in" : "based on " + style.family();
        actor.printInfo(TextComponent.of(style.name(), TextColor.YELLOW)
            .append(TextComponent.of(" (" + base + ") - " + style.description(), TextColor.WHITE)));
        for (StyleRole role : StyleRole.values()) {
            actor.print(TextComponent.of("  " + role.id(), TextColor.AQUA)
                .append(TextComponent.of(" = " + style.block(role), TextColor.WHITE))
                .append(TextComponent.of("  " + role.description(), TextColor.GRAY)));
        }
        String example = "//skyscraper " + style.name() + " 20";
        actor.print(TextComponent.of("Example: ", TextColor.GRAY)
            .append(TextComponent.of(example, TextColor.LIGHT_PURPLE)
                .clickEvent(ClickEvent.suggestCommand(example))));
    }

    @Command(
        name = "save",
        aliases = { "add", "set" },
        desc = "Save a building style: a base style with some blocks changed",
        descFooter = "Roles: wall, window, trim, pillar, floor, roof, accent, light. "
            + "Example: //style save harbour art_deco window=cyan_stained_glass accent=copper_block"
    )
    @CommandPermissions("worldedit.generation.skyscraper.style")
    public void save(Actor actor, World world, LocalSession session, EditSession editSession,
                     @Arg(desc = "The new style's name")
                         String name,
                     @Arg(desc = "The style it is based on")
                         HintedArguments.StyleName baseName,
                     @Arg(desc = "Blocks to change, as role=block", def = "", variable = true)
                         List<String> changes) throws WorldEditException {
        String base = baseName.value();
        StyleLibrary library = StyleLibraryHolder.get();
        BuildingStyle.Overrides overrides;
        try {
            overrides = BuildingStyle.splitOverrides(changes);
            if (!overrides.remaining().isEmpty()) {
                actor.printError(TextComponent.of("'" + overrides.remaining().getFirst() + "' is not role=block. "
                    + "Roles: " + StyleRole.names()));
                return;
            }
            BuildingStyle baseStyle = library.find(base).orElseThrow(() -> new IllegalArgumentException(
                "Unknown style '" + base + "'. Use //style list to see them."));
            // check the blocks before saving
            SkyscraperCommands.patterns(baseStyle.withOverrides(overrides.overrides()), actor, world, session,
                editSession);
            boolean existed = library.getSaved().stream().anyMatch(style -> style.name().equalsIgnoreCase(name.trim()));
            BuildingStyle saved = library.save(name, base, overrides.overrides());
            actor.printInfo(TextComponent.of((existed ? "Replaced style " : "Saved style ") + saved.name() + " (based on " + saved.family()
                + "). Build with //skyscraper " + saved.name() + " <stories>"));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
        }
    }

    @Command(
        name = "delete",
        aliases = { "remove", "del" },
        desc = "Delete a saved building style"
    )
    @CommandPermissions("worldedit.generation.skyscraper.style")
    public void delete(Actor actor,
                       @Arg(desc = "The style")
                           HintedArguments.StyleName styleName) {
        String name = styleName.value();
        StyleLibrary library = StyleLibraryHolder.get();
        if (library.isBuiltin(name)) {
            actor.printError(TextComponent.of("Built-in styles can't be deleted."));
        } else if (library.delete(name)) {
            actor.printInfo(TextComponent.of("Deleted style " + name));
        } else {
            actor.printError(TextComponent.of("There is no saved style called " + name));
        }
    }
}
