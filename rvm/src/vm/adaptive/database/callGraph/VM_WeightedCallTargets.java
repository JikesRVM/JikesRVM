/*
 * (C) Copyright IBM Corp. 2004
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.classloader.VM_Method;

/**
 * A collection of weighted call targets.
 * Depending on the size of the callee set, we use different subclasses
 * that optimize the time/space tradeoffs.
 *
 * @author Dave Grove
 */
public abstract class VM_WeightedCallTargets {

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
   * WeightedCallTargets accordingly to avoid losing the update.
   */
  public final VM_WeightedCallTargets incrementCount(VM_Method target) {
    return augmentCount(target, 1);
  }

  /**
   * Augment the weight associated with the argument method by the argument amount.
   * NOTE: This method may change the representation of the target
   * method.  The caller must be sure to update their backing store of
   * WeightedCallTargets accordingly to avoid losing the update.
   */
  public abstract VM_WeightedCallTargets augmentCount(VM_Method target, double amount);

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
   * @param desired VM_Method that is the only statically possible target
   * @return the filtered call targets or null if no such target exisits
   */
  public abstract VM_WeightedCallTargets filter(VM_Method goal);
  
  public static VM_WeightedCallTargets create(VM_Method target, double weight) {
    return new SingleTarget(target, weight);
  }

  /**
   * Used by visitTargets
   */
  public static interface Visitor {
    public void visit(VM_Method target, double weight);
  }
  
  /**
   * An implementation for storing a call site distribution that has a single target.
   */
  private static final class SingleTarget extends VM_WeightedCallTargets {
    private final VM_Method target;
    private float weight;

    SingleTarget(VM_Method t, double w) {
      target = t;
      weight = (float)w;
    }
      
    public final void visitTargets(Visitor func) {
      func.visit(target, weight);
    }

    public final VM_WeightedCallTargets augmentCount(VM_Method t, double v) {
      if (target.equals(t)) {
        weight += v;
        return this;
      } else {
        MultiTarget ms = new MultiTarget();
        ms.augmentCount(target, weight);
        ms.augmentCount(t, v);
        return ms;
      }
    }

    public final void decay(double rate) {
      weight /= rate;
    }

    public final double totalWeight() { return weight; }

    public VM_WeightedCallTargets filter(VM_Method goal) {
      return (goal.equals(target)) ? this : null;
    }
  }
  
  /**
   * An implementation for storing a call site distribution that has multiple targets.
   */
  private static final class MultiTarget extends VM_WeightedCallTargets {
    VM_Method[] methods = new VM_Method[5];
    float[] weights = new float[5];
    
    public final synchronized void visitTargets(Visitor func) {
      // Typically expect elements to be "almost" sorted due to previous sorting operations.
      // When this is true, expected time for insertion sort is O(n).
      for (int i=1; i<methods.length; i++) {
        VM_Method m = methods[i];
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

    public final synchronized VM_WeightedCallTargets augmentCount(VM_Method t, double v) {
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
        VM_Method[] newM = new VM_Method[methods.length * 2];
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

    public final synchronized void decay(double rate) {
      for (int i=0; i<weights.length; i++) {
        weights[i] /= rate;
      }
    }

    public final synchronized double totalWeight() {
      double sum = 0;
      for (int i=0; i<weights.length; i++) {
        sum += weights[i];
      }
      return sum;
    }

    public final synchronized VM_WeightedCallTargets filter(VM_Method goal) {
      for (int i=0; i<methods.length; i++) {
        if (goal.equals(methods[i])) {
          return VM_WeightedCallTargets.create(methods[i], weights[i]);
        }
      }
      return null;
    }
  }
}
