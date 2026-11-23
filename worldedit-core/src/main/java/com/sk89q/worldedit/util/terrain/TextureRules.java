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

package com.sk89q.worldedit.util.terrain;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.sk89q.util.StringUtil;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.util.formatting.text.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * An ordered list of texture rules, as used by {@code //texture}.
 *
 * <p>The input syntax is a comma-separated list of rules. Each rule is either
 * {@code pattern@condition&condition...} or a bare {@code pattern} that always
 * matches. A condition is {@code <variable><op><number>} where variable is one
 * of {@code y}, {@code slope} (degrees) or {@code noise} (0..1) and op is one of
 * {@code >}, {@code <}, {@code >=} or {@code <=}.</p>
 *
 * @param <T> the type of the parsed pattern
 */
public final class TextureRules<T> {

    /**
     * Parses the pattern part of a rule.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface PatternParser<T> {
        T parse(String input) throws InputParseException;
    }

    /**
     * A variable a condition can test.
     */
    public enum Variable {
        Y, SLOPE, NOISE
    }

    /**
     * A single comparison, e.g. {@code slope>45}.
     *
     * @param variable the tested variable
     * @param greater true for {@code >}/{@code >=}, false for {@code <}/{@code <=}
     * @param inclusive true if equality also matches
     * @param threshold the value compared against
     */
    public record Condition(Variable variable, boolean greater, boolean inclusive, double threshold) {

        /**
         * Test this condition against column values.
         *
         * @param y the surface Y
         * @param slope the slope in degrees
         * @param noise the noise value in [0, 1]
         * @return true if the condition holds
         */
        public boolean test(double y, double slope, double noise) {
            double value = switch (variable) {
                case Y -> y;
                case SLOPE -> slope;
                case NOISE -> noise;
            };
            if (inclusive && value == threshold) {
                return true;
            }
            return greater ? value > threshold : value < threshold;
        }
    }

    /**
     * A pattern with the conditions that must all hold for it to apply.
     *
     * @param pattern the pattern
     * @param conditions the conditions (empty = always matches)
     * @param <T> the pattern type
     */
    public record Rule<T>(T pattern, List<Condition> conditions) {

        /**
         * Test whether all conditions hold.
         *
         * @param y the surface Y
         * @param slope the slope in degrees
         * @param noise the noise value in [0, 1]
         * @return true if the rule matches
         */
        public boolean matches(double y, double slope, double noise) {
            for (Condition condition : conditions) {
                if (!condition.test(y, slope, noise)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final java.util.regex.Pattern CONDITION_REGEX =
        java.util.regex.Pattern.compile("^\\s*([a-zA-Z]+)\\s*(>=|<=|>|<)\\s*(-?[0-9]+(?:\\.[0-9]+)?|-?\\.[0-9]+)\\s*$");

    private final List<Rule<T>> rules;

    private TextureRules(List<Rule<T>> rules) {
        this.rules = ImmutableList.copyOf(rules);
    }

    /**
     * Get the parsed rules, in order.
     *
     * @return the rules
     */
    public List<Rule<T>> getRules() {
        return rules;
    }

    /**
     * Whether any rule uses the given variable.
     *
     * @param variable the variable
     * @return true if it is used
     */
    public boolean uses(Variable variable) {
        for (Rule<T> rule : rules) {
            for (Condition condition : rule.conditions()) {
                if (condition.variable() == variable) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find the pattern of the first matching rule.
     *
     * @param y the surface Y
     * @param slope the slope in degrees
     * @param noise the noise value in [0, 1]
     * @return the pattern, or null if no rule matches
     */
    public T select(double y, double slope, double noise) {
        for (Rule<T> rule : rules) {
            if (rule.matches(y, slope, noise)) {
                return rule.pattern();
            }
        }
        return null;
    }

    /**
     * Parse a rule list.
     *
     * @param input the user input
     * @param patternParser the parser for the pattern part of each rule
     * @param <T> the pattern type
     * @return the parsed rules
     * @throws InputParseException if the input is invalid
     */
    public static <T> TextureRules<T> parse(String input, PatternParser<T> patternParser) throws InputParseException {
        List<Rule<T>> rules = new ArrayList<>();
        for (String rawRule : StringUtil.splitOutsideBrackets(input, CharMatcher.is(','))) {
            String ruleText = rawRule.trim();
            if (ruleText.isEmpty()) {
                continue;
            }
            int at = findConditionSeparator(ruleText);
            String patternText = at < 0 ? ruleText : ruleText.substring(0, at).trim();
            if (patternText.isEmpty()) {
                throw new InputParseException(TextComponent.of("Texture rule '" + ruleText + "' has no pattern"));
            }
            List<Condition> conditions = new ArrayList<>();
            if (at >= 0) {
                String conditionText = ruleText.substring(at + 1);
                for (String rawCondition : conditionText.split("&", -1)) {
                    conditions.add(parseCondition(rawCondition, ruleText));
                }
            }
            rules.add(new Rule<>(patternParser.parse(patternText), ImmutableList.copyOf(conditions)));
        }
        if (rules.isEmpty()) {
            throw new InputParseException(TextComponent.of("At least one texture rule must be given"));
        }
        return new TextureRules<>(rules);
    }

    /**
     * Find the index of the '@' separating pattern and conditions, ignoring any inside brackets
     * and any directly followed by '[' (which is part of a pattern such as {@code #clipboard@[1,0,0]}).
     */
    private static int findConditionSeparator(String rule) {
        int depth = 0;
        int found = -1;
        for (int i = 0; i < rule.length(); i++) {
            char c = rule.charAt(i);
            if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                depth = Math.max(0, depth - 1);
            } else if (c == '@' && depth == 0) {
                boolean bracketFollows = i + 1 < rule.length() && rule.charAt(i + 1) == '[';
                if (!bracketFollows) {
                    found = i;
                }
            }
        }
        return found;
    }

    /**
     * Parse a single condition such as {@code slope>45}.
     *
     * @param input the condition text
     * @param rule the full rule text, for error messages
     * @return the condition
     * @throws InputParseException if the condition is invalid
     */
    static Condition parseCondition(String input, String rule) throws InputParseException {
        Matcher matcher = CONDITION_REGEX.matcher(input);
        if (!matcher.matches()) {
            throw new InputParseException(TextComponent.of("Invalid texture condition '" + input.trim()
                + "' in rule '" + rule + "'. Expected e.g. y>64, y<100, slope>45, slope<20, noise>0.5"));
        }
        Variable variable = switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
            case "y" -> Variable.Y;
            case "slope" -> Variable.SLOPE;
            case "noise" -> Variable.NOISE;
            default -> throw new InputParseException(TextComponent.of("Unknown texture condition variable '"
                + matcher.group(1) + "' in rule '" + rule + "'. Use y, slope or noise"));
        };
        String op = matcher.group(2);
        double threshold = Double.parseDouble(matcher.group(3));
        if (variable == Variable.NOISE && (threshold < 0 || threshold > 1)) {
            throw new InputParseException(TextComponent.of("Noise goes from 0 to 1, so '" + input.trim()
                + "' in rule '" + rule + "' can never match. Use a number from 0 to 1, e.g. noise>0.6"));
        }
        if (variable == Variable.SLOPE && (threshold < 0 || threshold > 90)) {
            throw new InputParseException(TextComponent.of("Slope is in degrees from 0 (flat) to 90 (a cliff), so '"
                + input.trim() + "' in rule '" + rule + "' can never match. E.g. slope>45"));
        }
        return new Condition(variable, op.charAt(0) == '>', op.length() == 2, threshold);
    }
}
