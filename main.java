package playtime.blockynights;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

public class main extends JavaPlugin implements Listener {

	private Map<String, Long> playtime = new HashMap<String, Long>();
	private Map<String, Long> afktime = new HashMap<String, Long>();
	private Map<String, Long> activetime = new HashMap<String, Long>();
	
	static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";  
	static final String DB_URL = "jdbc:mysql://localhost/playtime";
	static final String DB_OLD = "jdbc:mysql://localhost/stats";
	static final String USER = "user";
	static final String PASS = "password";
	
	private Connection connect = null;
	private Statement statement = null;
	private ResultSet resultSet = null;
	
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		scheduler.scheduleSyncRepeatingTask(this, new Runnable() {
			@Override
			public void run() {
				playerAfkScan();
			}
		}, 0,6000L);
		scheduler.scheduleSyncRepeatingTask(this, new Runnable() {
			@Override
			public void run() {
				updateMysqlFromCache();
			}
		}, 0,18000L);
	}
	
	public void onDisable() {

	}
	
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		event.setJoinMessage(null);
		if (event.getPlayer().hasPlayedBefore()) {
		 if (isPlayerInCache(event.getPlayer().getDisplayName())) {
		 }
		} else { 
			addPlayerMySQL(event.getPlayer(),event.getPlayer().getUniqueId().toString());
			if (isPlayerInCache(event.getPlayer().getDisplayName())) {
			 }
		}
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		event.setQuitMessage(null);
		 if (isPlayerInCache(event.getPlayer().getDisplayName())) {
			 removePlayerFromCache(event.getPlayer().getDisplayName());
		 }
	}
		
	@EventHandler
	public void onAsyncChat(AsyncPlayerChatEvent event) {
		if (event.getPlayer() != null) {
			updateNotAfk(event.getPlayer().getDisplayName());
		}
	}
	
	@EventHandler
	public void onInterAct(PlayerInteractEvent event) {
		if (event.getPlayer() != null) {
			updateNotAfk(event.getPlayer().getDisplayName());
		}
	}
	
	@EventHandler
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event){
		if (event.getPlayer() != null) {
				updateNotAfk(event.getPlayer().getDisplayName());
		}
	}

	@EventHandler
	public void onItemHeldChange(PlayerItemHeldEvent event){
		if (event.getPlayer() != null) {
			updateNotAfk(event.getPlayer().getDisplayName());
		}
	}
	
	 @EventHandler
	 public void onPlayerToggleSneak(PlayerToggleSneakEvent event){
		 if (event.getPlayer() != null) {
				updateNotAfk(event.getPlayer().getDisplayName());
		}
	}
	 
	 @EventHandler
	  public void onPlayerDropItem(PlayerDropItemEvent event){
			 if (event.getPlayer() != null) {
					updateNotAfk(event.getPlayer().getDisplayName());
			}
		}
	 
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		Player player = (Player) sender;
		if ((cmd.getName().equalsIgnoreCase("playtime") && sender instanceof Player) && (args.length == 0)) {
			playertime(player,player.getDisplayName());
		}
		if ((cmd.getName().equalsIgnoreCase("playtime") && sender instanceof Player) && (args.length == 1) && (sender.hasPermission("playtime.admin"))) {
			playtime(player,args[0]);
		}
		if ((cmd.getName().equalsIgnoreCase("playtime") && (args.length == 1) && (args[0].equals("Fetch")) && sender instanceof Player) && (sender.isOp())) {
			sender.sendMessage("Lets do this!");
			fetchOldData();
		}
		if ((cmd.getName().equalsIgnoreCase("playtime") && (args.length == 1) && (args[0].equals("Update")) && sender instanceof Player) && (sender.isOp())) {
			sender.sendMessage("Lets do this!");
			updateMysqlFromCache();
		}
		if ((cmd.getName().equalsIgnoreCase("playtime") && (args.length == 1) && (args[0].equals("Debug")) && sender instanceof Player) && (sender.isOp())) {
			System.out.print(player.getUniqueId().toString());
			System.out.print(playtime.get(player.getDisplayName()));
			System.out.print(afktime.get(player.getDisplayName()));
			System.out.print(activetime.get(player.getDisplayName()));
		}
		return true;
	}
		
	// Afk check - updating
	
	private void playerAfkScan() {
		long unixTime = (System.currentTimeMillis() / 1000L) - 300L;
		for (Entry<String, Long> entry : afktime.entrySet())
		{
			if (entry.getValue() > unixTime) {
				Long newplaytime = playtime.get(entry.getKey()) + 300L;
				playtime.put(entry.getKey(),newplaytime);
			}
			if (entry.getValue() < unixTime) {
				Long newafktime = activetime.get(entry.getKey()) + 300L;
				activetime.put(entry.getKey(),newafktime);
			}
		}
	}
	
	// Updating players because they where not afk
	
	private void updateNotAfk(String p) {
		long unixTime = System.currentTimeMillis() / 1000L;
		afktime.put(p,unixTime);
		}
	
	// Checks if user is in cache else adds him
	
	private boolean isPlayerInCache(String player) {
			if (playtime.get(player) != null && playtime.get(player) > 0) {
				return true;
			} else { getPlayerMySQL(player); }
			return false;
	}
	
	// Add player to cache
	
	private void addPlayerInCache(String p,Long playertime) {
		long unixTime = System.currentTimeMillis() / 1000L;
		playtime.put(p,playertime);
		afktime.put(p,unixTime);
		activetime.put(p,new Long(0));
	}
	
	// remove player from cache
	
	private void removePlayerFromCache(String p) {
	playtime.remove(p);
	afktime.remove(p);
	activetime.remove(p);
	}
	
	// Player changed name
	
	private void playerChangedName(String player,String oldnames) {
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.hasPermission("playtime.notify")) {
				p.sendMessage("§e» §3"+player+"§b Changed his name from:§3 "+oldnames+" §e«");
				updateNameChangeMySQL(player,oldnames);
			}
		}
	}
	
	// Update players new name in MySQL
	
	private void updateNameChangeMySQL(String playername,String oldnames) {
		String uuid = Bukkit.getServer().getPlayer(playername).getUniqueId().toString();
		 try{
		      Class.forName("com.mysql.jdbc.Driver");
		      connect = DriverManager.getConnection(DB_URL,USER,PASS);
		      statement = connect.createStatement();
		      String sql;
		      String oldy = oldnames+"-"+playername;
		      sql = "UPDATE info SET username='"+playername+"', old_names='"+oldy+"' WHERE uuid='"+uuid+"' ;";
		      statement.executeUpdate(sql);
		      ;statement.close();connect.close();
	   }catch(SQLException se){se.printStackTrace();}catch(Exception e){e.printStackTrace();}
 		finally{try{if(statement!=null)statement.close();}catch(SQLException se2){}try{if(connect!=null)connect.close();}catch(SQLException se){se.printStackTrace();}}
	}
	
	// Get player info from MySQL and check for name //
	
	private void getPlayerMySQL(String player) {
		String uuid = Bukkit.getServer().getPlayer(player).getUniqueId().toString();
		try{
		      Class.forName("com.mysql.jdbc.Driver");
		      connect = DriverManager.getConnection(DB_URL,USER,PASS);
		      statement = connect.createStatement();
		      String sql;
		      sql = "SELECT username,old_names,playtime from info WHERE uuid='"+uuid+"';";
		      resultSet = statement.executeQuery(sql);
		      if (resultSet.next()) {
		    	  addPlayerInCache(resultSet.getString("username"),new Long(resultSet.getInt("playtime")));
		      if (!resultSet.getString("username").equals(player)) {
		    	 playerChangedName(player,resultSet.getString("old_names"));
		      	}
		      }
		      resultSet.close();statement.close();connect.close(); 
	   }catch(SQLException se){se.printStackTrace();}catch(Exception e){e.printStackTrace();}
 		finally{try{if(statement!=null)statement.close();}catch(SQLException se2){}try{if(connect!=null)connect.close();}catch(SQLException se){se.printStackTrace();}}
	}
	
	// Add player to MySQL //
	private void addPlayerMySQL(Player player,String uuid) {
		 try{
		      Class.forName("com.mysql.jdbc.Driver");
		      connect = DriverManager.getConnection(DB_URL,USER,PASS);
		      statement = connect.createStatement();
		      String sql;
		      sql = "INSERT INTO info(uuid,username,playtime,afktime,old_names)"
		      + "VALUES ('"+uuid+"','"+player.getDisplayName()+"','1','0','"+player.getDisplayName()+"');";
		      statement.executeUpdate(sql);
		      ;statement.close();connect.close();
	   }catch(SQLException se){se.printStackTrace();}catch(Exception e){e.printStackTrace();}
  		finally{try{if(statement!=null)statement.close();}catch(SQLException se2){}try{if(connect!=null)connect.close();}catch(SQLException se){se.printStackTrace();}}
	Bukkit.getServer().broadcastMessage("§e» §bWelcome §3"+player.getDisplayName()+"§b To BlockyNights! §e«");
	}

	
	// Fetch old data, will only be used 1 time
	private void fetchOldData() {
		   try{
			      Class.forName("com.mysql.jdbc.Driver");
			      connect = DriverManager.getConnection(DB_OLD,USER,PASS);
			      statement = connect.createStatement();
			      String sql;
			      sql = "SELECT uuid,name,playtime from stats_players;";
			      resultSet = statement.executeQuery(sql);
			      while (resultSet.next()) {
			    	  String uuid = resultSet.getString("uuid");
			    	  String name = resultSet.getString("name");
			    	  int playtime = resultSet.getInt("playtime");
			    	  insertOldData(uuid,name,playtime);
			      }
			      resultSet.close();statement.close();connect.close(); 
		   }catch(SQLException se){se.printStackTrace();}catch(Exception e){e.printStackTrace();}
	   		finally{try{if(statement!=null)statement.close();}catch(SQLException se2){}try{if(connect!=null)connect.close();}catch(SQLException se){se.printStackTrace();}}
	}
	private void insertOldData(String uuid,String name, int playtime) {
		   try{
			      Class.forName("com.mysql.jdbc.Driver");
			      connect = DriverManager.getConnection(DB_URL,USER,PASS);
			      statement = connect.createStatement();
			      String sql;
			      double play = (double) playtime * 0.7;
			      sql = "INSERT INTO info(uuid,username,playtime,afktime,old_names)"
			      + "VALUES ('"+uuid+"','"+name+"','"+play+"','0','none');";
			      statement.executeUpdate(sql);
			      statement.close();connect.close();
			      System.out.print("Adding: " +uuid+ " " +name+ " " +playtime );
		   }catch(SQLException se){se.printStackTrace();}catch(Exception e){e.printStackTrace();}
	   		finally{try{if(statement!=null)statement.close();}catch(SQLException se2){}try{if(connect!=null)connect.close();}catch(SQLException se){se.printStackTrace();}}
	}
	
	// Update cache to MySQL
	private void updateMysqlFromCache() {
			for (Entry<String, Long> entry : afktime.entrySet())
			{
				try{
				      Class.forName("com.mysql.jdbc.Driver");
				      connect = DriverManager.getConnection(DB_URL,USER,PASS);
				      statement = connect.createStatement();
				      String sql;
				      sql = "UPDATE info SET playtime='"+playtime.get(entry.getKey())+"' WHERE username='"+entry.getKey()+"';";
				      statement.executeUpdate(sql);
				      statement.close();connect.close();
			   }catch(SQLException se){se.printStackTrace();}catch(Exception e){e.printStackTrace();}
		   		finally{try{if(statement!=null)statement.close();}catch(SQLException se2){}try{if(connect!=null)connect.close();}catch(SQLException se){se.printStackTrace();}}
			}
	}
	
	// Playtime reply
	
	private void playertime(Player player,String p) {
		try{
		      Class.forName("com.mysql.jdbc.Driver");
		      connect = DriverManager.getConnection(DB_URL,USER,PASS);
		      statement = connect.createStatement();
		      String sql;
		      sql = "SELECT playtime FROM info WHERE username='"+p+"';";
		      resultSet = statement.executeQuery(sql);
		      if (resultSet.next()) {
		    	 double playtime = Math.round((resultSet.getDouble("playtime")/60/60) *100);
		    	 playtime = playtime/100;
		    	 player.sendMessage("§3Your playtime is: §b"+playtime+"§3 Hours");
		      } else { player.sendMessage("Could not find you in the Database - contact hkenneth"); }
		      resultSet.close();statement.close();connect.close(); 
	   }catch(SQLException se){se.printStackTrace();}catch(Exception e){e.printStackTrace();}
 		finally{try{if(statement!=null)statement.close();}catch(SQLException se2){}try{if(connect!=null)connect.close();}catch(SQLException se){se.printStackTrace();}}
	}
	
	private void playtime(Player player,String p) {
		try{
		      Class.forName("com.mysql.jdbc.Driver");
		      connect = DriverManager.getConnection(DB_URL,USER,PASS);
		      statement = connect.createStatement();
		      String sql;
		      sql = "SELECT playtime FROM info WHERE username='"+p+"';";
		      resultSet = statement.executeQuery(sql);
		      if (resultSet.next()) {
		    	 double playtime = Math.round((resultSet.getDouble("playtime")/60/60) *100);
		    	 playtime = playtime/100;
		    	 player.sendMessage("§b"+p+"§3 playtime is: §b"+playtime+"§3 Hours");
		      } else { player.sendMessage("Could not find "+p+" in the Database - contact hkenneth"); }
		      resultSet.close();statement.close();connect.close(); 
	   }catch(SQLException se){se.printStackTrace();}catch(Exception e){e.printStackTrace();}
 		finally{try{if(statement!=null)statement.close();}catch(SQLException se2){}try{if(connect!=null)connect.close();}catch(SQLException se){se.printStackTrace();}}
	}

}


