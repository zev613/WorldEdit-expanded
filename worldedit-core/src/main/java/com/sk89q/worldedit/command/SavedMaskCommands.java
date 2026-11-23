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

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.mask.Mask;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;

import java.util.List;

/**
 * {@code //mask save|delete|list|info}: masks saved under a name for the whole server,
 * used as {@code #name} anywhere a mask is accepted.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class SavedMaskCommands {

    private final SavedInputCommandHelper<Mask> helper;

    public SavedMaskCommands(WorldEdit worldEdit) {
        this.helper = new SavedInputCommandHelper<>(worldEdit.getMaskFactory(), "mask", "//replace ");
    }

    @Command(
        name = "save",
        aliases = { "add", "set" },
        desc = "Save a mask under a name, to use as #name",
        descFooter = "Example: //mask save ores coal_ore,iron_ore,copper_ore"
            + ", then use #ores, e.g. //replace #ores stone"
    )
    @CommandPermissions("worldedit.mask.saved.edit")
    public void save(Actor actor, LocalSession session,
                     @Arg(desc = "The name (letters, digits, _ and -)")
                         String name,
                     @Arg(desc = "The mask", variable = true)
                         List<String> mask) {
        helper.save(actor, session, name, mask);
    }

    @Command(
        name = "delete",
        aliases = { "remove", "del" },
        desc = "Delete a saved mask"
    )
    @CommandPermissions("worldedit.mask.saved.edit")
    public void delete(Actor actor,
                       @Arg(desc = "The name")
                           String name) {
        helper.delete(actor, name);
    }

    @Command(
        name = "list",
        aliases = { "ls" },
        desc = "List the saved masks"
    )
    @CommandPermissions("worldedit.mask.saved")
    public void list(Actor actor) {
        helper.list(actor);
    }

    @Command(
        name = "info",
        aliases = { "show" },
        desc = "Show a saved mask"
    )
    @CommandPermissions("worldedit.mask.saved")
    public void info(Actor actor, LocalSession session,
                     @Arg(desc = "The name")
                         String name) {
        helper.info(actor, session, name);
    }
}
