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

package com.sk89q.worldedit.function.generator.image;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Finds the block whose colour looks closest to a pixel, using the CIEDE2000 colour difference in the
 * CIE L*a*b* colour space (which matches how different colours look to people far better than RGB distance).
 */
public final class ColorMatcher {

    private final List<BlockPalette.Entry> entries;
    private final double[][] labs;
    private final Map<Integer, BlockPalette.Entry> cache = new HashMap<>();

    /**
     * Create a matcher.
     *
     * @param entries the blocks to choose from
     */
    public ColorMatcher(List<BlockPalette.Entry> entries) {
        checkArgument(!entries.isEmpty(), "No blocks to choose from");
        this.entries = ImmutableList.copyOf(entries);
        this.labs = new double[entries.size()][];
        for (int i = 0; i < entries.size(); i++) {
            labs[i] = toLab(entries.get(i).rgb());
        }
    }

    /**
     * The block that looks closest to a colour.
     *
     * @param rgb the colour as {@code 0xRRGGBB} (alpha is ignored)
     * @return the block
     */
    public BlockPalette.Entry nearest(int rgb) {
        int key = rgb & 0xFFFFFF;
        return cache.computeIfAbsent(key, k -> {
            double[] lab = toLab(k);
            int best = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < labs.length; i++) {
                double distance = deltaE2000(lab, labs[i]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            return entries.get(best);
        });
    }

    /**
     * Convert an sRGB colour to CIE L*a*b* (D65 white).
     *
     * @param rgb the colour as {@code 0xRRGGBB}
     * @return {L, a, b}
     */
    public static double[] toLab(int rgb) {
        double r = linear((rgb >> 16) & 0xFF);
        double g = linear((rgb >> 8) & 0xFF);
        double b = linear(rgb & 0xFF);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = labCurve(x);
        double fy = labCurve(y);
        double fz = labCurve(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    /**
     * The CIEDE2000 difference between two L*a*b* colours.
     *
     * @param first {L, a, b}
     * @param second {L, a, b}
     * @return the difference (about 1 is just noticeable)
     */
    public static double deltaE2000(double[] first, double[] second) {
        double l1 = first[0];
        double a1 = first[1];
        double b1 = first[2];
        double l2 = second[0];
        double a2 = second[1];
        double b2 = second[2];
        double c1 = Math.hypot(a1, b1);
        double c2 = Math.hypot(a2, b2);
        double meanC = (c1 + c2) / 2;
        double meanC7 = Math.pow(meanC, 7);
        double g = 0.5 * (1 - Math.sqrt(meanC7 / (meanC7 + Math.pow(25, 7))));
        double a1p = a1 * (1 + g);
        double a2p = a2 * (1 + g);
        double c1p = Math.hypot(a1p, b1);
        double c2p = Math.hypot(a2p, b2);
        double h1p = hueDegrees(b1, a1p);
        double h2p = hueDegrees(b2, a2p);

        double deltaL = l2 - l1;
        double deltaC = c2p - c1p;
        double deltaH = 0;
        if (c1p * c2p != 0) {
            deltaH = h2p - h1p;
            if (deltaH > 180) {
                deltaH -= 360;
            } else if (deltaH < -180) {
                deltaH += 360;
            }
        }
        double deltaBigH = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(deltaH / 2));

        double meanL = (l1 + l2) / 2;
        double meanCp = (c1p + c2p) / 2;
        double meanH = h1p + h2p;
        if (c1p * c2p != 0) {
            if (Math.abs(h1p - h2p) <= 180) {
                meanH /= 2;
            } else if (h1p + h2p < 360) {
                meanH = (h1p + h2p + 360) / 2;
            } else {
                meanH = (h1p + h2p - 360) / 2;
            }
        }
        double t = 1 - 0.17 * Math.cos(Math.toRadians(meanH - 30))
            + 0.24 * Math.cos(Math.toRadians(2 * meanH))
            + 0.32 * Math.cos(Math.toRadians(3 * meanH + 6))
            - 0.20 * Math.cos(Math.toRadians(4 * meanH - 63));
        double deltaTheta = 30 * Math.exp(-Math.pow((meanH - 275) / 25, 2));
        double meanCp7 = Math.pow(meanCp, 7);
        double rc = 2 * Math.sqrt(meanCp7 / (meanCp7 + Math.pow(25, 7)));
        double meanL50 = (meanL - 50) * (meanL - 50);
        double sl = 1 + 0.015 * meanL50 / Math.sqrt(20 + meanL50);
        double sc = 1 + 0.045 * meanCp;
        double sh = 1 + 0.015 * meanCp * t;
        double rt = -Math.sin(Math.toRadians(2 * deltaTheta)) * rc;
        double termL = deltaL / sl;
        double termC = deltaC / sc;
        double termH = deltaBigH / sh;
        return Math.sqrt(termL * termL + termC * termC + termH * termH + rt * termC * termH);
    }

    private static double hueDegrees(double b, double a) {
        if (a == 0 && b == 0) {
            return 0;
        }
        double degrees = Math.toDegrees(Math.atan2(b, a));
        return degrees < 0 ? degrees + 360 : degrees;
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double labCurve(double t) {
        return t > 216.0 / 24389.0 ? Math.cbrt(t) : (24389.0 / 27.0 * t + 16) / 116;
    }
}
