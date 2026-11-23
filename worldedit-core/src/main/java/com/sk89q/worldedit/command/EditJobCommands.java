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
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.session.job.EditJob;
import com.sk89q.worldedit.session.job.EditJobQueue;
import com.sk89q.worldedit.util.formatting.Counts;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;

import java.util.List;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;

/**
 * Commands for inspecting edits that are being applied over several server ticks.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class EditJobCommands {

    private final WorldEdit worldEdit;

    public EditJobCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
        name = "/jobs",
        desc = "List, cancel or finish large edits that are being applied over several ticks",
        descFooter = "Actions: list (default), cancel <id>, finish. Cancelling leaves the part already applied "
            + "in place; //undo reverts it."
    )
    @CommandPermissions("worldedit.jobs")
    public void jobs(Actor actor,
                     @Arg(desc = "list, cancel or finish", def = "list")
                         String action,
                     @Arg(desc = "The job id (for cancel)", def = "-1")
                         int id) {
        EditJobQueue queue = worldEdit.getEditJobQueue();
        switch (action.toLowerCase(Locale.ROOT)) {
            case "list" -> {
                List<EditJob> jobs = queue.getJobs();
                if (!queue.isTicking()) {
                    actor.printInfo(TextComponent.of("Time-sliced edits are not available on this platform; "
                        + "edits are applied immediately."));
                }
                if (jobs.isEmpty()) {
                    actor.printInfo(TextComponent.of("No edits are being applied."));
                    return;
                }
                for (EditJob job : jobs) {
                    Actor owner = job.getActor();
                    actor.printInfo(TextComponent.of(String.format("#%d: %,d blocks by %s in %s, running for %.0fs",
                        job.getId(), job.getBlockCount(), owner == null ? "unknown" : owner.getName(),
                        job.getWorld().getName(), job.getElapsedSeconds())));
                }
            }
            case "cancel" -> {
                checkCommandArgument(id > 0, "Give the id of the job to cancel, see //jobs");
                EditJob job = queue.getJobs().stream().filter(j -> j.getId() == id).findFirst().orElse(null);
                checkCommandArgument(job != null, "There is no job #" + id);
                Actor owner = job.getActor();
                boolean own = owner != null && owner.getUniqueId().equals(actor.getUniqueId());
                checkCommandArgument(own || actor.hasPermission("worldedit.jobs.others"),
                    "You can only cancel your own jobs");
                queue.cancel(id);
                actor.printInfo(TextComponent.of("Cancelled job #" + id
                    + ". The part already applied stays; its owner can //undo it."));
            }
            case "finish" -> {
                checkCommandArgument(actor.hasPermission("worldedit.jobs.others") || queue.getJobs().stream()
                        .allMatch(j -> j.getActor() != null && j.getActor().getUniqueId().equals(actor.getUniqueId())),
                    "Other players' jobs are queued; you need worldedit.jobs.others to finish them");
                int count = queue.getJobs().size();
                queue.completeJobs(null);
                actor.printInfo(TextComponent.of("Finished " + Counts.of(count, "job") + "."));
            }
            default -> actor.printError(TextComponent.of("Unknown action '" + action + "'. Use list, cancel or finish."));
        }
    }
}
