package eu.mip.alandioda.WorldPortals.spigot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.PortalType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityCreatePortalEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class main extends JavaPlugin implements Listener {
	
	Map<Material, Portal> portals = new HashMap<Material, Portal>();
	
	Map<String, Material> portalsMaterialByWorld = new HashMap<String, Material>();
	
	FileConfiguration config;
	int searchRange = 128;
	
	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		reloadConfig();
		config = getConfig();
		config.options().copyDefaults(true);
		saveDefaultConfig();
	    LoadConfig(config);
	}
	
	private void LoadConfig(FileConfiguration conf) {
		ConfigurationSection section = conf.getConfigurationSection("portals");
		if(section != null) {
			Set<String> worlds = section.getKeys(false);
			for(String w : worlds) {
				String blockMaterial = conf.getString("portals." + w + ".block-material");
				Material material = Material.getMaterial(blockMaterial);
				int max = conf.getInt("portals." + w + ".max-blocks");
				Portal portal = new Portal(w, max);
				if(searchRange < max) {
					searchRange = max + 2;
				}
				portals.put(material, portal);
				portalsMaterialByWorld.put(w, material);
			}
		}
	}

	@EventHandler
	public void onEntityPortalEnterEvent(EntityPortalEnterEvent e) {
		if(!(e.getEntity() instanceof Player) && e.getEntity().getPortalCooldown() == 0) {
			Location location = e.getEntity().getLocation().getBlock().getLocation();
			Location fromLocation = location.clone();
			Material m = getPortalMaterial(location, searchRange);
			if(portals.containsKey(m)) {
				Portal portal = portals.get(m);
				if(e.getEntity().getWorld().getName().equals(portal.getWorldName())) {
					return;
				}
				location.setWorld(portal.getWorld());
				Material materialOnTheOtherSide = portalsMaterialByWorld.get(fromLocation.getWorld().getName());
				
				Location loc = location.clone();
				loc = getPortalLocation(loc, materialOnTheOtherSide, searchRange);
				if(loc != null) {
					e.getEntity().setPortalCooldown(100);
					e.getEntity().teleport(loc);
				}
			}
		}
	}
	
	@EventHandler
	public void onPlayerPortalEvent(PlayerPortalEvent e) {
		
		Location location = e.getFrom().getBlock().getLocation();
		Location fromLocation = e.getPlayer().getLocation().getBlock().getLocation();
		Material m = getPortalMaterial(location, searchRange);
		if(portals.containsKey(m)) {
			Portal portal = portals.get(m);
			if(e.getPlayer().getWorld().getName().equals(portal.getWorldName())) {
				return;
			}
			location.setWorld(portal.getWorld());
			
			Material materialOnTheOtherSide = portalsMaterialByWorld.get(fromLocation.getWorld().getName());
			Location loc = location.clone();
			loc = getPortalLocation(loc, materialOnTheOtherSide, searchRange);
			if(loc == null) {
				Material portalMaterial = portalsMaterialByWorld.get(fromLocation.getWorld().getName());
				boolean isZ = isZDir(fromLocation);
				
				loc = getPossiblePortalSpawnLocation(location, searchRange);
				List<BlockState> blocks = new ArrayList<BlockState>();
				for(Vector v : getFrame(isZ)) {
					Block b = loc.clone().add(v).getBlock();
					BlockState bs = b.getState();
					bs.setType(portalMaterial);
					blocks.add(bs);
				}
				Axis dirData = ((isZ)?(Axis.X):(Axis.Z));
				for(Vector v : getPortal(isZ)) {
					Block b = loc.clone().add(v).getBlock();
					BlockState bs = b.getState();
					bs.setType(Material.NETHER_PORTAL);
					blocks.add(bs);
					BlockData bd = b.getBlockData();
					if(bd instanceof Orientable) {
						Orientable o = (Orientable) bd;
						o.setAxis(dirData);
					}
				}
				Bukkit.getPluginManager().callEvent(new EntityCreatePortalEvent(e.getPlayer(), blocks, PortalType.CUSTOM));
			}
			e.setCancelled(true);
			if(loc != null) {
				loc = loc.getBlock().getLocation().add(0.5, 0, 0.5);
				e.getPlayer().teleport(loc);
			}
		}
	}
	
	@EventHandler
	public void onEntityCreatePortalEvent (EntityCreatePortalEvent e) {
		for(BlockState bs : e.getBlocks()) {
			bs.update(true, false);
		}
	}
	
	boolean isZDir(Location location) {
		Block b = location.getBlock();
		BlockData bd = b.getBlockData();
		System.out.println("isZDir");
		if(bd instanceof Orientable) {
			Orientable o = (Orientable) bd;
			for(Axis a : o.getAxes()) {
				System.out.print(a.name() + ", ");
			}
			System.out.println();
			System.out.println("asd");
			return o.getAxis().equals(Axis.Z);
		}
		return false;
	}
	
	private Location getPossiblePortalSpawnLocation(Location location, int range) {
		Location endLocation = null;
		double distance = 0;
		for(int x = 0; x < range; x++) {
			int difX = x - range/2;
			for(int y = location.getWorld().getMaxHeight(); y > 0; y--) {
				for(int z = 0; z < range; z++) {
					int difZ = z - range/2;
					Location newLocation = new Location(location.getWorld(), location.getX() + difX, y, location.getZ() + difZ);
					Location upLoc = newLocation.clone().add(0, 1, 0);
					if(newLocation.getBlock().getType().isSolid() && upLoc.getBlock().getType().equals(Material.AIR) && !(newLocation.getBlock().getType().equals(Material.BEDROCK) && y > 20)) {
						double r = location.distanceSquared(upLoc);
						if(endLocation == null || r < distance) {
							distance = r;
							endLocation = upLoc;
						}
					}
				}
			}
		}
		return endLocation;
	}
	
	Location getPortalLocation(Location location, Material material, int range) {
		Location endLocation = null;
		double distance = 0;
		for(int x = 0; x < range; x++) {
			int difX = x - range/2;
			for(int y = location.getWorld().getMaxHeight(); y > 0; y--) {
				for(int z = 0; z < range; z++) {
					int difZ = z - range/2;
					Location newLocation = new Location(location.getWorld(), location.getX() + difX, y, location.getZ() + difZ);
					if(newLocation.getBlock().getType().equals(material)) {
						Location locationUp = newLocation.clone().add(0, 1, 0);
						if(locationUp.getBlock().getType().equals(Material.NETHER_PORTAL)) {
							double r = location.distanceSquared(locationUp);
							if(endLocation == null || r < distance) {
								distance = r;
								endLocation = locationUp;
							}
						}
					}
				}
			}
		}
		return endLocation;
	}
	
	private Material getPortalMaterial(Location location, int range) {
		Material mat = null;
		Location loc = location.getBlock().getLocation().clone();
		if(loc.getBlock().getType().equals(Material.NETHER_PORTAL)) {
			for(int y = 0; y < range; y++) {
				int difY = y;
				Location newLocation = new Location(loc.getWorld(), loc.getX(), loc.getY() + difY, loc.getZ());
				if(!newLocation.getBlock().getType().equals(Material.NETHER_PORTAL)) {
					mat = newLocation.getBlock().getType();
					break;
				}
			}
		}
		return mat;
	}
	
	@EventHandler
	public void onBlockIgniteEvent(BlockIgniteEvent e) {
		if(e.getIgnitingBlock() == null) {
			
			List<Vector> vectors = getCross();
			lookForNewDirection:
			for(Vector v : vectors) {
				Material portalMaterial = e.getBlock().getLocation().clone().add(v).getBlock().getType();
				if(portals.containsKey(portalMaterial)) {
					Portal portal = portals.get(portalMaterial);
					Location location = e.getBlock().getLocation();		     	  	  //   0, 1, 2,    3, 4, 5
					boolean directions[] = {false, false, false, false, false, false};// +(x, y, z), -(x, y, z)
					List<Double> directionValues = new ArrayList<Double>();
					
					List<Vector> vectors2 = getCross();
					for(int q = 0; q < vectors2.size(); q++) {
						directionValues.add(((vectors2.get(q).getX() != 0d)?(location.getX()):(vectors2.get(q).getY() != 0d)?(location.getY()):(location.getZ())));
						for(int i = 0; i < portal.getMaxPortalSize()*2; i++) {
							Location loc = location.clone().add(vectors2.get(q).clone().multiply(i));
							if(loc.getBlock().getType().equals(portalMaterial)) {
								directionValues.set(q, ((vectors2.get(q).getX() != 0)?(loc.getX()):(vectors2.get(q).getY() != 0)?(loc.getY()):(loc.getZ())));
								directions[q] = true;
								break;
							} else if(loc.getBlock().getType().equals(Material.AIR) || loc.getBlock().getType().equals(Material.FIRE)) {
								directionValues.set(q, ((vectors2.get(q).getX() != 0)?(loc.getX()):(vectors2.get(q).getY() != 0)?(loc.getY()):(loc.getZ())));
							} else {
								break;
							}
						}
					}

					double xMin = ((directionValues.get(0) < directionValues.get(3))?(directionValues.get(0)):(directionValues.get(3)));
					double xMax = ((directionValues.get(0) > directionValues.get(3))?(directionValues.get(0)):(directionValues.get(3)));
					double yMin = ((directionValues.get(1) < directionValues.get(4))?(directionValues.get(1)):(directionValues.get(4)));
					double yMax = ((directionValues.get(1) > directionValues.get(4))?(directionValues.get(1)):(directionValues.get(4)));
					double zMin = ((directionValues.get(2) < directionValues.get(5))?(directionValues.get(2)):(directionValues.get(5)));
					double zMax = ((directionValues.get(2) > directionValues.get(5))?(directionValues.get(2)):(directionValues.get(5)));
					
					if(yMax - yMin > portal.getMaxPortalSize()) {
						break lookForNewDirection;
					}
					
					if(directions[0] && directions[1] && directions[3] && directions[4]) {// x
						if(xMax - xMin > portal.getMaxPortalSize()) {
							break lookForNewDirection;
						}
						double z = location.getZ();
						for(double x = (xMin +1); x < xMax; x += 1) { // look x
							Location l1 = new Location(location.getWorld(), x, yMin, z);
							Location l2 = new Location(location.getWorld(), x, yMax, z);
							if(!l1.getBlock().getType().equals(portalMaterial) || !l2.getBlock().getType().equals(portalMaterial)) {
								break lookForNewDirection;
							}
						}
						for(double x = (xMin +1); x < xMax; x += 1) { // look x------------>
							for(double y = (yMin +1); y < yMax; y += 1) {
								Location l = new Location(location.getWorld(), x, y, z);
								if(!l.getBlock().getType().equals(Material.AIR)) {
									break lookForNewDirection;
								}
							}
						}
						for(double y = (yMin +1); y < yMax; y += 1) { // look y
							Location l1 = new Location(location.getWorld(), xMin, y, z);
							Location l2 = new Location(location.getWorld(), xMax, y, z);
							if(!l1.getBlock().getType().equals(portalMaterial) || !l2.getBlock().getType().equals(portalMaterial)) {
								break lookForNewDirection;
							}
						}
						e.setCancelled(true);
						for(double x = (xMin +1); x < xMax; x++) {
							for(double y = (yMin +1); y < yMax; y++) {
								Location l = new Location(location.getWorld(), x, y, z);
								Block b = l.getBlock();
								b.setType(Material.NETHER_PORTAL);
								b.getState().update();
							}
						}
						
					} else if(directions[2] && directions[1] && directions[5] && directions[4]) {// z
						if(zMax - zMin > portal.getMaxPortalSize()) {
							break lookForNewDirection;
						}
						double x = location.getX();
						for(double z = (zMin +1); z < zMax; z += 1) { // look z
							Location l1 = new Location(location.getWorld(), x, yMin, z);
							Location l2 = new Location(location.getWorld(), x, yMax, z);
							if(!l1.getBlock().getType().equals(portalMaterial) || !l2.getBlock().getType().equals(portalMaterial)) {
								break lookForNewDirection;
							}
						}
						for(double z = (zMin +1); z < zMax; z += 1) { // look z------------->
							for(double y = (yMin +1); y < yMax; y += 1) {
								Location l = new Location(location.getWorld(), x, y, z);
								if(!l.getBlock().getType().equals(Material.AIR)) {
									break lookForNewDirection;
								}
							}
						}
						for(double y = (yMin +1); y < yMax; y += 1) { // look y
							Location l1 = new Location(location.getWorld(), x, y, zMin);
							Location l2 = new Location(location.getWorld(), x, y, zMax);
							if(!l1.getBlock().getType().equals(portalMaterial) || !l2.getBlock().getType().equals(portalMaterial)) {
								break lookForNewDirection;
							}
						}
						e.setCancelled(true);
						for(double z = (zMin +1); z < zMax; z++) {
							for(double y = (yMin +1); y < yMax; y++) {
								Location l = new Location(location.getWorld(), x, y, z);
								Block b = l.getBlock();
								b.setType(Material.NETHER_PORTAL);
								//b.setData((byte) 2, true);//2 == z
								BlockData bd = b.getBlockData();
								if(bd instanceof Rotatable){
								Rotatable rot = (Rotatable) bd;
									rot.setRotation(BlockFace.NORTH);
								}
								b.setBlockData(bd);
								b.getState().update();
							}
						}
					}
					
				}
			}
		}
	}
	
	List<Vector> getFrame (boolean isZ){
		List<Vector> vectors = new ArrayList<Vector>();
		vectors.add(new Vector(-1, -1, 0));
		vectors.add(new Vector(0, -1, 0));
		vectors.add(new Vector(1, -1, 0));
		vectors.add(new Vector(2, -1, 0));
		vectors.add(new Vector(2, 0, 0));
		vectors.add(new Vector(2, 1, 0));
		vectors.add(new Vector(2, 2, 0));
		vectors.add(new Vector(2, 3, 0));
		vectors.add(new Vector(1, 3, 0));
		vectors.add(new Vector(0, 3, 0));
		vectors.add(new Vector(-1, 3, 0));
		vectors.add(new Vector(-1, 2, 0));
		vectors.add(new Vector(-1, 1, 0));
		vectors.add(new Vector(-1, 0, 0));
		if(isZ) {
			for(int i = 0; i < vectors.size(); i++) {
				Vector v = vectors.get(i);
				vectors.set(i, new Vector(v.getZ(), v.getY(), v.getX()));
			}
		}
		return vectors;
	}
	
	List<Vector> getPortal (boolean isZ){
		List<Vector> vectors = new ArrayList<Vector>();
		vectors.add(new Vector(0, 0, 0));
		vectors.add(new Vector(1, 0, 0));
		vectors.add(new Vector(0, 1, 0));
		vectors.add(new Vector(1, 1, 0));
		vectors.add(new Vector(0, 2, 0));
		vectors.add(new Vector(1, 2, 0));
		if(isZ) {
			for(int i = 0; i < vectors.size(); i++) {
				Vector v = vectors.get(i);
				vectors.set(i, new Vector(v.getZ(), v.getY(), v.getX()));
			}
		}
		return vectors;
	}
	
	List<Vector> getPortalFrame (boolean isZ){
		List<Vector> vectors = new ArrayList<Vector>();
		vectors.add(new Vector(0, 0, 0));
		vectors.add(new Vector(1, 0, 0));
		vectors.add(new Vector(2, 0, 0));
		vectors.add(new Vector(3, 0, 0));
		vectors.add(new Vector(3, 1, 0));
		vectors.add(new Vector(3, 2, 0));
		vectors.add(new Vector(3, 3, 0));
		vectors.add(new Vector(3, 4, 0));
		vectors.add(new Vector(2, 4, 0));
		vectors.add(new Vector(1, 4, 0));
		vectors.add(new Vector(0, 4, 0));
		vectors.add(new Vector(0, 3, 0));
		vectors.add(new Vector(0, 2, 0));
		vectors.add(new Vector(0, 1, 0));
		if(isZ) {
			for(int i = 0; i < vectors.size(); i++) {
				Vector v = vectors.get(i);
				vectors.set(i, new Vector(v.getZ(), v.getY(), v.getX()));
			}
		}
		return vectors;
	}
	
	List<Vector> getCross (){
		List<Vector> vectors = new ArrayList<Vector>();
		vectors.add(new Vector(1, 0, 0));
		vectors.add(new Vector(0, 1, 0));
		vectors.add(new Vector(0, 0, 1));
		vectors.add(new Vector(-1, 0, 0));
		vectors.add(new Vector(0, -1, 0));
		vectors.add(new Vector(0, 0, -1));
		return vectors;
	}
	
	int getIndex (Vector vector){
		if(vector.equals(new Vector(1, 0, 0))) {
			return 0;
		} else if(vector.equals(new Vector(0, 1, 0))) {
			return 1;
		} else if(vector.equals(new Vector(0, 0, 1))) {
			return 2;
		} else if(vector.equals(new Vector(-1, 0, 0))) {
			return 3;
		} else if(vector.equals(new Vector(0, -1, 0))) {
			return 4;
		} else {
			return 5;
		}
	}
	
}