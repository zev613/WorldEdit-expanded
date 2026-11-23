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

package com.sk89q.worldedit.util.buildtools;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure id-mapping logic for {@code //retheme}: given a block id and a
 * "from" and "to" material token, produce the ordered list of candidate
 * target ids. The caller picks the first candidate that exists.
 *
 * <p>Tokens and ids are compared as sequences of {@code _}-separated
 * segments, so {@code oak} matches {@code oak_planks} and
 * {@code stripped_oak_log} but not {@code oaky_thing}. The last segment of
 * the "from" token also matches its plural/singular form, so
 * {@code stone_brick} matches both {@code stone_bricks} and
 * {@code stone_brick_stairs}.</p>
 */
public final class RethemeMapper {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+(_[a-z0-9]+)*");

    /**
     * Material families whose name ends with another family's name. A match
     * of the "from" token that is really part of one of these is ignored,
     * e.g. {@code oak} does not match {@code dark_oak_planks}.
     */
    private static final Set<String> COMPOUND_FAMILIES = ImmutableSet.of(
        "dark_oak", "pale_oak", "red_sandstone", "red_nether_brick", "red_nether_bricks",
        "end_stone", "end_stone_brick", "end_stone_bricks"
    );

    /**
     * Segment substitutions tried (in order) on the part of the id after the
     * material when the straight replacement does not exist, covering naming
     * quirks between families.
     */
    private static final List<String[]> SEGMENT_QUIRKS = ImmutableList.of(
        new String[] { "log", "stem" },
        new String[] { "stem", "log" },
        new String[] { "wood", "hyphae" },
        new String[] { "hyphae", "wood" },
        new String[] { "log", "block" },
        new String[] { "block", "log" },
        new String[] { "leaves", "wart_block" },
        new String[] { "wart_block", "leaves" },
        new String[] { "sapling", "fungus" },
        new String[] { "fungus", "sapling" },
        new String[] { "sapling", "propagule" },
        new String[] { "propagule", "sapling" }
    );

    /**
     * Ids that belong to a family without containing its token, mapped to a
     * canonical (non-existent) name that does contain it.
     */
    private static final Map<String, String> ALIASES = ImmutableMap.of(
        "nether_wart_block", "crimson_wart_block"
    );

    private RethemeMapper() {
    }

    /**
     * Check whether a user supplied token is well-formed: lowercase letters,
     * digits and single underscores, without a namespace.
     *
     * @param token the token
     * @return true if valid
     */
    public static boolean isValidToken(String token) {
        return token != null && TOKEN_PATTERN.matcher(token).matches();
    }

    /**
     * Check whether a block belongs to a material family, e.g. {@code oak_stairs} to {@code oak}
     * (but {@code dark_oak_stairs} does not).
     *
     * @param blockId the block id, with or without namespace
     * @param token the material token
     * @return true if the block's id contains the material
     */
    public static boolean isMember(String blockId, String token) {
        String path = blockId.substring(blockId.indexOf(':') + 1);
        path = ALIASES.getOrDefault(path, path);
        return findMatch(path.split("_"), token.toLowerCase(Locale.ROOT).split("_")) >= 0;
    }

    /**
     * Check whether any of the given blocks belongs to a material family, so a typo such as
     * {@code spruse} can be reported instead of silently changing nothing.
     *
     * @param blockIds the ids of all known blocks
     * @param token the material token
     * @return true if at least one block has the material in its id
     */
    public static boolean isKnownMaterial(Iterable<String> blockIds, String token) {
        for (String id : blockIds) {
            if (isMember(id, token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compute candidate target ids for the given block id.
     *
     * @param blockId the source block id, with or without namespace
     * @param from the material token to replace
     * @param to the material token to replace it with
     * @return ordered candidate ids (with the source namespace, defaulting to
     *     {@code minecraft:}), or an empty list if the block is not part of
     *     the "from" family (or already part of the "to" family)
     */
    public static List<String> candidates(String blockId, String from, String to) {
        String fromToken = from.toLowerCase(Locale.ROOT);
        String toToken = to.toLowerCase(Locale.ROOT);
        if (fromToken.equals(toToken)) {
            return ImmutableList.of();
        }
        String namespace;
        String path;
        int colon = blockId.indexOf(':');
        if (colon >= 0) {
            namespace = blockId.substring(0, colon + 1);
            path = blockId.substring(colon + 1);
        } else {
            namespace = "minecraft:";
            path = blockId;
        }
        path = ALIASES.getOrDefault(path, path);

        String[] segments = path.split("_");
        String[] fromSegs = fromToken.split("_");
        String[] toSegs = toToken.split("_");

        int index = findMatch(segments, fromSegs);
        if (index < 0 || occursOutside(segments, toSegs, index, index + fromSegs.length)) {
            // Not in the family, or already themed with the target material
            // (e.g. mossy_cobblestone when going from cobblestone to mossy_cobblestone)
            return ImmutableList.of();
        }

        String matchedLast = segments[index + fromSegs.length - 1];
        String fromLast = fromSegs[fromSegs.length - 1];
        String toLast = toSegs[toSegs.length - 1];
        List<String> lastVariants = new ArrayList<>();
        if (matchedLast.equals(fromLast + "s")) {
            // Block uses the plural ("bricks") form
            if (!toLast.endsWith("s")) {
                lastVariants.add(toLast + "s");
            }
            lastVariants.add(toLast);
        } else if (fromLast.equals(matchedLast + "s")) {
            // User typed the plural, block uses the singular
            if (toLast.endsWith("s") && toLast.length() > 1) {
                lastVariants.add(toLast.substring(0, toLast.length() - 1));
            }
            lastVariants.add(toLast);
        } else {
            lastVariants.add(toLast);
            if (toLast.endsWith("s") && toLast.length() > 1) {
                lastVariants.add(toLast.substring(0, toLast.length() - 1));
            }
        }

        List<String> prefix = Arrays.asList(segments).subList(0, index);
        List<String> suffix = Arrays.asList(segments).subList(index + fromSegs.length, segments.length);
        String toHead = String.join("_", Arrays.asList(toSegs).subList(0, toSegs.length - 1));
        String suffixText = String.join("_", suffix);

        List<String> heads = new ArrayList<>();
        for (String last : lastVariants) {
            String replacement = toHead.isEmpty() ? last : toHead + "_" + last;
            heads.add(join(prefix, replacement));
        }

        Set<String> result = new LinkedHashSet<>();
        for (String head : heads) {
            result.add(suffixText.isEmpty() ? head : head + "_" + suffixText);
        }
        for (String head : heads) {
            for (String[] quirk : SEGMENT_QUIRKS) {
                String replaced = replaceSegments(suffixText, quirk[0], quirk[1]);
                if (replaced != null) {
                    result.add(head + "_" + replaced);
                }
            }
            // "quartz_block" <-> "quartz"
            if (suffixText.equals("block")) {
                result.add(head);
            } else if (suffixText.isEmpty()) {
                result.add(head + "_block");
            }
        }

        Set<String> out = new LinkedHashSet<>();
        for (String candidate : result) {
            String reverseAlias = reverseAlias(candidate);
            if (reverseAlias != null) {
                out.add(namespace + reverseAlias);
            }
            out.add(namespace + candidate);
        }
        return ImmutableList.copyOf(out);
    }

    private static String reverseAlias(String candidate) {
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (entry.getValue().equals(candidate)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String join(List<String> prefix, String middle) {
        StringBuilder builder = new StringBuilder();
        for (String s : prefix) {
            builder.append(s).append('_');
        }
        builder.append(middle);
        return builder.toString();
    }

    /**
     * Replace a whole-segment sequence within an {@code _}-joined string.
     *
     * @return the replaced string, or null if {@code target} does not occur
     */
    private static String replaceSegments(String text, String target, String replacement) {
        if (text.isEmpty()) {
            return null;
        }
        String padded = "_" + text + "_";
        String needle = "_" + target + "_";
        int at = padded.indexOf(needle);
        if (at < 0) {
            return null;
        }
        String result = padded.substring(0, at) + "_" + replacement + "_" + padded.substring(at + needle.length());
        return result.substring(1, result.length() - 1);
    }

    private static boolean segmentMatches(String actual, String wanted, boolean allowPlural) {
        if (actual.equals(wanted)) {
            return true;
        }
        return allowPlural && (actual.equals(wanted + "s") || wanted.equals(actual + "s"));
    }

    /**
     * Check whether {@code needle} occurs as a segment sequence anywhere other
     * than entirely within {@code [start, end)}.
     */
    private static boolean occursOutside(String[] haystack, String[] needle, int start, int end) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (!haystack[i + j].equals(needle[j])) {
                    continue outer;
                }
            }
            if (i < start || i + needle.length > end) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the first index where {@code needle} occurs as a segment sequence.
     * The final segment may differ by a trailing "s", and matches that are
     * part of a compound family are skipped.
     */
    private static int findMatch(String[] haystack, String[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                boolean last = j == needle.length - 1;
                if (!segmentMatches(haystack[i + j], needle[j], last)) {
                    continue outer;
                }
            }
            if (i > 0) {
                String matched = String.join("_", Arrays.asList(haystack).subList(i, i + needle.length));
                if (COMPOUND_FAMILIES.contains(haystack[i - 1] + "_" + matched)) {
                    continue;
                }
            }
            return i;
        }
        return -1;
    }
}
