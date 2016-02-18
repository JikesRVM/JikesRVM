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

final class VMMemoryManagerMXBeanImpl {

  /**
   * We ignore the name as we only have one manager,
   * and simply return the same as the management factory
   * would.
   *
   * @param name the name of the memory manager whose pools
   *             should be returned (ignored).
   * @return the list of pools.
   */
  static String[] getMemoryPoolNames(String name) {
    return VMMemoryPoolMXBeanImpl.getPoolNames();
  }

  /**
   * We assume that our manager is always valid.
   *
   * @param name the name of the memory manager whose pools
   *             should be returned (ignored).
   * @return {@code true}
   */
  static boolean isValid(String name) {
    return true;
  }

}
