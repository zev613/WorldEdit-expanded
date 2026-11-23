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
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Radii;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.internal.util.TransformUtil;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.generation.ConfiguredFeatureType;
import com.sk89q.worldedit.world.generation.StructureType;
import com.sk89q.worldedit.world.generation.TreeType;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.Switch;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.command.util.Logging.LogMode.ALL;
import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;
import static com.sk89q.worldedit.command.util.Logging.LogMode.POSITION;
import static com.sk89q.worldedit.internal.command.CommandUtil.checkCommandArgument;

/**
 * Commands for the generation of shapes and other objects.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class GenerationCommands {

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public GenerationCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    @Command(
        name = "/hcyl",
        desc = "Generates a hollow cylinder."
    )
    @CommandPermissions("worldedit.generation.cylinder")
    @Logging(PLACEMENT)
    public int hcyl(Actor actor, LocalSession session, EditSession editSession,
                    @Arg(desc = "The pattern of blocks to generate")
                        Pattern pattern,
                    @Arg(desc = "The radii of the cylinder. 1st is N/S, 2nd is E/W")
                    @Radii(2)
                        List<Double> radii,
                    @Arg(desc = "The height of the cylinder", def = "1")
                        int height,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        return cyl(actor, session, editSession, pattern, radii, height, true, atCrosshair);
    }

    @Command(
        name = "/cyl",
        desc = "Generates a cylinder."
    )
    @CommandPermissions("worldedit.generation.cylinder")
    @Logging(PLACEMENT)
    public int cyl(Actor actor, LocalSession session, EditSession editSession,
                   @Arg(desc = "The pattern of blocks to generate")
                       Pattern pattern,
                   @Arg(desc = "The radii of the cylinder. 1st is N/S, 2nd is E/W")
                   @Radii(2)
                       List<Double> radii,
                   @Arg(desc = "The height of the cylinder", def = "1")
                       int height,
                   @Switch(name = 'h', desc = "Make a hollow cylinder")
                       boolean hollow,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        double radiusX;
        double radiusZ;
        switch (radii.size()) {
            case 1 -> radiusX = radiusZ = Math.max(1, radii.get(0));
            case 2 -> {
                radiusX = Math.max(1, radii.get(0));
                radiusZ = Math.max(1, radii.get(1));
            }
            default -> {
                actor.printError(TranslatableComponent.of("worldedit.cyl.invalid-radius"));
                return 0;
            }
        }

        worldEdit.checkMaxRadius(radiusX);
        worldEdit.checkMaxRadius(radiusZ);
        worldEdit.checkMaxRadius(height);

        BlockVector3 pos = CrosshairPlacement.placement(session, actor, atCrosshair);
        int affected = editSession.makeCylinder(pos, pattern, radiusX, radiusZ, height, !hollow);
        actor.printInfo(TranslatableComponent.of("worldedit.cyl.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/cone",
        desc = "Generates a cone."
    )
    @CommandPermissions("worldedit.generation.cone")
    @Logging(PLACEMENT)
    public int cone(Actor actor, LocalSession session, EditSession editSession,
                   @Arg(desc = "The pattern of blocks to generate")
                       Pattern pattern,
                   @Arg(desc = "The radii of the cone. 1st is N/S, 2nd is E/W")
                   @Radii(2)
                       List<Double> radii,
                   @Arg(desc = "The height of the cone", def = "1")
                       int height,
                   @Switch(name = 'h', desc = "Make a hollow cone")
                       boolean hollow,
                   @Arg(desc = "Thickness of the hollow cone", def = "1")
                       double thickness,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        double radiusX;
        double radiusZ;
        switch (radii.size()) {
            case 1 -> radiusX = radiusZ = Math.max(1, radii.get(0));
            case 2 -> {
                radiusX = Math.max(1, radii.get(0));
                radiusZ = Math.max(1, radii.get(1));
            }
            default -> {
                actor.printError(TranslatableComponent.of("worldedit.cone.invalid-radius"));
                return 0;
            }
        }

        worldEdit.checkMaxRadius(radiusX);
        worldEdit.checkMaxRadius(radiusZ);
        worldEdit.checkMaxRadius(height);

        BlockVector3 pos = CrosshairPlacement.placement(session, actor, atCrosshair);
        int affected = editSession.makeCone(pos, pattern, radiusX, radiusZ, height, !hollow, thickness);
        actor.printInfo(TranslatableComponent.of("worldedit.cone.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/hsphere",
        desc = "Generates a hollow sphere."
    )
    @CommandPermissions("worldedit.generation.sphere")
    @Logging(PLACEMENT)
    public int hsphere(Actor actor, LocalSession session, EditSession editSession,
                       @Arg(desc = "The pattern of blocks to generate")
                           Pattern pattern,
                       @Arg(desc = "The radii of the sphere. Order is N/S, U/D, E/W")
                       @Radii(3)
                           List<Double> radii,
                       @Switch(name = 'r', desc = "Raise the bottom of the sphere to the placement position")
                           boolean raised,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        return sphere(actor, session, editSession, pattern, radii, raised, true, atCrosshair);
    }

    @Command(
        name = "/sphere",
        desc = "Generates a filled sphere."
    )
    @CommandPermissions("worldedit.generation.sphere")
    @Logging(PLACEMENT)
    public int sphere(Actor actor, LocalSession session, EditSession editSession,
                      @Arg(desc = "The pattern of blocks to generate")
                          Pattern pattern,
                      @Arg(desc = "The radii of the sphere. Order is N/S, U/D, E/W")
                      @Radii(3)
                          List<Double> radii,
                      @Switch(name = 'r', desc = "Raise the bottom of the sphere to the placement position")
                          boolean raised,
                      @Switch(name = 'h', desc = "Make a hollow sphere")
                          boolean hollow,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        double radiusX;
        double radiusY;
        double radiusZ;
        switch (radii.size()) {
            case 1 -> radiusX = radiusY = radiusZ = Math.max(0, radii.get(0));
            case 3 -> {
                radiusX = Math.max(0, radii.get(0));
                radiusY = Math.max(0, radii.get(1));
                radiusZ = Math.max(0, radii.get(2));
            }
            default -> {
                actor.printError(TranslatableComponent.of("worldedit.sphere.invalid-radius"));
                return 0;
            }
        }

        worldEdit.checkMaxRadius(radiusX);
        worldEdit.checkMaxRadius(radiusY);
        worldEdit.checkMaxRadius(radiusZ);

        BlockVector3 pos = CrosshairPlacement.placement(session, actor, atCrosshair);
        if (raised) {
            pos = pos.add(0, (int) radiusY, 0);
        }

        int affected = editSession.makeSphere(pos, pattern, radiusX, radiusY, radiusZ, !hollow);
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TranslatableComponent.of("worldedit.sphere.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/helix",
        aliases = { "/spiral" },
        desc = "Generates a 3D helix or spiral."
    )
    @CommandPermissions("worldedit.generation.helix")
    @Logging(PLACEMENT)
    public int helix(Actor actor, LocalSession session, EditSession editSession,
                     @Arg(desc = "The pattern of blocks to generate")
                         Pattern pattern,
                     @Arg(desc = "The radii of the helix. 1st is N/S, 2nd is E/W")
                     @Radii(2)
                         List<Double> radii,
                     @Arg(desc = "The height of the helix")
                         int height,
                     @Arg(desc = "The number of turns/revolutions", def = "1")
                         double turns,
                     @Arg(desc = "Thickness of the helix strand", def = "0")
                         double thickness,
                     @Switch(name = 'h', desc = "Generate only a shell")
                         boolean shell,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        double radiusX;
        double radiusZ;
        switch (radii.size()) {
            case 1 -> radiusX = radiusZ = Math.max(1, radii.get(0));
            case 2 -> {
                radiusX = Math.max(1, radii.get(0));
                radiusZ = Math.max(1, radii.get(1));
            }
            default -> {
                actor.printError(TextComponent.of("Invalid number of radii for helix"));
                return 0;
            }
        }

        worldEdit.checkMaxRadius(radiusX);
        worldEdit.checkMaxRadius(radiusZ);
        worldEdit.checkMaxRadius(thickness);

        BlockVector3 pos = CrosshairPlacement.placement(session, actor, atCrosshair);
        int affected = editSession.makeHelix(pos, pattern, radiusX, radiusZ, height, turns, thickness, !shell);
        actor.printInfo(TextComponent.of(affected + " blocks changed."));
        return affected;
    }

    @Command(
        name = "/hhelix",
        aliases = { "/hspiral" },
        desc = "Generates a hollow 3D helix or spiral."
    )
    @CommandPermissions("worldedit.generation.helix")
    @Logging(PLACEMENT)
    public int hhelix(Actor actor, LocalSession session, EditSession editSession,
                      @Arg(desc = "The pattern of blocks to generate")
                          Pattern pattern,
                      @Arg(desc = "The radii of the helix. 1st is N/S, 2nd is E/W")
                      @Radii(2)
                          List<Double> radii,
                      @Arg(desc = "The height of the helix")
                          int height,
                      @Arg(desc = "The number of turns/revolutions", def = "1")
                          double turns,
                      @Arg(desc = "Thickness of the helix strand", def = "1")
                          double thickness,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        return helix(actor, session, editSession, pattern, radii, height, turns, thickness, true, atCrosshair);
    }

    @Command(
        name = "/torus",
        aliases = { "/donut" },
        desc = "Generates a 3D torus (donut shape)."
    )
    @CommandPermissions("worldedit.generation.torus")
    @Logging(PLACEMENT)
    public int torus(Actor actor, LocalSession session, EditSession editSession,
                     @Arg(desc = "The pattern of blocks to generate")
                         Pattern pattern,
                     @Arg(desc = "The major radius (distance from center to tube center)")
                         double majorRadius,
                     @Arg(desc = "The minor radius (tube thickness)")
                         double minorRadius,
                     @Switch(name = 'h', desc = "Generate only a shell")
                         boolean shell,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        checkCommandArgument(majorRadius > 0, "Major radius must be > 0");
        checkCommandArgument(minorRadius > 0, "Minor radius must be > 0");
        worldEdit.checkMaxRadius(majorRadius + minorRadius);

        BlockVector3 pos = CrosshairPlacement.placement(session, actor, atCrosshair);
        int affected = editSession.makeTorus(pos, pattern, majorRadius, minorRadius, !shell);
        actor.printInfo(TextComponent.of(affected + " blocks changed."));
        return affected;
    }

    @Command(
        name = "/htorus",
        aliases = { "/hdonut" },
        desc = "Generates a hollow 3D torus (donut shell)."
    )
    @CommandPermissions("worldedit.generation.torus")
    @Logging(PLACEMENT)
    public int htorus(Actor actor, LocalSession session, EditSession editSession,
                      @Arg(desc = "The pattern of blocks to generate")
                          Pattern pattern,
                      @Arg(desc = "The major radius (distance from center to tube center)")
                          double majorRadius,
                      @Arg(desc = "The minor radius (tube thickness)")
                          double minorRadius,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        return torus(actor, session, editSession, pattern, majorRadius, minorRadius, true, atCrosshair);
    }

    @Command(
        name = "forestgen",
        desc = "Generate a forest"
    )
    @CommandPermissions("worldedit.generation.forest")
    @Logging(POSITION)
    public int forestGen(Actor actor, LocalSession session, EditSession editSession,
                         @Arg(desc = "The size of the forest, in blocks", def = "10")
                             int size,
                         @Arg(desc = "The type of forest", def = "tree")
                             TreeType type,
                         @Arg(desc = "The density of the forest, between 0 and 100", def = "5")
                             double density,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        checkCommandArgument(0 <= density && density <= 100, "Density must be between 0 and 100");
        worldEdit.checkMaxRadius(size);
        density /= 100;
        int affected = editSession.makeForest(CrosshairPlacement.placement(session, actor, atCrosshair), size, density, type);
        actor.printInfo(TranslatableComponent.of("worldedit.forestgen.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "pumpkins",
        desc = "Generate pumpkin patches"
    )
    @CommandPermissions("worldedit.generation.pumpkins")
    @Logging(POSITION)
    public int pumpkins(Actor actor, LocalSession session, EditSession editSession,
                        @Arg(desc = "The size of the patch", def = "10")
                            int size,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        worldEdit.checkMaxRadius(size);
        int affected = editSession.makePumpkinPatches(CrosshairPlacement.placement(session, actor, atCrosshair), size);
        actor.printInfo(TranslatableComponent.of("worldedit.pumpkins.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/feature",
        desc = "Generate Minecraft features"
    )
    @CommandPermissions("worldedit.generation.feature")
    @Logging(POSITION)
    public int feature(Actor actor, LocalSession session, EditSession editSession,
                       @Arg(desc = "The feature")
                       ConfiguredFeatureType feature,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        if (editSession.getWorld().generateFeature(feature, editSession, CrosshairPlacement.placement(session, actor, atCrosshair))) {
            actor.printInfo(TranslatableComponent.of("worldedit.feature.created"));
        } else {
            actor.printError(TranslatableComponent.of("worldedit.feature.failed"));
        }
        return 0;
    }

    @Command(
        name = "/structure",
        desc = "Generate Minecraft structures"
    )
    @CommandPermissions("worldedit.generation.structure")
    @Logging(POSITION)
    public int structure(Actor actor, LocalSession session, EditSession editSession,
                        @Arg(desc = "The structure")
                        StructureType feature,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        if (editSession.getWorld().generateStructure(feature, editSession, CrosshairPlacement.placement(session, actor, atCrosshair))) {
            actor.printInfo(TranslatableComponent.of("worldedit.structure.created"));
        } else {
            actor.printError(TranslatableComponent.of("worldedit.structure.failed"));
        }
        return 0;
    }

    @Command(
        name = "/hpyramid",
        desc = "Generate a hollow pyramid"
    )
    @CommandPermissions("worldedit.generation.pyramid")
    @Logging(PLACEMENT)
    public int hollowPyramid(Actor actor, LocalSession session, EditSession editSession,
                             @Arg(desc = "The pattern of blocks to set")
                                 Pattern pattern,
                             @Arg(desc = "The size of the pyramid")
                                 int size,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        return pyramid(actor, session, editSession, pattern, size, true, atCrosshair);
    }

    @Command(
        name = "/pyramid",
        desc = "Generate a filled pyramid"
    )
    @CommandPermissions("worldedit.generation.pyramid")
    @Logging(PLACEMENT)
    public int pyramid(Actor actor, LocalSession session, EditSession editSession,
                       @Arg(desc = "The pattern of blocks to set")
                           Pattern pattern,
                       @Arg(desc = "The size of the pyramid")
                           int size,
                       @Switch(name = 'h', desc = "Make a hollow pyramid")
                           boolean hollow,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        worldEdit.checkMaxRadius(size);
        BlockVector3 pos = CrosshairPlacement.placement(session, actor, atCrosshair);
        int affected = editSession.makePyramid(pos, pattern, size, !hollow);
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TranslatableComponent.of("worldedit.pyramid.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/generate",
        aliases = { "/gen", "/g" },
        desc = "Generates a shape according to a formula.",
        descFooter = "For details, see https://ehub.to/we/expr"
    )
    @CommandPermissions("worldedit.generation.shape")
    @Logging(ALL)
    public int generate(Actor actor, LocalSession session, EditSession editSession,
                        @Selection Region region,
                        @Arg(desc = "The pattern of blocks to set")
                            Pattern pattern,
                        @Arg(desc = "Expression to test block placement locations and set block type", variable = true)
                            List<String> expression,
                        @Switch(name = 'h', desc = "Generate a hollow shape")
                            boolean hollow,
                        @Switch(name = 'r', desc = "Use the game's coordinate origin")
                            boolean useRawCoords,
                        @Switch(name = 'o', desc = "Use the placement's coordinate origin")
                            boolean offsetPlacement,
                        @Switch(name = 'c', desc = "Use the selection's center as origin")
                            boolean offsetCenter) throws WorldEditException {
        final Transform transform = TransformUtil.createTransformForExpressionCommand(actor, session, region, useRawCoords, offsetPlacement, offsetCenter);

        try {
            final int affected = editSession.makeShape(region, transform, pattern, String.join(" ", expression), hollow, session.getTimeout());
            if (actor instanceof Player player) {
                player.findFreePosition();
            }
            actor.printInfo(TranslatableComponent.of("worldedit.generate.created", TextComponent.of(affected)));
            return affected;
        } catch (ExpressionException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
    }

    @Command(
        name = "/generatebiome",
        aliases = { "/genbiome", "/gb" },
        desc = "Sets biome according to a formula.",
        descFooter = "For details, see https://ehub.to/we/expr"
    )
    @CommandPermissions("worldedit.generation.shape.biome")
    @Logging(ALL)
    public int generateBiome(Actor actor, LocalSession session, EditSession editSession,
                             @Selection Region region,
                             @Arg(desc = "The biome type to set")
                                 BiomeType target,
                             @Arg(desc = "Expression to test block placement locations and set biome type", variable = true)
                                 List<String> expression,
                             @Switch(name = 'h', desc = "Generate a hollow shape")
                                 boolean hollow,
                             @Switch(name = 'r', desc = "Use the game's coordinate origin")
                                 boolean useRawCoords,
                             @Switch(name = 'o', desc = "Use the placement's coordinate origin")
                                 boolean offsetPlacement,
                             @Switch(name = 'c', desc = "Use the selection's center as origin")
                                 boolean offsetCenter) throws WorldEditException {
        final Transform transform = TransformUtil.createTransformForExpressionCommand(actor, session, region, useRawCoords, offsetPlacement, offsetCenter);

        try {
            final int affected = editSession.makeBiomeShape(region, transform, target, String.join(" ", expression), hollow, session.getTimeout());
            actor.printInfo(TranslatableComponent.of("worldedit.generatebiome.changed", TextComponent.of(affected)));
            return affected;
        } catch (ExpressionException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
    }

}
