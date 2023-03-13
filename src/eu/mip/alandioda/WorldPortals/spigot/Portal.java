package eu.mip.alandioda.WorldPortals.spigot;

import org.bukkit.Bukkit;
import org.bukkit.World;

public class Portal {

	private String worldName;
	private int portalSize;
	
	public Portal(String world, int max) {
		this.worldName = world;
		this.portalSize = max;
	}
	
	public String getWorldName () {
		return worldName;
	}
	
	public World getWorld () {
		return Bukkit.getWorld(this.worldName);
	}
	
	public int getMaxPortalSize () {
		return portalSize;
	}
}
