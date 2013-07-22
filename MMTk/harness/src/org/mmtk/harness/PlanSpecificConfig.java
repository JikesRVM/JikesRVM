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
package org.mmtk.harness;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-plan configuration for the various MMTk plans.  Here we specify:
 *
 * - Short name(s) for each plan (eg "MS" for org.mmtk.plan.marksweep.MS)
 * - A "Heap Factor", ie the expected minimum heap (compared to MarkCompact) in which
 *   a given benchmark should complete.
 */
public class PlanSpecificConfig {

  /**
   * Map of names (both short and long) to the corresponding config.
   */
  private static Map<String, PlanSpecific> plans = new HashMap<String, PlanSpecific>();

  /**
   * Register a plan, creating the entries in {@code plans}
   * @param plan The plan-specific config
   * @param aliases Alternative names for this plan
   */
  private static void register(PlanSpecific plan, String...aliases) {
    plans.put(plan.getName(),plan);
    for (String alias : aliases) {
      if (plans.containsKey(alias)) {
        throw new Error("A plan with the name "+alias+" has already been registered");
      }
      plans.put(alias,plan);
    }
  }

  /**
   * The per-plan configuration.
   */
  public static final class PlanSpecific {

    /** The full class name of this plan's Plan class */
    private final String name;

    /** heap factor */
    private double heapFactor = 1.0d;

    /** The names of the expected spaces */
    private final Set<String> expectedSpaces = new HashSet<String>();

    /**
     * Create a plan with the given class name
     * @param name
     */
    private PlanSpecific(String name) {
      this.name = name;
      addExpectedSpaces("vm");
      addExpectedSpaces("immortal");
      addExpectedSpaces("meta");
      addExpectedSpaces("los");
      addExpectedSpaces("sanity");
      addExpectedSpaces("non-moving");
      addExpectedSpaces("sm-code");
      addExpectedSpaces("lg-code");
    }

    /**
     * @return The heap factor of this plan
     */
    public double getHeapFactor() {
      return heapFactor;
    }

    /**
     * Set the heap factor, ie the multiple of the minimum heap (ie MC heap)
     * required for this plan to run equivalent benchmarks.
     * @param heapFactor
     * @return This PlanSpecific, so that setters can be chained
     */
    public PlanSpecific heapFactor(double heapFactor) {
      this.heapFactor = heapFactor;
      return this;
    }

    /** @return the class name of this plan's Plan class */
    public String getName() {
      return name;
    }

    public PlanSpecific addExpectedSpaces(String...spaces) {
      for (String space : spaces) {
        expectedSpaces.add(space);
      }
      return this;
    }

    public Set<String> getExpectedSpaces() {
      return Collections.unmodifiableSet(expectedSpaces);
    }
  }

  private static final PlanSpecific DEFAULTS = new PlanSpecific("DEFAULT");
  static {
    /* Heap factors determined by min heap size for FixedLive benchmark */
    final double BASE_HEAP = 9472d; // Heap size in k for MS

    register(
        new PlanSpecific("org.mmtk.plan.copyms.CopyMS")
        .addExpectedSpaces("nursery", "ms"),
        "CopyMS");
    register(
        new PlanSpecific("org.mmtk.plan.generational.copying.GenCopy")
        .addExpectedSpaces("nursery", "ss0", "ss1")
        .heapFactor(18816/BASE_HEAP),
        "GenCopy");
    register(
        new PlanSpecific("org.mmtk.plan.generational.immix.GenImmix")
        .addExpectedSpaces("nursery", "immix"),
        "GenImmix");
    register(
        new PlanSpecific("org.mmtk.plan.generational.marksweep.GenMS")
        .addExpectedSpaces("nursery", "ms"),
        "GenMS");
    register(
        new PlanSpecific("org.mmtk.plan.immix.Immix")
        .addExpectedSpaces("immix"),
        "Immix");
    register(
        new PlanSpecific("org.mmtk.plan.markcompact.MC")
        .addExpectedSpaces("mc")
        .heapFactor(10496/BASE_HEAP),
        "MC", "MarkCompact");
    register(
        new PlanSpecific("org.mmtk.plan.marksweep.MS")
        .addExpectedSpaces("ms"),
        "MS", "MarkSweep");
    register(
        new PlanSpecific("org.mmtk.plan.nogc.NoGC"),
        "NoGC");
    register(
        new PlanSpecific("org.mmtk.plan.poisoned.Poisoned")
        .addExpectedSpaces("ms"),
        "Poisoned");
    register(
        new PlanSpecific("org.mmtk.plan.semispace.usePrimitiveWriteBarriers.UsePrimitiveWriteBarriers")
        .addExpectedSpaces("ss0", "ss1")
        .heapFactor(18816/BASE_HEAP),
        "UsePrimitiveWriteBarriers", "PrimitiveWB");
    register(
        new PlanSpecific("org.mmtk.plan.refcount.fullheap.RC")
        .addExpectedSpaces("rclos", "rc")
        .heapFactor(9856/BASE_HEAP),
        "RC");
    register(
        new PlanSpecific("org.mmtk.plan.refcount.generational.GenRC")
        .addExpectedSpaces("nursery", "rclos", "rc")
        .heapFactor(9984/BASE_HEAP),
        "GenRC");
    register(
        new PlanSpecific("org.mmtk.plan.semispace.SS")
        .heapFactor(18816/BASE_HEAP)
        .addExpectedSpaces("ss0", "ss1"),
        "SS", "SemiSpace");
    register(
        new PlanSpecific("org.mmtk.plan.stickyimmix.StickyImmix")
        .addExpectedSpaces("immix"),
        "StickyImmix");
    register(
        new PlanSpecific("org.mmtk.plan.stickyms.StickyMS")
        .addExpectedSpaces("ms"),
        "StickyMS");
  }

  /**
   * Get the config for a given name, returning DEFAULTS if it does
   * not exist.
   *
   * @param name
   * @return
   */
  public static PlanSpecific get(String name) {
    PlanSpecific plan = plans.get(name);
    if (plan == null) {
      System.err.println("No plan specific configuration for "+name+", using defaults");
      return DEFAULTS;
    }
    return plan;
  }

  /**
   * Return the name of the class for a given plan.  Allows the use
   * of short names for plans on the command line, and full plan class
   * names for new collectors not yet configured.
   * @param name
   * @return
   */
  public static String planClass(String name) {
    PlanSpecific plan = plans.get(name);
    if (plan == null) {
      System.err.println("No plan specific configuration for "+name);
      return name;
    }
    return plan.getName();
  }

  /**
   * Return the heap factor of the plan.  This is an approximate estimate of the
   * size of the minimum heap required to run a given benchmark, compared to the
   * size required by the most compact collector, MC.
   *
   * @param plan The name of the plan
   * @return The configured heap factor for the plan
   */
  public static double heapFactor(String plan) {
    return get(plan).getHeapFactor();
  }
}
