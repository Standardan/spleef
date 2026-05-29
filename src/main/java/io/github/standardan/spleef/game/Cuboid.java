package io.github.standardan.spleef.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * A simple axis-aligned box between two corners in one world. Used for the
 * Spleef floor: we can test whether a block is part of the floor, and refill
 * the whole floor between rounds.
 */
public final class Cuboid {

    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public Cuboid(Location a, Location b) {
        this.world = a.getWorld().getName();
        this.minX = Math.min(a.getBlockX(), b.getBlockX());
        this.minY = Math.min(a.getBlockY(), b.getBlockY());
        this.minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        this.maxX = Math.max(a.getBlockX(), b.getBlockX());
        this.maxY = Math.max(a.getBlockY(), b.getBlockY());
        this.maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
            return false;
        }
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Fill every block in the box with a material (used to (re)build the floor). */
    public void fill(World w, Material material) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    w.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    public int minY() {
        return minY;
    }

    public World getWorld() {
        return Bukkit.getWorld(world);
    }
}
