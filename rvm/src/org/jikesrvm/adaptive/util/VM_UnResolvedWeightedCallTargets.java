/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright 
 * Department of Computer Science,
 * University of Texas at Austin 2005
 * All rights reserved.
 */

package org.jikesrvm.adaptive.util;

import org.jikesrvm.classloader.VM_MethodReference;

/**
 * A collection of weighted call targets. In some case we can't resolve a
 * class too early in the process. So we recorded it as unresolved and 
 * resolve the method when the method is being compiled.
 *
 * @author Xianglong Huang
 */
public abstract class VM_UnResolvedWeightedCallTargets {

  /**
   * Iterate over all of the targets, evaluating the argument function on each edge.
   * NOTE: We guarentee that the targets will be iterated in monotonically decrasing
   *       edge weight. This simplifies the coding of the inlining clients that consume
   *       this information.
   * @param func the function to evaluate on each target
   */
  public abstract void visitTargets(Visitor func);

  /**
   * Augment the weight associated with the argument method by 1.
   * NOTE: This method may change the representation of the target
   * method.  The caller must be sure to update their backing store of
   * UnResolvedWeightedCallTargets accordingly to avoid losing the update.
   */
  public final VM_UnResolvedWeightedCallTargets incrementCount(VM_MethodReference target) {
    return augmentCount(target, 1);
  }

  /**
   * Augment the weight associated with the argument method by the argument amount.
   * NOTE: This method may change the representation of the target
   * method.  The caller must be sure to update their backing store of
   * UnResolvedWeightedCallTargets accordingly to avoid losing the update.
   */
  public abstract VM_UnResolvedWeightedCallTargets augmentCount(VM_MethodReference target, double amount);

  /**
   * Decay the weights of all call targets by the specified amount
   * @param rate the value to decay by
   */
  public abstract void decay(double rate);

  /**
   * totalWeight of all call targets
   */
  public abstract double totalWeight();

  /**
   * @param goal VM_MethodReference that is the only statically possible target
   * @return the filtered call targets or null if no such target exisits
   */
  public abstract VM_UnResolvedWeightedCallTargets filter(VM_MethodReference goal);
  
  public static VM_UnResolvedWeightedCallTargets create(VM_MethodReference target, double weight) {
    return new UnResolvedSingleTarget(target, weight);
  }

  /**
   * Used by visitTargets
   */
  public interface Visitor {
    void visit(VM_MethodReference target, double weight);
  }
  
  /**
   * An implementation for storing a call site distribution that has a single target.
   */
  private static final class UnResolvedSingleTarget extends VM_UnResolvedWeightedCallTargets {
    private final VM_MethodReference target;
    private float weight;

    UnResolvedSingleTarget(VM_MethodReference t, double w) {
      target = t;
      weight = (float)w;
    }
      
    public void visitTargets(Visitor func) {
      func.visit(target, weight);
    }

    public VM_UnResolvedWeightedCallTargets augmentCount(VM_MethodReference t, double v) {
      if (target.equals(t)) {
        weight += v;
        return this;
      } else {
        UnResolvedMultiTarget ms = new UnResolvedMultiTarget();
        ms.augmentCount(target, weight);
        ms.augmentCount(t, v);
        return ms;
      }
    }

    public void decay(double rate) {
      weight /= rate;
    }

    public double totalWeight() { return weight; }

    public VM_UnResolvedWeightedCallTargets filter(VM_MethodReference goal) {
      return (goal.equals(target)) ? this : null;
    }
  }
  
  /**
   * An implementation for storing a call site distribution that has multiple targets.
   */
  private static final class UnResolvedMultiTarget extends VM_UnResolvedWeightedCallTargets {
    VM_MethodReference[] methods = new VM_MethodReference[5];
    float[] weights = new float[5];
    
    public synchronized void visitTargets(Visitor func) {
      // Typically expect elements to be "almost" sorted due to previous sorting operations.
      // When this is true, expected time for insertion sort is O(n).
      for (int i=1; i<methods.length; i++) {
        VM_MethodReference m = methods[i];
        if (m != null) {
          float w = weights[i];
          int j = i;
          while (j > 0 && weights[j-1] < w) {
            methods[j] = methods[j-1];
            weights[j] = weights[j-1];
            j--;
          }
          methods[j] = m;
          weights[j] = w;
        }
      }
      
      for (int i=0; i<methods.length; i++) {
        if (methods[i] != null) {
          func.visit(methods[i], weights[i]);
        }
      }
    }      

    public synchronized VM_UnResolvedWeightedCallTargets augmentCount(VM_MethodReference t, double v) {
      int empty = -1;
      for (int i=0; i<methods.length; i++) {
        if (methods[i] != null) {
          if (methods[i].equals(t)) {
            weights[i] += v;
            return this;
          }
        } else {
          if (empty == -1) { empty = i; }
        }
      }

      // New target must be added
      if (empty == -1) {
        // must grow arrays
        empty = methods.length;
        VM_MethodReference[] newM = new VM_MethodReference[methods.length * 2];
        System.arraycopy(methods, 0, newM, 0, methods.length);
        methods = newM;
        float[] newW = new float[weights.length * 2];
        System.arraycopy(weights, 0, newW, 0, weights.length);
        weights = newW;
      }

      methods[empty] = t;
      weights[empty] = (float)v;
      return this;
    }

    public synchronized void decay(double rate) {
      for (int i=0; i<weights.length; i++) {
        weights[i] /= rate;
      }
    }

    public synchronized double totalWeight() {
      double sum = 0;
      for (float weight : weights) {
        sum += weight;
      }
      return sum;
    }

    public synchronized VM_UnResolvedWeightedCallTargets filter(VM_MethodReference goal) {
      for (int i=0; i<methods.length; i++) {
        if (goal.equals(methods[i])) {
          return VM_UnResolvedWeightedCallTargets.create(methods[i], weights[i]);
        }
      }
      return null;
    }
  }
}
