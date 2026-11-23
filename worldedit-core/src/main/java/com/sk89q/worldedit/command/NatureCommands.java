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
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.CrosshairPlacement;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.NamedSettings;
import com.sk89q.worldedit.function.generator.nature.CoralGenerator;
import com.sk89q.worldedit.function.generator.nature.CrystalGenerator;
import com.sk89q.worldedit.function.generator.nature.GiantMushroomGenerator;
import com.sk89q.worldedit.function.generator.nature.NatureModel;
import com.sk89q.worldedit.function.generator.nature.NatureSize;
import com.sk89q.worldedit.function.generator.nature.RockGenerator;
import com.sk89q.worldedit.function.generator.nature.RootGenerator;
import com.sk89q.worldedit.function.generator.shape.ShapeFlags;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Radii;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;

/**
 * Natural objects: rocks, crystals, giant mushrooms, roots and coral.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class NatureCommands {

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public NatureCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    /**
     * The settings and size of one use of a nature command.
     */
    private record Request(NatureSize size, ShapeParameters parameters, long seed) {
    }

    /**
     * Read the size and settings, printing a message and returning null if they are wrong.
     */
    private Request request(Actor actor, List<Double> size, List<String> settings, List<ShapeParameter> parameters,
                            String typeSetting, List<String> typeNames) throws WorldEditException {
        NatureSize natureSize;
        ShapeParameters values;
        try {
            natureSize = NatureSize.of(size);
            List<String> words = typeNames == null ? settings
                : NamedSettings.replaceNames(settings, typeSetting, typeNames);
            values = ShapeParameters.parse(parameters, words);
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return null;
        }
        worldEdit.checkMaxRadius(natureSize.max());
        long seed = Math.round(values.get("seed"));
        if (seed < 0) {
            seed = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
        }
        return new Request(natureSize, values, seed);
    }

    private static Pattern parsePattern(String input, Actor actor, World world, LocalSession session,
                                        EditSession editSession) throws WorldEditException {
        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(world);
        context.setExtent(editSession);
        context.setSession(session);
        return WorldEdit.getInstance().getPatternFactory().parseFromInput(input, context);
    }

    private static int place(Actor actor, LocalSession session, EditSession editSession, NatureModel model,
                             Map<Integer, Pattern> patterns, String what, long seed, boolean atCrosshair)
            throws WorldEditException {
        BlockVector3 origin = CrosshairPlacement.placement(session, actor, atCrosshair);
        int[] changed = new int[1];
        MaxChangedBlocksException[] failure = new MaxChangedBlocksException[1];
        model.forEach((x, y, z, part) -> {
            Pattern pattern = patterns.get(part);
            if (pattern == null || failure[0] != null) {
                return;
            }
            try {
                if (editSession.setBlock(origin.add(x, y, z), pattern)) {
                    changed[0]++;
                }
            } catch (MaxChangedBlocksException e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TextComponent.of("Generated " + what + " (seed " + seed + "): " + changed[0]
            + " blocks changed."));
        return changed[0];
    }

    @Command(
        name = "/rock",
        aliases = { "/boulder" },
        desc = "Generates a boulder or rock formation standing on the placement position",
        descFooter = "The size is the radius, or x,y,z radii (it is up to twice as tall as the y radius). "
            + "Mix blocks for a natural look, e.g. //rock stone,andesite,cobblestone 6 roughness=0.8. "
            + "The same seed gives the same rock."
    )
    @CommandPermissions("worldedit.generation.rock")
    @Logging(PLACEMENT)
    public int rock(Actor actor, World world, LocalSession session, EditSession editSession,
                    @Arg(desc = "The pattern of blocks to build with")
                        Pattern pattern,
                    @Arg(desc = "The radius, or x,y,z radii")
                    @Radii(3)
                        List<Double> size,
                    @Arg(desc = "Settings: roughness (0-1), seed; numbers in order or name=value",
                        def = "", variable = true)
                        List<String> settings,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        atCrosshair |= CrosshairPlacement.hasFlag(settings, 'c');
        Request request = request(actor, size, CrosshairPlacement.withoutFlag(settings, 'c'), RockGenerator.PARAMETERS, null, null);
        if (request == null) {
            return 0;
        }
        NatureModel model = RockGenerator.generate(request.size(), request.parameters(), request.seed());
        return place(actor, session, editSession, model, Map.of(RockGenerator.ROCK, pattern),
            "a rock " + request.size(), request.seed(), atCrosshair);
    }

    @Command(
        name = "/crystals",
        aliases = { "/crystal", "/geode" },
        desc = "Generates a cluster of pointed hexagonal crystals",
        descFooter = "The size is the spread, or x,y,z (the tallest crystal is about twice the y size). "
            + "Example: //crystals amethyst_block,purple_stained_glass 6 count=9"
    )
    @CommandPermissions("worldedit.generation.crystals")
    @Logging(PLACEMENT)
    public int crystals(Actor actor, World world, LocalSession session, EditSession editSession,
                        @Arg(desc = "The pattern of blocks to build with")
                            Pattern pattern,
                        @Arg(desc = "The spread, or x,y,z")
                        @Radii(3)
                            List<Double> size,
                        @Arg(desc = "Settings: count, seed; numbers in order or name=value",
                            def = "", variable = true)
                            List<String> settings,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        atCrosshair |= CrosshairPlacement.hasFlag(settings, 'c');
        Request request = request(actor, size, CrosshairPlacement.withoutFlag(settings, 'c'), CrystalGenerator.PARAMETERS, null, null);
        if (request == null) {
            return 0;
        }
        NatureModel model = CrystalGenerator.generate(request.size(), request.parameters(), request.seed());
        return place(actor, session, editSession, model, Map.of(CrystalGenerator.CRYSTAL, pattern),
            "a crystal cluster " + request.size(), request.seed(), atCrosshair);
    }

    @Command(
        name = "/giantmushroom",
        aliases = { "/bigmushroom", "/mushroom" },
        desc = "Generates a giant mushroom: a curved stem under a round, flat or bell cap",
        descFooter = "The size is the cap's radius, or x,y,z (the mushroom is twice the y size tall). "
            + "Types: round, flat, bell. -u <pattern> fills the underside (gills) of the cap. "
            + "Example: //giantmushroom mushroom_stem red_mushroom_block 8 round -u white_wool"
    )
    @CommandPermissions("worldedit.generation.giantmushroom")
    @Logging(PLACEMENT)
    public int giantMushroom(Actor actor, World world, LocalSession session, EditSession editSession,
                             @Arg(desc = "The pattern for the stem")
                                 Pattern stem,
                             @Arg(desc = "The pattern for the cap")
                                 Pattern cap,
                             @Arg(desc = "The cap radius, or x,y,z")
                             @Radii(3)
                                 List<Double> size,
                             @Arg(desc = "Settings: type (round, flat, bell), seed", def = "", variable = true)
                                 List<String> settings,
                             @ArgFlag(name = 'u', desc = "The pattern for the underside of the cap")
                                 String undersideInput,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        String undersideText;
        List<String> remaining;
        try {
            // flags typed after the settings end up in the settings list, so pick them out here
            ShapeFlags flags = ShapeFlags.parse(settings, "c", "", "u");
            atCrosshair |= flags.has('c');
            undersideText = flags.text('u', undersideInput == null ? "" : undersideInput);
            remaining = flags.remaining();
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        Request request = request(actor, size, remaining, GiantMushroomGenerator.PARAMETERS, "type",
            GiantMushroomGenerator.TYPES);
        if (request == null) {
            return 0;
        }
        Pattern underside = undersideText.isEmpty() ? null
            : parsePattern(undersideText, actor, world, session, editSession);
        NatureModel model = GiantMushroomGenerator.generate(request.size(), request.parameters(), underside != null,
            request.seed());
        Map<Integer, Pattern> patterns = underside == null
            ? Map.of(GiantMushroomGenerator.STEM, stem, GiantMushroomGenerator.CAP, cap)
            : Map.of(GiantMushroomGenerator.STEM, stem, GiantMushroomGenerator.CAP, cap,
                GiantMushroomGenerator.UNDERSIDE, underside);
        String type = GiantMushroomGenerator.TYPES.get(request.parameters().getInt("type")).split("\\|", -1)[0];
        return place(actor, session, editSession, model, patterns, "a " + type + " giant mushroom " + request.size(),
            request.seed(), atCrosshair);
    }

    @Command(
        name = "/roots",
        aliases = { "/treeroots" },
        desc = "Generates twisting roots spreading out and down from the placement position",
        descFooter = "The size is how far they spread, or x,y,z (they reach down to twice the y size). "
            + "Good under trees and floating islands. Example: //roots oak_wood,rooted_dirt 8 count=7"
    )
    @CommandPermissions("worldedit.generation.roots")
    @Logging(PLACEMENT)
    public int roots(Actor actor, World world, LocalSession session, EditSession editSession,
                     @Arg(desc = "The pattern of blocks to build with")
                         Pattern pattern,
                     @Arg(desc = "The spread, or x,y,z")
                     @Radii(3)
                         List<Double> size,
                     @Arg(desc = "Settings: count, seed; numbers in order or name=value", def = "", variable = true)
                         List<String> settings,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        atCrosshair |= CrosshairPlacement.hasFlag(settings, 'c');
        Request request = request(actor, size, CrosshairPlacement.withoutFlag(settings, 'c'), RootGenerator.PARAMETERS, null, null);
        if (request == null) {
            return 0;
        }
        NatureModel model = RootGenerator.generate(request.size(), request.parameters(), request.seed());
        return place(actor, session, editSession, model, Map.of(RootGenerator.ROOT, pattern),
            "roots " + request.size(), request.seed(), atCrosshair);
    }

    @Command(
        name = "/coral",
        aliases = { "/reef" },
        desc = "Generates coral: branching, brain, fan or tubes",
        descFooter = "The size is the spread, or x,y,z (up to twice the y size tall). "
            + "Example: //coral tube_coral_block 6 branching, //coral brain_coral_block 4 brain"
    )
    @CommandPermissions("worldedit.generation.coral")
    @Logging(PLACEMENT)
    public int coral(Actor actor, World world, LocalSession session, EditSession editSession,
                     @Arg(desc = "The pattern of blocks to build with")
                         Pattern pattern,
                     @Arg(desc = "The spread, or x,y,z")
                     @Radii(3)
                         List<Double> size,
                     @Arg(desc = "Settings: type (branching, brain, fan, tubes), seed", def = "", variable = true)
                         List<String> settings,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        atCrosshair |= CrosshairPlacement.hasFlag(settings, 'c');
        Request request = request(actor, size, CrosshairPlacement.withoutFlag(settings, 'c'), CoralGenerator.PARAMETERS, "type", CoralGenerator.TYPES);
        if (request == null) {
            return 0;
        }
        NatureModel model = CoralGenerator.generate(request.size(), request.parameters(), request.seed());
        String type = CoralGenerator.TYPES.get(request.parameters().getInt("type")).split("\\|", -1)[0];
        return place(actor, session, editSession, model, Map.of(CoralGenerator.CORAL, pattern),
            type + " coral " + request.size(), request.seed(), atCrosshair);
    }
}
