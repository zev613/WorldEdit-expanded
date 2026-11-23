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

package com.sk89q.worldedit.command.util;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.LongRangeTrace;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import org.enginehub.piston.exception.StopExecutionException;

import java.util.List;

/**
 * Lets generator commands build where the player's crosshair points instead of where they stand.
 */
public final class CrosshairPlacement {

    /**
     * Description used for the crosshair flag on every command.
     */
    public static final String FLAG_DESCRIPTION = "Build where your crosshair points instead of where you stand";

    private CrosshairPlacement() {
    }

    /**
     * The placement position, or the block in front of the surface the player is aiming at.
     *
     * <p>The aimed-at position is the air block just in front of the face the crosshair hits,
     * i.e. the closest spot to the crosshair that is not inside the ground.</p>
     *
     * @param session the session
     * @param actor the actor
     * @param atCrosshair whether to use the crosshair
     * @return the position to build at
     * @throws IncompleteRegionException if the normal placement position is not available
     */
    public static BlockVector3 placement(LocalSession session, Actor actor, boolean atCrosshair)
            throws IncompleteRegionException {
        if (!atCrosshair) {
            return session.getPlacementPosition(actor);
        }
        if (!(actor instanceof Player player)) {
            throw new StopExecutionException(TextComponent.of("Only a player can aim with the crosshair."));
        }
        int maxDistance = Math.max(1, WorldEdit.getInstance().getConfiguration().jumptoMaxDistance);
        LongRangeTrace.Hit hit = LongRangeTrace.fromPlayer(player, maxDistance);
        if (hit == null) {
            throw new StopExecutionException(TextComponent.of(
                "Your crosshair isn't pointing at any block within " + maxDistance + " blocks."));
        }
        return hit.before();
    }

    /**
     * Whether a flag such as {@code -c} was typed among a command's settings (the command framework
     * hands flags typed after variable settings over as settings).
     *
     * @param settings the settings
     * @param letter the flag letter
     * @return true if the flag is there
     */
    public static boolean hasFlag(List<String> settings, char letter) {
        return settings.contains("-" + letter);
    }

    /**
     * The settings without a flag such as {@code -c}.
     *
     * @param settings the settings
     * @param letter the flag letter
     * @return a copy without the flag
     */
    public static List<String> withoutFlag(List<String> settings, char letter) {
        String flag = "-" + letter;
        return settings.stream().filter(s -> !s.equals(flag)).toList();
    }
}
