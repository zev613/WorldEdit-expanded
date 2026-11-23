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

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.util.formatting.text.format.TextDecoration;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Links to the illustrated guide for this fork's commands.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class GuidebookCommands {

    private final WorldEdit worldEdit;

    public GuidebookCommands(WorldEdit worldEdit) {
        this.worldEdit = checkNotNull(worldEdit);
    }

    @Command(
        name = "/guidebook",
        aliases = { "/weguide" },
        desc = "Get a link to the illustrated guide for the WorldEdit Expanded commands",
        descFooter = "The link is set by guidebook-url in WorldEdit's config."
    )
    @CommandPermissions("worldedit.help")
    public void guidebook(Actor actor) {
        String url = worldEdit.getConfiguration().guidebookUrl;
        if (url == null || url.isBlank()) {
            actor.printError(TextComponent.of("No guide link is set. Add guidebook-url to WorldEdit's config."));
            return;
        }
        url = url.trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            actor.printError(TextComponent.of("The guidebook-url in WorldEdit's config must start with https://"));
            return;
        }
        if (actor instanceof Player) {
            actor.print(TextComponent.of("[Open the WorldEdit Expanded guide]", TextColor.AQUA)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(TextComponent.of("Opens in your web browser:\n" + url))));
            actor.print(TextComponent.of("Click the link above; Minecraft asks before opening it.", TextColor.GRAY));
        } else {
            actor.printInfo(TextComponent.of("WorldEdit Expanded guide: " + url));
        }
    }
}
