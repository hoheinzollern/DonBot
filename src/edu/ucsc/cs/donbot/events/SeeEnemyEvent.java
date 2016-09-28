/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.ucsc.cs.donbot.events;

import cz.cuni.amis.pogamut.base3d.worldview.object.Location;
import cz.cuni.amis.pogamut.ut2004.communication.messages.UnrealId;

/**
 *
 * @author Alessandro
 */
public class SeeEnemyEvent {
    private final UnrealId enemyId;
    private final Location location;

    public SeeEnemyEvent(UnrealId enemyId, Location enemyLocation) {
        this.enemyId = enemyId;
        this.location = enemyLocation;
    }

    public UnrealId getEnemyId() {
        return enemyId;
    }

    public Location getLocation() {
        return location;
    }
}
