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
import com.sk89q.worldedit.command.argument.HintedArguments;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.CrosshairPlacement;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.command.util.StyleLibraryHolder;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.generator.NamedSettings;
import com.sk89q.worldedit.function.generator.parts.FacingPlane;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.generator.skyscraper.Skyscraper;
import com.sk89q.worldedit.function.generator.style.BuildingStyle;
import com.sk89q.worldedit.function.generator.style.StyleRole;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.Switch;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;

/**
 * Builds skyscrapers in a named building style.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class SkyscraperCommands {

    private final WorldEdit worldEdit;

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public SkyscraperCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
        this.worldEdit = worldEdit;
    }

    /**
     * Turn a style's blocks into patterns.
     *
     * @throws InputParseException if a block isn't valid
     */
    static Map<StyleRole, Pattern> patterns(BuildingStyle style, Actor actor, World world, LocalSession session,
                                            EditSession editSession) throws InputParseException {
        ParserContext context = new ParserContext();
        context.setActor(actor);
        context.setWorld(world);
        context.setExtent(editSession);
        context.setSession(session);
        Map<StyleRole, Pattern> patterns = new EnumMap<>(StyleRole.class);
        for (StyleRole role : StyleRole.values()) {
            try {
                patterns.put(role, WorldEdit.getInstance().getPatternFactory().parseFromInput(style.block(role),
                    context));
            } catch (InputParseException e) {
                throw new InputParseException(TextComponent.of("Bad block for " + role.id() + " ('"
                    + style.block(role) + "'): ").append(e.getRichMessage()), e);
            }
        }
        return patterns;
    }

    /**
     * Thrown out of the block sink to stop building.
     */
    private static final class TooManyBlocks extends RuntimeException {
        private TooManyBlocks(MaxChangedBlocksException cause) {
            super(cause);
        }
    }

    @Command(
        name = "/skyscraper",
        aliases = { "/highrise" },
        desc = "Builds a skyscraper in a building style, its entrance facing you",
        descFooter = "Styles: modern, art_deco, futuristic, gothic, brutalist, glass, brick, sandstone, or your own "
            + "(//style list). Settings are plain numbers, in order or as name=value: width 15, depth 15, story 4 "
            + "(blocks per story), setbacks, taper, shape (box, rounded, round, l), crown (flat, spire, stepped, "
            + "pyramid), seed, interior (1 = floors and stairwell, 0 = floor slabs only). Change any block with "
            + "role=block, e.g. window=cyan_stained_glass. The front wall is 3 blocks in front of the placement "
            + "position. Example: //skyscraper art_deco 40 width=25 depth=25 crown=stepped"
    )
    @CommandPermissions("worldedit.generation.skyscraper")
    @Logging(PLACEMENT)
    public int skyscraper(Actor actor, World world, LocalSession session, EditSession editSession,
                          @Arg(desc = "The building style (see //style list)")
                              HintedArguments.StyleName styleArg,
                          @Arg(desc = "The number of stories")
                              int stories,
                          @Arg(desc = "Settings (name=value or numbers in order) and role=block changes",
                              def = "", variable = true)
                              List<HintedArguments.SettingWord> settingWords,
                       @Switch(name = 'c', desc = CrosshairPlacement.FLAG_DESCRIPTION)
                           boolean atCrosshair) throws WorldEditException {
        String styleName = styleArg.value();
        List<String> settings = HintedArguments.strings(settingWords, HintedArguments.SettingWord::value);
        BuildingStyle style = StyleLibraryHolder.get().find(styleName).orElse(null);
        if (style == null) {
            actor.printError(TextComponent.of("Unknown style '" + styleName + "'. Use //style list to see them."));
            return 0;
        }
        if (stories < 1 || stories > Skyscraper.MAX_STORIES) {
            actor.printError(TextComponent.of("Stories must be between 1 and " + Skyscraper.MAX_STORIES));
            return 0;
        }
        atCrosshair |= CrosshairPlacement.hasFlag(settings, 'c');
        Skyscraper skyscraper;
        try {
            BuildingStyle.Overrides overrides = BuildingStyle.splitOverrides(CrosshairPlacement.withoutFlag(settings, 'c'));
            style = style.withOverrides(overrides.overrides());
            List<String> numbers = NamedSettings.replaceNames(overrides.remaining(), "shape",
                Skyscraper.SHAPE_NAMES);
            numbers = NamedSettings.replaceNames(numbers, "crown", Skyscraper.CROWN_NAMES);
            ShapeParameters parameters = ShapeParameters.parse(Skyscraper.PARAMETERS, numbers);
            skyscraper = new Skyscraper(style.family(), stories, parameters,
                ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE));
        } catch (IllegalArgumentException e) {
            actor.printError(TextComponent.of(e.getMessage()));
            return 0;
        }
        worldEdit.checkMaxRadius(Math.max(skyscraper.width(), skyscraper.depth()) / 2.0);

        BlockVector3 origin = CrosshairPlacement.placement(session, actor, atCrosshair);
        int top = origin.y() + skyscraper.roofY();
        if (top > world.getMaxY()) {
            actor.printError(TextComponent.of("That would be " + skyscraper.roofY() + " blocks tall, but the world "
                + "ends at y=" + world.getMaxY() + ". Use fewer stories or start lower."));
            return 0;
        }
        Map<StyleRole, Pattern> patterns = patterns(style, actor, world, session, editSession);
        Direction entrance = actor instanceof Player player
            ? FacingPlane.rightOf(FacingPlane.rightOf(FacingPlane.cardinalFromYaw(player.getLocation().getYaw())))
            : Direction.NORTH;
        BlockState air = BlockTypes.AIR.getDefaultState();
        int width = skyscraper.width();
        int[] changed = new int[1];
        try {
            skyscraper.generate(new Skyscraper.Sink() {
                @Override
                public void block(int x, int y, int z, StyleRole role) {
                    BlockVector3 position = origin.add(Skyscraper.toWorld(entrance, width, x, y, z));
                    try {
                        if (editSession.setBlock(position, patterns.get(role))) {
                            changed[0]++;
                        }
                    } catch (MaxChangedBlocksException e) {
                        throw new TooManyBlocks(e);
                    }
                }

                @Override
                public void air(int x, int y, int z) {
                    BlockVector3 position = origin.add(Skyscraper.toWorld(entrance, width, x, y, z));
                    try {
                        if (editSession.setBlock(position, air)) {
                            changed[0]++;
                        }
                    } catch (MaxChangedBlocksException e) {
                        throw new TooManyBlocks(e);
                    }
                }
            });
        } catch (TooManyBlocks e) {
            throw (MaxChangedBlocksException) e.getCause();
        }
        if (actor instanceof Player player) {
            player.findFreePosition();
        }
        actor.printInfo(TextComponent.of("Built a " + stories + "-story " + style.name() + " skyscraper ("
            + Skyscraper.SHAPE_NAMES.get(skyscraper.shape()) + ", " + Skyscraper.CROWN_NAMES.get(skyscraper.crown())
            + " crown, seed " + skyscraper.seed() + ", entrance facing "
            + entrance.name().toLowerCase(Locale.ROOT) + "): " + changed[0] + " blocks changed."));
        return changed[0];
    }
}
