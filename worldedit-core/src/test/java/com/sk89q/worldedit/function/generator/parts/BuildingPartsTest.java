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

import com.sk89q.worldedit.function.generator.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingPartsTest {

    private static final BuildingPart.Context HERE = new BuildingPart.Context(BlockVector3.ZERO, Direction.NORTH);

    private static BuildPlan plan(BuildingPart part, BuildingPart.Context context, String... settings) {
        return part.plan(ShapeParameters.parse(part.parameters(), List.of(settings)), context);
    }

    private static long count(BuildPlan plan, BuildBlock.Role role) {
        return plan.blocks().values().stream().filter(b -> b.role() == role).count();
    }

    @Test
    void everyPartBuildsSomethingWithDefaults() {
        BuildingPart.Context line = new BuildingPart.Context(BlockVector3.at(20, 0, 5), Direction.NORTH);
        for (BuildingPart part : BuildingParts.all()) {
            BuildPlan plan = plan(part, part.usesSelection() ? line : HERE);
            assertTrue(plan.size() > 10, part.name());
            assertTrue(BuildingParts.find(part.name()).isPresent());
            for (String alias : part.aliases()) {
                assertEquals(part, BuildingParts.find(alias).orElseThrow());
            }
        }
        assertEquals(BuildingParts.ROUND_WINDOW, BuildingParts.find("Round_Window").orElseThrow());
        assertTrue(BuildingParts.find("castle").isEmpty());
    }

    @Test
    void spiralStairClimbsOneBlockPerStepAroundAPole() {
        BuildPlan plan = plan(BuildingParts.SPIRAL_STAIR, HERE, "radius=4", "height=16", "turns=1");
        Set<Integer> levels = plan.blocks().keySet().stream().map(BlockVector3::y).collect(Collectors.toSet());
        assertEquals(17, levels.size()); // 16 steps plus the top of the pole
        for (int y = 0; y <= 16; y++) {
            assertNotNull(plan.get(0, y, 0), "pole at " + y);
        }
        // one turn: every column (apart from the pole) has exactly one step
        Map<BlockVector3, Long> perColumn = plan.blocks().keySet().stream()
            .filter(p -> p.x() != 0 || p.z() != 0)
            .collect(Collectors.groupingBy(p -> p.withY(0), Collectors.counting()));
        assertTrue(perColumn.values().stream().allMatch(c -> c == 1));
        // the first step is in front of the player (north = -z)
        assertNotNull(plan.get(0, 0, -3));
        assertTrue(count(plan, BuildBlock.Role.STAIR) > 0);
        plan.blocks().values().stream().filter(b -> b.role() == BuildBlock.Role.STAIR)
            .forEach(b -> assertTrue(b.facing().isCardinal()));
    }

    @Test
    void spiralStairTreadsFaceTheWayUp() {
        // clockwise from north, the stairs straight north of the pole climb towards the east
        BuildPlan plan = plan(BuildingParts.SPIRAL_STAIR, HERE, "radius=5", "height=40", "turns=1");
        BuildBlock north = plan.blocks().entrySet().stream()
            .filter(e -> e.getKey().x() == 0 && e.getKey().z() == -4)
            .map(Map.Entry::getValue).findFirst().orElseThrow();
        assertEquals(BuildBlock.Role.STAIR, north.role());
        assertEquals(Direction.EAST, north.facing());
        BuildPlan anticlockwise = plan(BuildingParts.SPIRAL_STAIR, HERE, "radius=5", "height=40", "clockwise=0");
        BuildBlock mirrored = anticlockwise.blocks().entrySet().stream()
            .filter(e -> e.getKey().x() == 0 && e.getKey().z() == -4)
            .map(Map.Entry::getValue).findFirst().orElseThrow();
        assertEquals(Direction.WEST, mirrored.facing());
    }

    @Test
    void twoTurnsStackStepsAbove() {
        BuildPlan plan = plan(BuildingParts.SPIRAL_STAIR, HERE, "4", "32", "2");
        long column = plan.blocks().keySet().stream().filter(p -> p.x() == 3 && p.z() == 0).count();
        assertEquals(2, column);
    }

    @Test
    void domeIsAHollowHalfSphereWithWindows() {
        BuildPlan plan = plan(BuildingParts.DOME, HERE, "radius=10");
        assertNull(plan.get(0, 0, 0));
        assertNull(plan.get(0, 5, 0));
        assertNotNull(plan.get(0, 10, 0));
        assertNotNull(plan.get(10, 0, 0));
        assertTrue(plan.blocks().keySet().stream().allMatch(p -> p.y() >= 0 && p.y() <= 10));
        assertEquals(0, count(plan, BuildBlock.Role.DETAIL));

        BuildPlan windows = plan(BuildingParts.DOME, HERE, "radius=10", "windows=4");
        assertEquals(plan.size(), windows.size());
        assertTrue(count(windows, BuildBlock.Role.DETAIL) > 0);
        // the first window faces the player (north)
        assertEquals(BuildBlock.DETAIL, windows.get(0, 1, -10));
        assertEquals(BuildBlock.MAIN, windows.get(0, 0, -10));

        BuildPlan thick = plan(BuildingParts.DOME, HERE, "radius=10", "thickness=3");
        assertTrue(thick.size() > plan.size() * 2);
    }

    @Test
    void towerHasFloorsWindowsAndBattlements() {
        BuildPlan plan = plan(BuildingParts.TOWER, HERE, "radius=4", "height=20", "battlements=1", "windows=4",
            "floors=5");
        // walls
        assertNotNull(plan.get(4, 0, 0));
        assertNotNull(plan.get(-4, 19, 0));
        // floors every 5 blocks, with a hatch, and a roof at the top
        assertNotNull(plan.get(0, 0, 0));
        assertNotNull(plan.get(0, 5, 0));
        assertNull(plan.get(0, 3, 0));
        assertNull(plan.get(3, 5, 0));
        assertNotNull(plan.get(0, 19, 0));
        // window facing the player
        assertEquals(BuildBlock.DETAIL, plan.get(0, 2, -4));
        assertEquals(BuildBlock.DETAIL, plan.get(0, 3, -4));
        assertEquals(BuildBlock.MAIN, plan.get(0, 4, -4));
        // battlements alternate
        long merlons = plan.blocks().keySet().stream().filter(p -> p.y() == 20).count();
        long ring = plan.blocks().keySet().stream().filter(p -> p.y() == 10 && plan.get(p.x(), 11, p.z()) != null
            && Math.hypot(p.x(), p.z()) > 3).count();
        assertTrue(merlons > ring / 4 && merlons < ring, merlons + " of " + ring);

        BuildPlan open = plan(BuildingParts.TOWER, HERE, "battlements=0", "floors=0", "windows=0");
        assertNull(open.get(0, 19, 0));
        assertTrue(open.blocks().keySet().stream().allMatch(p -> p.y() < 20));
        assertThrows(IllegalArgumentException.class, () -> plan(BuildingParts.TOWER, HERE, "radius=3", "thickness=3"));
    }

    @Test
    void wallRunsBetweenTheTwoPoints() {
        BuildingPart.Context context = new BuildingPart.Context(BlockVector3.at(10, 4, 0), Direction.NORTH);
        BuildPlan plan = plan(BuildingParts.WALL, context, "height=5", "thickness=1", "crenellations=0");
        assertNotNull(plan.get(0, 0, 0));
        assertNotNull(plan.get(0, 4, 0));
        assertNull(plan.get(0, 5, 0));
        assertNotNull(plan.get(10, 4, 0));
        assertNotNull(plan.get(10, 8, 0));
        assertTrue(plan.blocks().keySet().stream().allMatch(p -> p.z() == 0));

        BuildPlan crenellated = plan(BuildingParts.WALL, context, "height=5", "thickness=1");
        assertTrue(crenellated.size() > plan.size());

        BuildingPart.Context straightUp = new BuildingPart.Context(BlockVector3.at(0, 10, 0), Direction.NORTH);
        assertThrows(IllegalArgumentException.class, () -> plan(BuildingParts.WALL, straightUp));
    }

    @Test
    void columnIsWiderAtTheEnds() {
        BuildPlan plan = plan(BuildingParts.COLUMN, HERE, "radius=2", "height=10");
        assertNotNull(plan.get(3, 0, 3));
        assertNotNull(plan.get(3, 9, 3));
        assertNotNull(plan.get(3, 1, 0));
        assertNull(plan.get(3, 5, 0));
        assertNotNull(plan.get(2, 5, 0));
        assertTrue(plan.blocks().keySet().stream().allMatch(p -> p.y() >= 0 && p.y() < 10));
    }

    @Test
    void roundWindowStandsUprightFacingThePlayer() {
        BuildingPart.Context east = new BuildingPart.Context(BlockVector3.ZERO, Direction.EAST);
        BuildPlan plan = plan(BuildingParts.ROUND_WINDOW, east, "radius=6", "spokes=4");
        assertTrue(plan.blocks().keySet().stream().allMatch(p -> p.x() == 0));
        assertEquals(BuildBlock.MAIN, plan.get(0, 0, 0)); // hub
        assertEquals(BuildBlock.MAIN, plan.get(0, 6, 0)); // frame
        assertEquals(BuildBlock.MAIN, plan.get(0, 4, 0)); // spoke straight up
        assertEquals(BuildBlock.DETAIL, plan.get(0, 2, 4)); // pane between spokes
        assertNotNull(plan.get(0, -6, 0));

        BuildPlan deep = plan(BuildingParts.ROUND_WINDOW, east, "radius=6", "depth=2");
        assertEquals(plan.size() * 2, deep.size());
        assertTrue(deep.blocks().keySet().stream().allMatch(p -> p.x() == 0 || p.x() == 1));
    }

    @Test
    void bridgeArchesBetweenTheTwoPoints() {
        BuildingPart.Context context = new BuildingPart.Context(BlockVector3.at(0, 0, 20), Direction.NORTH);
        BuildPlan plan = plan(BuildingParts.BRIDGE, context, "width=3", "arch=6", "railings=1");
        assertEquals(BuildBlock.MAIN, plan.get(0, 0, 0));
        assertEquals(BuildBlock.MAIN, plan.get(0, 0, 20));
        assertEquals(BuildBlock.MAIN, plan.get(1, 6, 10));
        assertEquals(BuildBlock.MAIN, plan.get(-1, 6, 10));
        assertNull(plan.get(0, 7, 10));
        // railings outside the deck
        assertEquals(BuildBlock.DETAIL, plan.get(2, 7, 10));
        assertEquals(BuildBlock.DETAIL, plan.get(-2, 7, 10));
        assertFalse(plan.blocks().keySet().stream().anyMatch(p -> Math.abs(p.x()) > 2));

        BuildPlan plain = plan(BuildingParts.BRIDGE, context, "railings=0");
        assertEquals(0, count(plain, BuildBlock.Role.DETAIL));
    }

    @Test
    void steepBridgeHasNoGaps() {
        BuildingPart.Context context = new BuildingPart.Context(BlockVector3.at(6, 0, 0), Direction.NORTH);
        BuildPlan plan = plan(BuildingParts.BRIDGE, context, "width=1", "arch=12", "railings=0");
        for (int x = 0; x <= 6; x++) {
            int column = x;
            List<Integer> ys = plan.blocks().keySet().stream().filter(p -> p.x() == column)
                .map(BlockVector3::y).sorted().toList();
            for (int i = 1; i < ys.size(); i++) {
                assertEquals(ys.get(i - 1) + 1, ys.get(i), "gap at x=" + x);
            }
        }
    }

    @Test
    void facingPlaneReadsLeftToRight() {
        assertEquals(Direction.SOUTH, FacingPlane.cardinalFromYaw(0));
        assertEquals(Direction.WEST, FacingPlane.cardinalFromYaw(90));
        assertEquals(Direction.NORTH, FacingPlane.cardinalFromYaw(180));
        assertEquals(Direction.NORTH, FacingPlane.cardinalFromYaw(-170));
        assertEquals(Direction.EAST, FacingPlane.cardinalFromYaw(-90));
        assertEquals(Direction.EAST, FacingPlane.cardinalFromYaw(270));

        // looking north, right is east and the thickness goes north
        FacingPlane north = new FacingPlane(Direction.NORTH, false);
        assertEquals(BlockVector3.at(3, 2, -1), north.map(3, 2, 1));
        // looking east, right is south
        assertEquals(BlockVector3.at(0, 0, 1), new FacingPlane(Direction.EAST, false).map(1, 0, 0));
        // flat: up the picture is away from the player, thickness goes up
        assertEquals(BlockVector3.at(1, 3, -2), new FacingPlane(Direction.NORTH, true).map(1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new FacingPlane(Direction.UP, false));
    }

    @Test
    void planSizeIsLimited() {
        BuildPlan plan = new BuildPlan(2);
        plan.put(0, 0, 0, BuildBlock.MAIN);
        plan.put(1, 0, 0, BuildBlock.MAIN);
        plan.put(1, 0, 0, BuildBlock.DETAIL);
        assertThrows(IllegalArgumentException.class, () -> plan.put(2, 0, 0, BuildBlock.MAIN));
    }
}
