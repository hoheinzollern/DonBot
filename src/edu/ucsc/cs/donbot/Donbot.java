package edu.ucsc.cs.donbot;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.Timer;
import java.util.TimerTask;
import javax.vecmath.*;

import cz.cuni.amis.introspection.java.JProp;
import cz.cuni.amis.pogamut.base.agent.navigation.PathEventType;
import cz.cuni.amis.pogamut.base.agent.navigation.PathExecutorListener;
import cz.cuni.amis.pogamut.base.communication.worldview.listener.annotation.EventListener;
import cz.cuni.amis.pogamut.base.communication.worldview.object.WorldObjectId;
import cz.cuni.amis.pogamut.base.utils.guice.AgentScoped;
import cz.cuni.amis.pogamut.base.utils.math.DistanceUtils;
import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.ut2004.agent.module.sensomotoric.Weapon;
import cz.cuni.amis.pogamut.ut2004.agent.module.utils.TabooSet;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004Bot;
import cz.cuni.amis.pogamut.ut2004.bot.impl.UT2004BotModuleController;
import cz.cuni.amis.pogamut.ut2004.communication.messages.ItemType;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UnrealId;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Dodge;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Initialize;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Move;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Rotate;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Shoot;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.Stop;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.StopShooting;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbcommands.TurnTo;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.BotKilled;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Item;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.NavPoint;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.Player;
import cz.cuni.amis.pogamut.ut2004.communication.messages.gbinfomessages.PlayerKilled;
import cz.cuni.amis.pogamut.ut2004.utils.MultipleUT2004BotRunner;
import cz.cuni.amis.utils.exception.PogamutException;
import edu.ucsc.cs.donbot.events.Event;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Example of Simple Pogamut bot, that randomly walks around the map searching for preys shooting at everything that
 * is in its way.
 *
 * @author Rudolf Kadlec aka ik
 * @author Jimmy
 */
@AgentScoped
public class Donbot extends UT2004BotModuleController<UT2004Bot> {

    /** boolean switch to activate engage */
    @JProp
    public boolean shouldEngage = true;
    /** boolean switch to activate pursue */
    @JProp
    public boolean shouldPursue = true;
    /** boolean switch to activate rearm */
    @JProp
    public boolean shouldRearm = true;
    /** boolean switch to activate collect items */
    @JProp
    public boolean shouldCollectItems = true;
    /** boolean switch to activate collect health */
    @JProp
    public boolean shouldCollectHealth = true;
    /** how low the health level should be to start collecting health */
    @JProp
    public int healthLevel = 90;
    /** how many bot the hunter killed */
    @JProp
    public int frags = 0;
    /** how many times the hunter died */
    @JProp
    public int deaths = 0;
    /** Whether the bot saw someone recently (within 5 seconds) */
    @JProp
    public boolean sawEnemy = false;
    public Location enemyLocation = null;
    private static Timer engageTimer = null;
    private Memory memory = null;
    private static int counterId = 0;
    private int botId;
    private final String botName;
    private Location hotspotLocation;

    @EventListener(eventClass = PlayerKilled.class)
    public void playerKilled(PlayerKilled event) {
        if (enemy == null) {
            return;
        }
        try {
            memory.remember(game.getMapName(), info.getNearestNavPoint().getLocation(), info.getNearestNavPoint().getId(), bot.getLocation(), Event.PLAYER_KILLED, game.getTime(), 1.0f);
        } catch (SQLException ex) {
            Logger.getLogger(Donbot.class.getName()).log(Level.SEVERE, null, ex);
            System.out.print(ex);
        }
        if (enemy.getId().equals(event.getId())) {
            previousState = State.OTHER;
            enemy = null;
        }
    }
    Player enemy = null;
    TabooSet<Item> tabooItems = null;

    /**
     * Bot's preparation - initialization of agent's modules take their place here.
     */
    @Override
    public void prepareBot(UT2004Bot bot) {
        tabooItems = new TabooSet<Item>(bot);

        // listeners        
        pathExecutor.addPathListener(new PathExecutorListener() {

            @Override
            public void onEvent(PathEventType eventType) {
                switch (eventType) {
                    case BOT_STUCKED:
                        if (item != null) {
                            tabooItems.add(item, 10);
                        }
                        reset();
                        break;

                    case TARGET_REACHED:
                        reset();
                        break;
                }
            }
        });
    }

    /**
     * Here we can modify initializing command for our bot.
     *
     * @return
     */
    @Override
    public Initialize getInitializeCommand() {
        // just set the name of the bot, nothing else
        return new Initialize().setName(botName);
    }

