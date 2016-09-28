/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.ucsc.cs.donbot;

/**
 *
 * @author Alessandro
 */
public abstract class Behavior {
    public float weight = 1.0f;
    public abstract void run();
    public abstract boolean enabled();
    public float weight(){
        return weight;
    }
    public void setWeight(float weight) {
        this.weight = weight;
    }
}
