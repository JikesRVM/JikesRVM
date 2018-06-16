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
package org.jikesrvm.mm.mminterface;

import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.HashMap;

import org.jikesrvm.mm.mminterface.Selected.Plan;
import org.jikesrvm.mm.mmtk.FinalizableProcessor;
import org.mmtk.policy.Space;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.statistics.Stats;

/**
 * Provides methods supporting all JMX beans that relate to memory. Functionality
 * for memory management beans generally requires interfacing with MMTk which is
 * why this class belongs to the MMTk-VM interface.
 * <p>
 * In JMX terms, the Jikes RVM provides only one memory manager (the garbage collector)
 * and several memory pools (each of which corresponds to exactly one MMTk space).
 * <p>
 * NOTE: Features related to memory usage are currently not implemented.
 */
public class JMXSupport {

  /**
   * We only provide one garbage collector because the choice of garbage collector
   * for the VM is fixed at build time.
   */
  private static final String[] garbageCollectorNames = { Selected.name };

  /**
   * The names of all memory managers that are not garbage collectors.
   * All of our memory managers are collectors so this is empty.
   */
  private static final String[] memoryManagerNames = {};

  /**
   * Maps a space name to its index in the space array.
   */
  private static HashMap<String, Integer> pools;

  private static String[] poolNames;

  /**
   * The level of verbosity that was used in MMTk when verbosity was switched off.
   * It will be restored when verbosity is switched on again. We can do this
   * because no other part of the system ever switches the verbosity.
   */
  private static int lastMMTkVerbosity;

  /**
   * Initializes data structures.
   * This needs to called before the application starts.
   */
  public static void fullyBootedVM() {
    int spaceCount = Space.getSpaceCount();
    pools = new HashMap<String, Integer>(spaceCount * 2);
    Space[] spaces = Space.getSpaces();
    for (int i = 0; i < spaceCount; i++) {
      pools.put(spaces[i].getName(), i);
    }
    poolNames = pools.keySet().toArray(new String[spaceCount]);
  }

  public static String[] getGarbageCollectorNames() {
    return garbageCollectorNames;
  }

  public static String[] getMemoryManagerNames() {
    return memoryManagerNames;
  }

  /**
   * @param poolName the name of the pool
   * @return the name of the memory manager(s) of the pool (always
   *  our single garbage collector, i.e. active plan)
   */
  public static String[] getMemoryManagerNames(String poolName) {
    return garbageCollectorNames;
  }

  public static String[] getPoolNames() {
    return poolNames;
  }

  /**
   * Returns non-heap for immortal spaces and heap for non-immortal
   * spaces because objects can be added and remove from non-immortal
   * spaces.
   *
   * @param poolName the pool's name
   * @return the type of the memory pool
   */
  public static MemoryType getType(String poolName) {
    Space space = getSpace(poolName);
    boolean immortal = space.isImmortal();
    if (immortal) {
      return MemoryType.NON_HEAP;
    } else {
      return MemoryType.HEAP;
    }
  }

  private static Space getSpace(String poolName) {
    Space[] spaces = Space.getSpaces();
    int poolIndex = pools.get(poolName);
    return spaces[poolIndex];
  }

  /**
   * @param poolName a memory pool name
   * @return whether a space with the given name exists
   */
  public static boolean isValid(String poolName) {
    return pools.get(poolName) != null;
  }

  public static MemoryUsage getUsage(boolean immortal) {
    int spaceCount = Space.getSpaceCount();
    Space[] spaces = Space.getSpaces();
    JMXMemoryUsage usage = JMXMemoryUsage.empty();
    for (int index = 0; index < spaceCount; index++) {
      Space space = spaces[index];
      if (space.isImmortal() == immortal) {
        usage.add(space);
      }
    }
    return usage.toMemoryUsage();
  }

  public static MemoryUsage getUsage(String poolName) {
    Space space = getSpace(poolName);
    return new JMXMemoryUsage(space).toMemoryUsage();
  }

  public static int getObjectPendingFinalizationCount() {
    return FinalizableProcessor.countReadyForFinalize();
  }

  public static synchronized boolean isMMTkVerbose() {
    return Options.verbose.getValue() > 0;
  }

  /**
   * Sets the verbosity for MMTk. Verbosity in MMTk has several levels
   * so this method makes an effort to save the previous verbosity level
   * if possible.
   *
   * @param verbose {@code true} if verbosity is to be enabled,
   *  {@code false} otherwise
   */
  public static synchronized void setMMTkVerbose(boolean verbose) {
    int currentVerbosity = Options.verbose.getValue();
    if (verbose == false) {
      if (currentVerbosity > 0) {
        lastMMTkVerbosity = currentVerbosity;
        Options.verbose.setValue(0);
      }
      // else: nothing to do, MMTk is already non-verbose
    } else {
      if (lastMMTkVerbosity > 0) {
        // Restore old verbosity value, if we have one
        Options.verbose.setValue(lastMMTkVerbosity);
      } else {
        // No old value, so we assume verbosity of 1 because that will get
        // us printouts of collection times
        Options.verbose.setValue(1);
      }
    }
  }

  public static long getCollectionCount() {
    return Stats.gcCount();
  }

  public static long getCollectionTime() {
    return Math.round(Plan.totalTime.getTotalMillis());
  }

}