    public void reset() {
        previousState = State.OTHER;
        notMoving = 0;
        enemy = null;
        pathExecutor.stop();
        itemsToRunAround = null;
        item = null;
        hotspotLocation = null;
    }
    State previousState = State.OTHER;
    int notMoving = 0;
    // Behaviors:
    Behavior engage = new EngageBehavior();
    Behavior hit = new HitBehavior();
    Behavior medkit = new MedKitBehavior();
    Behavior pursue = new PursueBehavior();
    Behavior runAround = new RunAroundBehavior();
    Behavior hotspot = new HotSpotBehavior();
    Behavior seeItem = new SeeItemBehavior();
    Behavior stopShooting = new StopShootingBehavior();
    Behavior searchBehavior = new SearchBehavior();
    BehaviorNode root = new BehaviorNode();

    public Donbot() {
        root.addBehavior(engage);
        root.addBehavior(hit);
        root.addBehavior(medkit);
        root.addBehavior(pursue);
        root.addBehavior(hotspot);
        root.addBehavior(runAround);
        root.addBehavior(seeItem);
        root.addBehavior(stopShooting);

        botId = counterId++;
        botName = "Donbot-" + botId;

        memory = new Memory(botName);
    }

    private void setStatus(String status) {
        config.setName(botName + " [" + status + "]");
    }

    /**
     * Main method that controls the bot - makes decisions what to do next.
     * It is called iteratively by Pogamut engine every time a synchronous batch
     * from the environment is received. This is usually 4 times per second - it
     * is affected by visionTime variable, that can be adjusted in GameBots ini file in
     * UT2004/System folder.
     *
     * @throws cz.cuni.amis.pogamut.base.exceptions.PogamutException
     */
    @Override
    public void logic() {
        // global anti-stuck?
        if (!info.isMoving()) {
            ++notMoving;
            if (notMoving > 4) {
                // we're stuck - reset the bot's mind
                reset();
            }
        }
        root.run();
    }
    //////////////
    //////////////////
    // STATE ENGAGE //
    //////////////////
    //////////////
    private boolean runningToPlayer = false;

    class EngageBehavior extends Behavior {

        /**
         * Fired when bot see any enemy.
         * <ol>
         * <li> if enemy that was attacked last time is not visible than choose new enemy
         * <li> if out of ammo - switch to another weapon
         * <li> if enemy is reachable and the bot is far - run to him
         * <li> if enemy is not reachable - stand still (kind a silly, right? :-)
         * </ol>
         */
        public void run() {
            user.info("Decision is: ENGAGE");
            setStatus("ENGAGE");
            sawEnemy = true;
            if (engageTimer != null) {
                engageTimer.cancel();
            }
            boolean shooting = false;
            double distance = Double.MAX_VALUE;

            // 1) pick new enemy if the old one has been lost
            if (previousState != State.ENGAGE || enemy == null || !enemy.isVisible()) {
                // pick new enemy
                enemy = players.getNearestVisiblePlayer(players.getVisibleEnemies().values());
                if (info.isShooting()) {
                    // stop shooting
                    getAct().act(new StopShooting());
                }
                runningToPlayer = false;
            }
            // 2) if out of ammo - switch to another weapon
            if (weaponry.getCurrentPrimaryAmmo() == 0 && weaponry.hasLoadedWeapon()) {
                user.info("No ammo - switching weapon");
                // obtain any loaded weapon
                Weapon weapon = weaponry.getLoadedWeapons().values().iterator().next();
                // change the weapon
                weaponry.changeWeapon(weapon);
            } else {
                // check whether you do not have better weapon
                Weapon currentWeapon = weaponry.getCurrentWeapon();
                Weapon switchWeapon = null;
                for (Weapon weapon : weaponry.getLoadedRangedWeapons().values()) {
                    if (weapon.getDescriptor().getPriDamage() > currentWeapon.getDescriptor().getPriDamage()) {
                        switchWeapon = weapon;
                    }
                }
                if (switchWeapon != null) {
                    weaponry.changeWeapon(switchWeapon);
                }
            }

            if (enemy != null) {
                // 3) if not shooting at enemyID - start shooting
                distance = info.getLocation().getDistance(enemy.getLocation());

                // 4) should shoot?
                if (weaponry.getCurrentWeapon() != null) {
                    // it is worth shooting
                    user.info("Shooting at enemy!!!");
                    shouldPursue = false;
                    getAct().act(new Shoot().setTarget(enemy.getId()));
                    shooting = true;
                    shouldCollectItems = false;
                    Vector3d dodgeDir = new Vector3d(random.nextFloat(),random.nextFloat(),random.nextFloat());
                    if (random.nextFloat() < .3) {
                        getAct().act(new Dodge(dodgeDir));
                        getAct().act(new TurnTo(enemy.getId(), null, null));
                        System.out.print(dodgeDir.toString());
                        //getAct().act(new Rotate(dodgedir, USER_LOG_CATEGORY_ID))
                    }
                }
                enemyLocation = enemy.getLocation();
            }

            // 5) if enemy is far - run to him

            int decentDistance = Math.round(random.nextFloat() * 800) + 200;
            if (enemy != null && (!enemy.isVisible() || !shooting || decentDistance < distance)) {
                shouldPursue=true;
                if (!runningToPlayer) {
                    pathExecutor.followPath(pathPlanner.computePath(enemy));
                    runningToPlayer = true;
                }
            } else {
                if (engageTimer == null) {
                    engageTimer = new Timer("Decay timer", true);
                    engageTimer.schedule(new TimerTask() {

                        public void run() {
                            sawEnemy = false;
                            enemyLocation = null;
                        }
                    }, 5 * 1000);
                }
                //pursue.setWeight(10.0f);
                runningToPlayer = false;
                pathExecutor.stop();
                getAct().act(new Stop());
                previousState = State.ENGAGE;
            }
        }

