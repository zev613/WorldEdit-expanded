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

package com.sk89q.worldedit.function.clipboard;

import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;

/**
 * Where the copies of {@code //radial} go.
 *
 * <p>Copy {@code k} (counting from 0) is the paste turned {@code k * 360 / count} degrees
 * clockwise (seen from above) around the paste position, so the copies are evenly spaced and
 * each faces outward like a spoke. Copy 0 is exactly where {@code //paste} would put it. With a
 * radius, every copy is first slid straight outward (or inward) so the centre of the clipboard is
 * that many blocks (rounded to whole blocks) from the paste position.</p>
 */
public final class RadialLayout {

    /** The most copies allowed. */
    public static final int MAX_COUNT = 360;

    private final int count;
    private final Vector3 shift;

    /**
     * Create a layout.
     *
     * @param count the number of copies
     * @param center the clipboard centre relative to its origin (after any //rotate or //flip)
     * @param radius the distance from the paste position to the centre of each copy, or null to
     *     keep the clipboard's own distance
     * @param fallbackYaw the direction (Minecraft yaw in degrees) to push the clipboard out to when
     *     its centre is right on the paste position
     */
    public RadialLayout(int count, Vector3 center, Double radius, double fallbackYaw) {
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("The number of copies must be between 1 and " + MAX_COUNT + ".");
        }
        if (radius != null && (radius < 0 || radius.isNaN() || radius.isInfinite())) {
            throw new IllegalArgumentException("The radius cannot be negative.");
        }
        this.count = count;
        if (radius == null) {
            this.shift = Vector3.ZERO;
        } else {
            double cx = center.x();
            double cz = center.z();
            double length = Math.hypot(cx, cz);
            double dirX;
            double dirZ;
            if (length < 0.5) {
                double yaw = Math.toRadians(fallbackYaw);
                dirX = -Math.sin(yaw);
                dirZ = Math.cos(yaw);
            } else {
                dirX = cx / length;
                dirZ = cz / length;
            }
            // whole blocks, so copy 0 stays on the block grid
            this.shift = Vector3.at(Math.rint(dirX * radius - cx), 0, Math.rint(dirZ * radius - cz));
        }
    }

    public int getCount() {
        return count;
    }

    /**
     * Get how far each copy is slid (before turning) to sit at the radius.
     *
     * @return the shift
     */
    public Vector3 getShift() {
        return shift;
    }

    /**
     * Get the clockwise angle of a copy.
     *
     * @param index the copy, from 0
     * @return the angle in degrees
     */
    public double angle(int index) {
        return 360.0 * index / count;
    }

    /**
     * Get the transform of a copy, applied to positions relative to the clipboard origin.
     *
     * @param index the copy, from 0
     * @return the transform
     */
    public AffineTransform transform(int index) {
        return clockwise(angle(index)).translate(shift);
    }

    /**
     * Get a rotation clockwise (seen from above) around the vertical axis.
     *
     * @param degrees the angle in degrees
     * @return the transform
     */
    public static AffineTransform clockwise(double degrees) {
        return new AffineTransform().rotateY(-degrees);
    }
}
