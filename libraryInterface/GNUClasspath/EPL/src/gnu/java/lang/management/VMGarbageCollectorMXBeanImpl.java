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

import org.jikesrvm.mm.mminterface.JMXSupport;

final class VMGarbageCollectorMXBeanImpl {

  /**
   * Returns the number of collections that have occurred.
   * We ignore the name as we only have one collector.
   *
   * @param name the name of the collector whose count should
   *             be returned (ignored).
   * @return the number of collections.
   */
  static long getCollectionCount(String name) {
    return JMXSupport.getCollectionCount();
  }

  /**
   * Returns the amount of time spent collecting.
   * We ignore the name as we only have one collector.
   *
   * @param name the name of the collector whose time should
   *             be returned (ignored).
   * @return the number of milliseconds spent collecting.
   */
  static long getCollectionTime(String name) {
    return JMXSupport.getCollectionTime();
  }

}
