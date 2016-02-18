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
package gnu.java.lang.management;

import java.lang.management.MemoryUsage;

import org.jikesrvm.mm.mminterface.JMXSupport;

final class VMMemoryMXBeanImpl {

  /**
   * Return the sum of the usage in all heap-based
   * pools.
   *
   * @return the memory usage for heap-based pools.
   */
  static MemoryUsage getHeapMemoryUsage() {
    return getUsage(false);
  }

  /**
   * Return the sum of the usage in all non-heap-based
   * pools.
   *
   * @return the memory usage for non-heap-based pools.
   */
  static MemoryUsage getNonHeapMemoryUsage() {
    return getUsage(true);
  }

  /**
   * Return the number of objects waiting for finalization.
   *
   * @return the number of finalizable objects.
   */
  static int getObjectPendingFinalizationCount() {
    return JMXSupport.getObjectPendingFinalizationCount();
  }

  /**
   * Returns true if some level of verbosity is on.
   *
   * @return {@code true} if verbosity is greater than 0.
   */
  static boolean isVerbose() {
    return JMXSupport.isMMTkVerbose();
  }

  /**
   * Turns on or off verbosity.  MMTk has a more detailed
   * level of verbosity, so we simply map true to level 1.
   *
   * @param verbose the new verbosity setting.
   */
  static void setVerbose(boolean verbose) {
    JMXSupport.setMMTkVerbose(verbose);
  }

  /**
   * Totals the memory usage from all the pools that are either
   * mortal or immortal.
   * <p>
   * Non-heap pools are immortal, heap pools are non-immortal.
   *
   * @param immortal true if the spaces counted should be immortal.
   * @return the memory usage overall.
   */
  private static MemoryUsage getUsage(boolean immortal) {
    return JMXSupport.getUsage(immortal);
  }

}