        public boolean enabled() {
            return shouldEngage && players.canSeeEnemies() && weaponry.hasLoadedWeapon();
        }
    }

    class StopShootingBehavior extends Behavior {

        public void run() {
            getAct().act(new StopShooting());
        }

        public boolean enabled() {
            return info.isShooting() || info.isSecondaryShooting();
        }
    }

    //////////////
    // HOT SPOT //
    //////////////
    public class HotSpotBehavior extends Behavior {

        @Override
        public void run() {
            user.info("hotspot behavior");
            setStatus("HOTSPOT");
            if (hotspotLocation == null) {
                System.out.println("opt1");
                hotspotLocation = memory.getHotspot();
            }
            if (hotspotLocation != null && bot.getLocation().getDistance(hotspotLocation) < 10.0f) {
                System.out.println("opt2");
            Object np[] = bot.getWorldView().getAll(NavPoint.class).values().toArray();
                hotspotLocation = ((NavPoint)np[random.nextInt(np.length)]).getLocation();
            }
            if (hotspotLocation == null) {
                System.out.println("opt3");
            Object np[] = bot.getWorldView().getAll(NavPoint.class).values().toArray();
                hotspotLocation = ((NavPoint)np[random.nextInt(np.length)]).getLocation();
            }
            if (previousState != State.HOTSPOT)
                pathExecutor.followPath(pathPlanner.computePath(hotspotLocation));
            previousState = State.HOTSPOT;
        }

        @Override
        public boolean enabled() {
            return previousState != State.GRAB && previousState != State.MEDKIT && previousState != State.PURSUE
                    && (hotspotLocation == null || bot.getLocation().getDistance(hotspotLocation) > 100.0f);
        }

    }

    ///////////
    ///////////////
    // STATE HIT //
    ///////////////
    ///////////-
    public class HitBehavior extends Behavior {

        public void run() {
            user.info("Decision is: HIT");
            getAct().act(new Rotate().setAmount(32000));
            previousState = State.OTHER;
        }

        @Override
        public boolean enabled() {
            return senses.isBeingDamaged();
        }
    }

    //////////////
    //////////////////
    // STATE PURSUE //
    //////////////////
    //////////////
    class PursueBehavior extends Behavior {

        /**
         * State pursue is for pursuing enemy who was for example lost behind a corner.
         * How it works?:
         * <ol>
         * <li> initialize properties
         * <li> obtain path to the enemy
         * <li> follow the path - if it reaches the end - set lastEnemy to null - bot would have seen him before or lost him once for all
         * </ol>
         */
        public void run() {
            user.info("Decision is: PURSUE");
            setStatus("PURSUE");
            if (previousState != State.PURSUE) {
                pursueCount = 0;
                pathExecutor.followPath(pathPlanner.computePath(enemy));
            }
            ++pursueCount;
            if (pursueCount > 30) {
                shouldCollectItems = true;
                reset();
            } else {
                previousState = State.PURSUE;
            }
        }
        int pursueCount = 0;

        public boolean enabled() {
            return enemy != null && shouldPursue && weaponry.hasLoadedWeapon(); // !enemy.isVisible() because of 2)
        }
    }

