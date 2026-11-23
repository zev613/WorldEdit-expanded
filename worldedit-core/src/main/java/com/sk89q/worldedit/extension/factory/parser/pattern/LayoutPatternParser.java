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

package com.sk89q.worldedit.extension.factory.parser.pattern;

import com.google.common.base.CharMatcher;
import com.sk89q.util.StringUtil;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.generator.shape.ShapeAxis;
import com.sk89q.worldedit.function.generator.shape.ShapeParameter;
import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.function.pattern.LayoutPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.PatternLayouts;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.text.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Parser for named layout patterns that mix two or more block patterns without formulas:
 * {@code #stripes}, {@code #checker}, {@code #rings}, {@code #spiral}, {@code #diagonal},
 * {@code #bricks} and {@code #dither}.
 *
 * <p>Inside the brackets, block patterns and settings can be mixed: numbers fill the settings in
 * order, {@code name=value} sets one by name, and {@code x}, {@code y} or {@code z} set the axis.
 * Everything else is a block pattern, e.g. {@code #stripes[stone,oak_planks,3,x]}.</p>
 */
public class LayoutPatternParser extends InputParser<Pattern> {

    /**
     * Where the pattern is used, for layouts that need a centre or a range.
     *
     * @param selection the player's selection, if there is one
     * @param center the centre for rings and spirals
     */
    record Place(@Nullable Region selection, Vector3 center) {
    }

    @FunctionalInterface
    interface Builder {
        PatternLayouts.Layout build(ShapeParameters values, int patternCount, Place place) throws InputParseException;
    }

    /**
     * A named layout.
     *
     * @param name the name, used as {@code #name[...]}
     * @param aliases other names
     * @param example an example
     * @param minPatterns the fewest block patterns
     * @param maxPatterns the most block patterns
     * @param parameters the settings
     * @param builder builds the layout
     */
    record Definition(String name, List<String> aliases, String example, int minPatterns, int maxPatterns,
                      List<ShapeParameter> parameters, Builder builder) {
    }

    private static final int MAX_PATTERNS = 16;

    static final List<Definition> DEFINITIONS = List.of(
        new Definition("stripes", List.of("stripe", "layers"), "#stripes[white_wool,red_wool,2,y]", 2, MAX_PATTERNS,
            List.of(
                new ShapeParameter("width", 1, 1, 1000, "Thickness of each stripe"),
                ShapeAxis.parameter(ShapeAxis.Y, "The axis the stripes change along (y = flat layers)")),
            (values, _, _) -> PatternLayouts.stripes(values.getInt("width"), axis(values, ShapeAxis.Y))),
        new Definition("checker", List.of("checkers", "checkerboard"), "#checker[black_concrete,white_concrete,2]",
            2, MAX_PATTERNS,
            List.of(new ShapeParameter("size", 1, 1, 1000, "Size of each square")),
            (values, _, _) -> PatternLayouts.checker(values.getInt("size"))),
        new Definition("rings", List.of("ring", "circles"), "#rings[stone,andesite,2]", 2, MAX_PATTERNS,
            List.of(
                new ShapeParameter("width", 2, 1, 1000, "Width of each ring"),
                ShapeAxis.parameter(ShapeAxis.Y, "The axis the rings go around (y = rings seen from above)")),
            (values, _, place) -> PatternLayouts.rings(place.center(), values.getInt("width"),
                axis(values, ShapeAxis.Y))),
        new Definition("spiral", List.of("swirl"), "#spiral[white_wool,black_wool,3]", 2, MAX_PATTERNS,
            List.of(
                new ShapeParameter("arms", 1, 1, 64, "Arms for each block pattern"),
                new ShapeParameter("curl", 16, 0, 1000,
                    "Blocks an arm moves outwards in one full turn (0 = straight spokes)"),
                ShapeAxis.parameter(ShapeAxis.Y, "The axis the spiral goes around (y = seen from above)")),
            (values, count, place) -> PatternLayouts.spiral(place.center(), values.getInt("arms") * count,
                values.get("curl"), axis(values, ShapeAxis.Y))),
        new Definition("diagonal", List.of("diagonals", "diag"), "#diagonal[yellow_concrete,black_concrete,2]",
            2, MAX_PATTERNS,
            List.of(new ShapeParameter("width", 1, 1, 1000, "Thickness of each stripe")),
            (values, _, _) -> PatternLayouts.diagonal(values.getInt("width"))),
        new Definition("bricks", List.of("brick", "brickwork"), "#bricks[bricks,smooth_stone,3,1]", 2, 2,
            List.of(
                new ShapeParameter("length", 3, 1, 64, "Length of each brick"),
                new ShapeParameter("height", 1, 1, 64, "Height of each row of bricks"),
                ShapeAxis.parameter(ShapeAxis.Y, "The axis the rows stack along (y for walls; x or z to pave a floor)")),
            (values, _, _) -> PatternLayouts.bricks(values.getInt("length"), values.getInt("height"),
                axis(values, ShapeAxis.Y))),
        new Definition("dither", List.of("blend"), "#dither[stone,grass_block,y]", 2, 2,
            List.of(
                new ShapeParameter("mix", -1, -1, 1,
                    "Share of the second pattern from 0 to 1; leave out to blend across the selection"),
                ShapeAxis.parameter(ShapeAxis.Y, "The axis to blend along across the selection")),
            (values, _, place) -> {
                double mix = values.get("mix");
                if (mix >= 0) {
                    return PatternLayouts.dither(mix);
                }
                Region selection = place.selection();
                if (selection == null) {
                    throw new InputParseException(TextComponent.of(
                        "#dither needs a selection to blend across, or a mix such as #dither[stone,dirt,0.3]"));
                }
                int axis = axis(values, ShapeAxis.Y);
                return PatternLayouts.ditherBlend(axis,
                    PatternLayouts.along(selection.getMinimumPoint().toVector3(), axis),
                    PatternLayouts.along(selection.getMaximumPoint().toVector3(), axis));
            })
    );

    public LayoutPatternParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    private static int axis(ShapeParameters values, int fallback) {
        int axis = values.getInt(ShapeAxis.NAME);
        return axis == ShapeAxis.AUTO ? fallback : axis;
    }

    @Nullable
    static Definition find(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (Definition definition : DEFINITIONS) {
            if (definition.name().equals(lower) || definition.aliases().contains(lower)) {
                return definition;
            }
        }
        return null;
    }

    @Override
    public Stream<String> getSuggestions(String input, ParserContext context) {
        if (input.isEmpty() || "#".startsWith(input)) {
            return DEFINITIONS.stream().map(d -> "#" + d.name());
        }
        String lower = input.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("#") || lower.contains("[")) {
            return Stream.empty();
        }
        return DEFINITIONS.stream()
            .filter(d -> ("#" + d.name()).startsWith(lower))
            .map(Definition::example);
    }

    @Override
    public Pattern parseFromInput(String input, ParserContext context) throws InputParseException {
        if (!input.startsWith("#") || input.startsWith("##")) {
            return null;
        }
        int bracket = input.indexOf('[');
        String name = (bracket < 0 ? input.substring(1) : input.substring(1, bracket)).trim();
        Definition definition = find(name);
        if (definition == null) {
            return null;
        }
        List<String> groups = bracket < 0 ? null : GradientPatternParser.bracketGroups(input, bracket);
        if (groups == null || groups.size() != 1) {
            throw new InputParseException(TextComponent.of("Expected #" + definition.name()
                + "[patterns and settings], e.g. " + definition.example()));
        }

        List<String> patternTokens = new ArrayList<>();
        List<String> settingTokens = new ArrayList<>();
        for (String token : StringUtil.splitOutsideBrackets(groups.get(0), CharMatcher.is(','))) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (isSetting(token, definition.parameters())) {
                settingTokens.add(token);
            } else {
                patternTokens.add(token);
            }
        }
        if (patternTokens.size() < definition.minPatterns() || patternTokens.size() > definition.maxPatterns()) {
            String count = definition.minPatterns() == definition.maxPatterns()
                ? Integer.toString(definition.minPatterns())
                : definition.minPatterns() + " to " + definition.maxPatterns();
            throw new InputParseException(TextComponent.of("#" + definition.name() + " needs " + count
                + " block patterns, e.g. " + definition.example()));
        }

        ShapeParameters values;
        try {
            values = ShapeParameters.parse(definition.parameters(),
                ShapeAxis.normalize(definition.parameters(), settingTokens));
        } catch (IllegalArgumentException e) {
            throw new InputParseException(TextComponent.of("#" + definition.name() + ": " + e.getMessage()));
        }

        List<Pattern> patterns = new ArrayList<>();
        for (String token : patternTokens) {
            patterns.add(worldEdit.getPatternFactory().parseFromInput(token, context));
        }
        PatternLayouts.Layout layout = definition.builder().build(values, patterns.size(), place(context));
        return new LayoutPattern(layout, patterns);
    }

    /**
     * Whether a token inside the brackets is a setting rather than a block pattern.
     *
     * @param token the token
     * @param parameters the settings of the layout
     * @return true for numbers, axis words and name=value settings
     */
    static boolean isSetting(String token, List<ShapeParameter> parameters) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException _) {
            // not a number
        }
        boolean hasAxis = parameters.stream().anyMatch(p -> p.name().equals(ShapeAxis.NAME));
        if (hasAxis && ShapeAxis.isWord(token)) {
            return true;
        }
        int eq = token.indexOf('=');
        if (eq > 0) {
            String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            return parameters.stream().anyMatch(p -> p.name().equals(key));
        }
        return false;
    }

    private static Place place(ParserContext context) {
        LocalSession session = context.getSession();
        Region selection = null;
        if (session != null) {
            try {
                selection = session.getSelection();
            } catch (IncompleteRegionException _) {
                // no selection; fall back to the placement position below
            }
        }
        if (selection != null) {
            return new Place(selection, selection.getCenter());
        }
        if (session != null && context.getActor() != null) {
            try {
                return new Place(null, session.getPlacementPosition(context.getActor()).toVector3());
            } catch (IncompleteRegionException _) {
                // fall through to the origin
            }
        }
        return new Place(null, Vector3.ZERO);
    }
}
