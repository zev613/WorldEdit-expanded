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

package com.sk89q.worldedit.regions.smart;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.MultiUserPlatform;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.extension.platform.permission.ActorSelectorLimits;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Keeps track of who is recording a {@code //polywalk}, and samples their position every server tick.
 *
 * <p>The tick task is only registered while at least one walk is running. A walk stops by itself when
 * its player logs out or changes world.</p>
 */
public final class PolyWalkManager {

    /**
     * How far below the lowest walked position the finished selection starts.
     */
    public static final int GROUND_MARGIN = 1;

    /**
     * Roughly how many blocks are tested to find the top of the build when a walk is finished.
     */
    public static final long MAX_HEIGHT_CHECKS = 4_000_000;

    /**
     * A walk being recorded.
     */
    public static final class Walk {
        private final PolyWalkPath path;
        private final World world;
        private Actor actor;
        private boolean warnedLimit;

        Walk(PolyWalkPath path, World world, Actor actor) {
            this.path = path;
            this.world = world;
            this.actor = actor;
        }

        public PolyWalkPath getPath() {
            return path;
        }

        public World getWorld() {
            return world;
        }
    }

    /**
     * What a finished walk selected.
     *
     * @param outline the polygon points
     * @param minY the lowest y
     * @param maxY the highest y
     * @param recordedPoints how many points were recorded before simplifying
     * @param selector the selector that was set
     */
    public record Finished(List<BlockVector2> outline, int minY, int maxY, int recordedPoints,
                           Polygonal2DRegionSelector selector) {
    }

    private static final PolyWalkManager INSTANCE = new PolyWalkManager();

    private final Map<UUID, Walk> walks = new ConcurrentHashMap<>();
    private final Runnable ticker = this::tick;

    public static PolyWalkManager getInstance() {
        return INSTANCE;
    }

    /**
     * Start recording a walk, replacing any walk the actor was recording.
     *
     * @param actor the walker
     * @param world the world they are in
     * @param spacing the distance between automatic points
     * @return the walk
     */
    public Walk start(Actor actor, World world, double spacing) {
        checkNotNull(actor);
        checkNotNull(world);
        Walk walk = new Walk(new PolyWalkPath(spacing), world, actor);
        walks.put(actor.getUniqueId(), walk);
        if (actor instanceof Player) {
            WorldEdit.getInstance().getTickListeners().add(ticker);
        }
        return walk;
    }

    /**
     * The walk an actor is recording.
     *
     * @param actor the actor
     * @return the walk, or null
     */
    @Nullable
    public Walk get(Actor actor) {
        return walks.get(actor.getUniqueId());
    }

    /**
     * Stop recording an actor's walk without selecting anything.
     *
     * @param actor the actor
     * @return the walk that was stopped, or null
     */
    @Nullable
    public Walk remove(Actor actor) {
        return remove(actor.getUniqueId());
    }

    @Nullable
    private Walk remove(UUID id) {
        Walk walk = walks.remove(id);
        if (walks.isEmpty()) {
            WorldEdit.getInstance().getTickListeners().remove(ticker);
        }
        return walk;
    }

    /**
     * Whether anyone is walking.
     *
     * @return true if at least one walk is being recorded
     */
    public boolean isActive() {
        return !walks.isEmpty();
    }

    private void tick() {
        if (walks.isEmpty()) {
            WorldEdit.getInstance().getTickListeners().remove(ticker);
            return;
        }
        for (Map.Entry<UUID, Walk> entry : walks.entrySet()) {
            Walk walk = entry.getValue();
            Player player = findOnlinePlayer(entry.getKey(), walk.actor);
            if (player == null) {
                // logged out
                remove(entry.getKey());
                continue;
            }
            walk.actor = player;
            if (!sameWorld(player.getWorld(), walk.world)) {
                remove(entry.getKey());
                player.printError(TextComponent.of("//polywalk stopped because you changed world; "
                    + "nothing was selected. Start again with //polywalk."));
                continue;
            }
            Vector3 position = player.getLocation().toVector();
            if (walk.path.offer(position)) {
                pointAdded(player, walk);
            }
        }
    }

    /**
     * Tell the walker about a new point and show the selection so far.
     *
     * @param actor the walker
     * @param walk the walk
     */
    public void pointAdded(Actor actor, Walk walk) {
        PolyWalkPath path = walk.path;
        BlockVector2 last = path.getPoints().getLast().position();
        actor.printInfo(TextComponent.of("Polywalk point " + path.size() + " at " + last.x() + ", " + last.z()
            + " (//polywalk stop to finish, undo to remove it)"));
        int limit = pointLimit(actor);
        if (limit > 0 && path.size() > limit && !walk.warnedLimit) {
            walk.warnedLimit = true;
            actor.printInfo(TextComponent.of("That is more than the " + limit + " points a polygon may have here; "
                + "the outline will be simplified when you stop."));
        }
        showSelection(actor, walk);
    }

    /**
     * Show the walk so far as the walker's polygon selection (so WorldEdit CUI and //size show it).
     *
     * @param actor the walker
     * @param walk the walk
     */
    public void showSelection(Actor actor, Walk walk) {
        PolyWalkPath path = walk.path;
        if (path.size() == 0) {
            return;
        }
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        int minY = path.getLowestY() - GROUND_MARGIN;
        int maxY = path.getHighestY() + 2;
        Polygonal2DRegionSelector selector = new Polygonal2DRegionSelector(walk.world,
            FootprintOutline.atLeastThreePoints(path.getColumns()), clampY(walk.world, minY), clampY(walk.world, maxY));
        session.setRegionSelector(walk.world, selector);
        session.dispatchCUISelection(actor);
    }

    /**
     * Finish a walk: simplify the outline, choose the height range and select it.
     *
     * @param actor the walker
     * @param walk the walk (already removed or not)
     * @param heightRange an explicit {@code {minY, maxY}}, or null to work it out from the walk and the build
     * @return the result
     */
    public Finished finish(Actor actor, Walk walk, @Nullable int[] heightRange) {
        remove(actor);
        PolyWalkPath path = walk.path;
        World world = walk.world;
        List<BlockVector2> outline = FootprintOutline.atLeastThreePoints(path.outline(pointLimit(actor)));
        int minY;
        int maxY;
        if (heightRange != null) {
            minY = Math.min(heightRange[0], heightRange[1]);
            maxY = Math.max(heightRange[0], heightRange[1]);
        } else {
            minY = path.getLowestY() - GROUND_MARGIN;
            int top = PolyWalkPath.highestBlockInside(outline, path.getLowestY(), world.getMaxY(),
                (x, y, z) -> !NaturalBlocks.isAir(world.getBlock(BlockVector3.at(x, y, z))),
                MAX_HEIGHT_CHECKS);
            maxY = Math.max(top, path.getHighestY());
        }
        minY = clampY(world, minY);
        maxY = clampY(world, maxY);
        Polygonal2DRegionSelector selector = new Polygonal2DRegionSelector(world, outline, minY, maxY);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        session.setRegionSelector(world, selector);
        session.dispatchCUISelection(actor);
        return new Finished(outline, minY, maxY, path.size(), selector);
    }

    private static int clampY(World world, int y) {
        return Math.max(world.getMinY(), Math.min(world.getMaxY(), y));
    }

    /**
     * The most points a polygon selection may have for an actor.
     *
     * @param actor the actor
     * @return the limit, or 0 for none
     */
    public static int pointLimit(Actor actor) {
        return ActorSelectorLimits.forActor(actor).getPolygonVertexLimit().orElse(0);
    }

    private static boolean sameWorld(World a, World b) {
        return a == b || Objects.equals(a.id(), b.id());
    }

    @Nullable
    private static Player findOnlinePlayer(UUID id, Actor last) {
        Platform platform;
        try {
            platform = WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.USER_COMMANDS);
        } catch (RuntimeException _) {
            return null;
        }
        if (platform instanceof MultiUserPlatform multiUser) {
            for (Actor user : multiUser.getConnectedUsers()) {
                if (user instanceof Player player && id.equals(user.getUniqueId())) {
                    return player;
                }
            }
            return null;
        }
        return last instanceof Player player ? player : null;
    }
}