    //////////////
    //////////////////
    // STATE MEDKIT //
    //////////////////
    //////////////
    class MedKitBehavior extends Behavior {

        public void run() {
            user.info("Decision is: MEDKIT");
            setStatus("MEDKIT");
            if (previousState != State.MEDKIT) {
                List<Item> healths = new LinkedList();
                healths.addAll(items.getSpawnedItems(ItemType.HEALTH_PACK).values());
                if (healths.isEmpty()) {
                    healths.addAll(items.getSpawnedItems(ItemType.MINI_HEALTH_PACK).values());
                }
                Set<Item> okHealths = tabooItems.filter(healths);
                if (okHealths.isEmpty()) {
                    user.log(Level.WARNING, "No suitable health to run for.");
                    hotspot.run();
                    return;
                }
                item = DistanceUtils.getNearest(okHealths, info.getLocation());
                pathExecutor.followPath(pathPlanner.computePath(item));
            }
            previousState = State.MEDKIT;
        }

        public boolean enabled() {
            return info.getHealth() < healthLevel && canRunAlongMedKit() && previousState != State.GRAB;
        }
    }
    ////////////////
    ////////////////////
    // STATE SEE ITEM //
    ////////////////////
    ////////////////
    Item item = null;

    class SeeItemBehavior extends Behavior {

        public void run() {
            user.info("Decision is: SEE ITEM");
            setStatus("SEE ITEM");

            if (item != null && item.getLocation().getDistance(info.getLocation()) < 100) {
                reset();
            }

            if (previousState != State.GRAB) {
                item = DistanceUtils.getNearest(items.getVisibleItems().values(), info.getLocation());
                if (item.getDescriptor().getItemCategory() == ItemType.Category.AMMO && info.getCurrentAmmo() == 100)
                    reset();
                else if (item.getDescriptor().getItemCategory() == ItemType.Category.HEALTH && info.isHealthy())
                    reset();
                else if (item.getLocation().getDistance(info.getLocation()) < 300) {
                    getAct().act(new Move().setFirstLocation(item.getLocation()));
                    System.out.println("GRABBING! " + item.getDescriptor());
                    System.out.println("AMMO: " + info.getCurrentAmmo().toString() + "ARMOR: " + info.getArmor().toString());
                } else {
                    pathExecutor.followPath(pathPlanner.computePath(item));
                }
            }
            previousState = State.GRAB;
        }

        public boolean enabled() {
            return shouldCollectItems && !items.getVisibleItems().isEmpty();
        }
    }

    private boolean canRunAlongMedKit() {
        boolean result = !items.getSpawnedItems(ItemType.HEALTH_PACK).isEmpty()
                || !items.getSpawnedItems(ItemType.MINI_HEALTH_PACK).isEmpty();
        return result;
    }
    ////////////////////////
    ////////////////////////////
    // STATE RUN AROUND ITEMS //
    ////////////////////////////
    ////////////////////////
    List<Item> itemsToRunAround = null;

    class RunAroundBehavior extends Behavior {

        public void run() {
            user.info("Decision is: ITEMS");
            setStatus("ITEMS");
            if (previousState != State.ITEMS) {
                itemsToRunAround = new LinkedList<Item>(items.getSpawnedItems().values());
                Set<Item> items = tabooItems.filter(itemsToRunAround);
                if (items.isEmpty()) {
                    user.log(Level.WARNING, "No item to run for...");
                    reset();
                    return;
                }
                item = items.iterator().next();
                pathExecutor.followPath(pathPlanner.computePath(item));
            }
            previousState = State.ITEMS;

        }

        public boolean enabled() {
            return previousState != State.HOTSPOT;
        }
    }

    class SearchBehavior extends Behavior {

        public void run() {
            user.info("Decision is: SEARCH");
            setStatus("SEARCH");
            pathExecutor.followPath(pathPlanner.computePath(enemyLocation));
            previousState = State.SEARCH;

        }

        public boolean enabled() {
            return sawEnemy;
        }
    }

    ////////////
    ////////////////
    // BOT KILLED //
    ////////////////
    ////////////
    @Override
    public void botKilled(BotKilled event) {
        user.info("Bot died;logging to database location" + info.getNearestNavPoint().toString());
        itemsToRunAround = null;
        enemy = null;
    }

////////////////////////////////////////////
////////////////////////////////////////////
////////////////////////////////////////////
    public static void main(String args[]) throws PogamutException {
        new MultipleUT2004BotRunner<UT2004Bot>(1, Donbot.class, "Hunter").startAgent();
    }
}
