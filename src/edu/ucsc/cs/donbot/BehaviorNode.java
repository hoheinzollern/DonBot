/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.ucsc.cs.donbot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 *
 * @author Alessandro
 */
public class BehaviorNode extends Behavior {
    private List<Behavior> children;
    private Behavior lastChild = null;
    private float curWeight;

    public BehaviorNode() {
        children = new ArrayList<Behavior>();
    }

    public void addBehavior(Behavior b) {
        children.add(b);
    }

    public void run() {
        if (lastChild != null && lastChild.enabled()) {
            lastChild.run();
        }
        // Build the list of available behaviors
        List<Behavior> available = new LinkedList<Behavior>();
        List<Float> sumWeights = new LinkedList<Float>();

        Iterator<Behavior> itB = children.iterator();
        float total = 0.0f;
        while (itB.hasNext()) {
            Behavior behavior = itB.next();
            if (behavior.enabled()) {
                total += behavior.weight();
                available.add(behavior);
                sumWeights.add(total);
            }
        }

        // Choose a behavior based on the weights
        Random random = new Random();
        float value = random.nextFloat() * total;
        itB = available.iterator();
        Iterator<Float> itF = sumWeights.iterator();
        while (itF.next() < value) {
            itB.next();
        }
        Behavior bi = itB.next();
        lastChild = bi;
        bi.run();
    }

    public boolean enabled() {
        return true;
    }

    public float weight() {
        return curWeight;
    }

    public void setWeight(float weight)
    {
        curWeight = weight;
    }
}
