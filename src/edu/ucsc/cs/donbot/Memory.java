/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucsc.cs.donbot;

import edu.ucsc.cs.donbot.events.Event;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Alessandro
 */
public class Memory {
    // Location status time

    private static final String sqliteClass = "org.sqlite.JDBC";
    private static final String memoryDBPath = "jdbc:sqlite::memory:";
    private static final String persistentDBPath = "jdbc:sqlite:c:\\donbot.db";
    private Connection botDB = null;
    private static Connection commonDB = null;
    private static Connection persistentDB = null;
    private static Timer timer = null;
    private static List<Memory> instances = new LinkedList<Memory>();
    private String botName = null;
    private static final int TABLE_TYPE_VOLATILE_OBSERVATION = 0x01;
    private static final int TABLE_TYPE_PERSISTENT_OBSERVATION = 0x02;
    private static final int TABLE_TYPE_NAVPOINT = 0x10;

    static {
        try {
            Class.forName(sqliteClass);
            persistentDB = DriverManager.getConnection(persistentDBPath);
            initDB(persistentDB, TABLE_TYPE_PERSISTENT_OBSERVATION | TABLE_TYPE_NAVPOINT);
            commonDB = DriverManager.getConnection(memoryDBPath);
            initDB(commonDB, TABLE_TYPE_NAVPOINT);
        } catch (SQLException ex) {
            Logger.getLogger(Memory.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Memory.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Memory(String botName) {
        try {
            this.botName = botName;
            botDB = DriverManager.getConnection(memoryDBPath);
            initDB(botDB, TABLE_TYPE_VOLATILE_OBSERVATION | TABLE_TYPE_PERSISTENT_OBSERVATION);
            instances.add(this);
            if (timer == null) {
                timer = new Timer("Decay timer", true);
                timer.scheduleAtFixedRate(new TimerTask() {

                    @Override
                    public void run() {
                        for (Memory instance : instances) {
                            instance.decay();
                        }
                    }
                }, 60 * 1000, 15 * 1000);
            }
        } catch (SQLException ex) {
            Logger.getLogger(Memory.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void initDB(Connection conn, int tableType) throws SQLException {
        Statement stmt = conn.createStatement();
        String query;
        if ((tableType & TABLE_TYPE_VOLATILE_OBSERVATION) != 0) {
            //query = "DROP TABLE volatile_observations IF NOT EXISTS;";
            //stmt.execute(query);
            query = "CREATE TABLE volatile_observations ("
                    + "row_id integer PRIMARY KEY,"
                    + "row_entry_date text,"
                    + "map_level text,"
                    + "navpoint_location text,"
                    + "navpoint_id text,"
                    + "event_location text,"
                    + "event_type text,"
                    + "event_time real,"
                    + "event_weight real"
                    + ");";
            stmt.execute(query);
        }
        if ((tableType & TABLE_TYPE_PERSISTENT_OBSERVATION) != 0) {
            query = "DROP TABLE persistent_observations;";
            //stmt.execute(query);
            query = "CREATE TABLE persistent_observations ("
                    + "row_id integer PRIMARY KEY,"
                    + "row_entry_date text,"
                    + "map_level text,"
                    + "navpoint_location text,"
                    + "navpoint_id text,"
                    + "event_location text,"
                    + "event_type text,"
                    + "event_time real,"
                    + "event_weight real"
                    + ");";
            //stmt.execute(query);
        }
        if ((tableType & TABLE_TYPE_NAVPOINT) != 0) {
            //query = "DROP TABLE navpoint IF NOT EXISTS;";
            //stmt.execute(query);
            query = "CREATE TABLE navpoint ("
                    + "row_id integer PRIMARY KEY,"
                    + "row_entry_date text,"
                    + "map_level text,"
                    + "from_navpoint_id int,"
                    + "to_navpoint_id int,"
                    + "visibility int"
                    + ");";
            //stmt.execute(query);
        }
    }

    public void decay() {
    }

    public void locate(NavPoint navCur, NavPoint navSee) {
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        instances.remove(this);
        if (instances.isEmpty()) {
            persistentDB.close();
        }
        botDB.close();
    }

    void remember(String mapName, Location nearestNavpointLocation,
            UnrealId unrealId, Location eventLocation, Event eventType, double eventTime, float eventWeight) throws SQLException {
        synchronized (persistentDB) {
            System.out.println("remember called");
            PreparedStatement prep = botDB.prepareStatement(
                    "insert into volatile_observations (row_entry_date,map_level,navpoint_location,navpoint_id,"
                    + "event_location,event_type,event_time,event_weight) "
                    + "values (datetime('now'),?,?,?,?,?,?,?);");

            prep.setString(1, mapName);
            prep.setString(2, nearestNavpointLocation.toString());
            prep.setString(3, unrealId.getStringId());
            prep.setString(4, eventLocation.toString());
            prep.setString(5, eventType.toString());
            prep.setDouble(6, eventTime);
            prep.setDouble(7, eventWeight);
            prep.execute();
        }
    }

    Location getHotspot() {
        synchronized (persistentDB) {
            try {
                Statement stmt = botDB.createStatement();
                ResultSet rs = stmt.executeQuery("select navpoint_location from volatile_observations order by row_entry_date desc limit 0,1;");
                System.out.println("Querying..");
                if (rs.next()) {
                    String location = rs.getString("navpoint_location");
                    Matcher matcher = Pattern.compile("\\[([^;]+); ([^;]+); ([^\\]]+)\\]").matcher(location);
                    System.out.println("location found");
                    if (matcher.matches()) {
                        return new Location(Double.valueOf(matcher.group(1)), Double.valueOf(matcher.group(2)), Double.valueOf(matcher.group(3)));
                    }
                }
            } catch (SQLException ex) {
                Logger.getLogger(Memory.class.getName()).log(Level.SEVERE, null, ex);
            }
            return null;
        }
    }
}
