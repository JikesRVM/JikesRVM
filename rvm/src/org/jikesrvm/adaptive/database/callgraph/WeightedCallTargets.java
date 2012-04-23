/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.database.callgraph;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.runtime.RuntimeEntrypoints;

/**
 * A collection of weighted call targets.
 * Depending on the size of the callee set, we use different subclasses
 * that optimize the time/space tradeoffs.
 */
public abstract class WeightedCallTargets {

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
  public final WeightedCallTargets incrementCount(RVMMethod target) {
    return augmentCount(target, 1);
  }

  /**
   * Augment the weight associated with the argument method by the argument amount.
   * NOTE: This method may change the representation of the target
   * method.  The caller must be sure to update their backing store of
   * WeightedCallTargets accordingly to avoid losing the update.
   */
  public abstract WeightedCallTargets augmentCount(RVMMethod target, double amount);

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
   * @param goal RVMMethod that is the statically possible target
   * @param isPrecise whether or not goal is a precise target, or should be
   *        interpreted as being the root of a virtual method family, any of which
   *        are statically possible.
   * @return the filtered call targets or null if no such target exists
   */
  public abstract WeightedCallTargets filter(RVMMethod goal, boolean isPrecise);

  public static WeightedCallTargets create(RVMMethod target, double weight) {
    return new SingleTarget(target, weight);
  }

  /**
   * Used by visitTargets
   */
  public interface Visitor {
    void visit(RVMMethod target, double weight);
  }

  /**
   * An implementation for storing a call site distribution that has a single target.
   */
  private static final class SingleTarget extends WeightedCallTargets {
    private final RVMMethod target;
    private float weight;

    SingleTarget(RVMMethod t, double w) {
      target = t;
      weight = (float) w;
    }

    @Override
    public void visitTargets(Visitor func) {
      func.visit(target, weight);
    }

    @Override
    public WeightedCallTargets augmentCount(RVMMethod t, double v) {
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

    @Override
    public void decay(double rate) {
      weight /= rate;
    }

    @Override
    public double totalWeight() { return weight; }

    @Override
    public WeightedCallTargets filter(RVMMethod goal, boolean isPrecise) {
      if (isPrecise) {
        return (goal.equals(target)) ? this : null;
      } else {
        if (goal.equals(target)) {
          return this;
        }
        if (RuntimeEntrypoints.isAssignableWith(goal.getDeclaringClass(), target.getDeclaringClass())) {
          return this;
        } else {
          return null;
        }
      }
    }
  }

  /**
   * An implementation for storing a call site distribution that has multiple targets.
   */
  private static final class MultiTarget extends WeightedCallTargets {
    RVMMethod[] methods = new RVMMethod[5];
    float[] weights = new float[5];

    @Override
    public synchronized void visitTargets(Visitor func) {
      // Typically expect elements to be "almost" sorted due to previous sorting operations.
      // When this is true, expected time for insertion sort is O(n).
      for (int i = 1; i < methods.length; i++) {
        RVMMethod m = methods[i];
        if (m != null) {
          float w = weights[i];
          int j = i;
          while (j > 0 && weights[j - 1] < w) {
            methods[j] = methods[j - 1];
            weights[j] = weights[j - 1];
            j--;
          }
          methods[j] = m;
          weights[j] = w;
        }
      }

      for (int i = 0; i < methods.length; i++) {
        if (methods[i] != null) {
          func.visit(methods[i], weights[i]);
        }
      }
    }

    @Override
    public synchronized WeightedCallTargets augmentCount(RVMMethod t, double v) {
      int empty = -1;
      for (int i = 0; i < methods.length; i++) {
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
        RVMMethod[] newM = new RVMMethod[methods.length * 2];
        System.arraycopy(methods, 0, newM, 0, methods.length);
        methods = newM;
        float[] newW = new float[weights.length * 2];
        System.arraycopy(weights, 0, newW, 0, weights.length);
        weights = newW;
      }

      methods[empty] = t;
      weights[empty] = (float) v;
      return this;
    }

    @Override
    public synchronized void decay(double rate) {
      for (int i = 0; i < weights.length; i++) {
        weights[i] /= rate;
      }
    }

    @Override
    public synchronized double totalWeight() {
      double sum = 0;
      for (float weight : weights) {
        sum += weight;
      }
      return sum;
    }

    @Override
    public synchronized WeightedCallTargets filter(RVMMethod goal, boolean isPrecise) {
      if (isPrecise) {
        for (int i = 0; i < methods.length; i++) {
          if (goal.equals(methods[i])) {
            return WeightedCallTargets.create(methods[i], weights[i]);
          }
        }
        return null;
      } else {
        int matchCount = 0;
        int mismatchCount = 0;
        for (RVMMethod method : methods) {
          if (method != null) {
            if (RuntimeEntrypoints.isAssignableWith(goal.getDeclaringClass(), method.getDeclaringClass())) {
              matchCount++;
            } else {
              mismatchCount++;
            }
          }
        }
        if (mismatchCount == 0) {
          return this;
        }
        if (matchCount == 0) {
          return null;
        }

        MultiTarget filtered = new MultiTarget();
        for (int i=0; i<methods.length; i++) {
          RVMMethod method = methods[i];
          if (method != null && RuntimeEntrypoints.isAssignableWith(goal.getDeclaringClass(), method.getDeclaringClass())) {
            filtered.augmentCount(method, weights[i]);
          }
        }
        return filtered;
      }
    }
  }
}
