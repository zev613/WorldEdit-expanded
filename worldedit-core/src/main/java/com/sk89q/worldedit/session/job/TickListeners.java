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

package com.sk89q.worldedit.session.job;

import com.sk89q.worldedit.internal.util.LogManagerCompat;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CopyOnWriteArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Tasks that run once per server tick, on the server thread, for features that follow players over time
 * (such as {@code //polywalk}).
 *
 * <p>The platform calls {@link #tick()} from the same place it ticks the {@link EditJobQueue}. Listeners
 * should only be registered while they have work to do, so a server where nobody uses such a feature
 * does nothing extra each tick. Platforms that do not tick (such as the command line) never run them.</p>
 */
public final class TickListeners {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    /**
     * Run a task every tick until it is removed. Adding the same task twice has no effect.
     *
     * @param listener the task
     */
    public void add(Runnable listener) {
        listeners.addIfAbsent(checkNotNull(listener));
    }

    /**
     * Stop running a task.
     *
     * @param listener the task
     */
    public void remove(Runnable listener) {
        listeners.remove(listener);
    }

    /**
     * Whether a task is registered.
     *
     * @param listener the task
     * @return true if it runs every tick
     */
    public boolean contains(Runnable listener) {
        return listeners.contains(listener);
    }

    public boolean isEmpty() {
        return listeners.isEmpty();
    }

    /**
     * Run every registered task once. A task that throws is logged and removed.
     */
    public void tick() {
        if (listeners.isEmpty()) {
            return;
        }
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                LOGGER.error("A WorldEdit tick task failed and was stopped", e);
                listeners.remove(listener);
            }
        }
    }
}
