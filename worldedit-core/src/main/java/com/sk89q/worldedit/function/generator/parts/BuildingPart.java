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

package com.sk89q.worldedit.function.generator.parts;

import com.google.common.collect.ImmutableList;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A parametric building part for {@code //build}, such as a tower or a spiral staircase.
 *
 * @param name the name
 * @param aliases other names
 * @param description a short description
 * @param parameters the settings, in order
 * @param usesSelection whether it runs from the selection's first to second position
 * @param defaultDetail the pattern for windows, panes and railings when none is given, or empty to use the
 *                      main pattern
 * @param exampleSettings settings for the example in the help
 * @param planner computes the blocks
 */
public record BuildingPart(String name, List<String> aliases, String description, List<ShapeParameter> parameters,
                           boolean usesSelection, String defaultDetail, String exampleSettings, Planner planner) {

    public BuildingPart {
        checkNotNull(name);
        aliases = ImmutableList.copyOf(aliases);
        checkNotNull(description);
        parameters = ImmutableList.copyOf(parameters);
        checkNotNull(defaultDetail);
        checkNotNull(exampleSettings);
        checkNotNull(planner);
    }

    /**
     * Where and which way a part is built.
     *
     * @param end for parts that use the selection, the second position relative to the first; otherwise zero
     * @param facing the horizontal direction the player looks in
     */
    public record Context(BlockVector3 end, Direction facing) {

        public Context {
            checkNotNull(end);
            checkNotNull(facing);
        }
    }

    /**
     * Computes the blocks of a part.
     */
    @FunctionalInterface
    public interface Planner {

        /**
         * Add the blocks of the part to a plan.
         *
         * @param settings the settings
         * @param context where and which way
         * @param plan the plan to add to
         */
        void plan(ShapeParameters settings, Context context, BuildPlan plan);
    }

    /**
     * Compute the blocks of this part.
     *
     * @param settings the settings
     * @param context where and which way
     * @return the plan
     * @throws IllegalArgumentException if the settings don't work together or it is too big
     */
    public BuildPlan plan(ShapeParameters settings, Context context) {
        BuildPlan plan = new BuildPlan();
        planner.plan(settings, context, plan);
        return plan;
    }
}
