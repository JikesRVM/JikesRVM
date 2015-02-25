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

import org.jikesrvm.classloader.JMXSupport;

final class VMClassLoadingMXBeanImpl {

  /**
   * @return count of number of classes currently loaded
   */
  static int getLoadedClassCount() {
    return JMXSupport.getLoadedClassCount();
  }

  /**
   * @return count of number of classes ever unloaded
   */
  static long getUnloadedClassCount() {
    return JMXSupport.getUnloadedClassCount();
  }

  /**
   * @return true if verbose class loading is on.
   */
  static boolean isVerbose() {
    return JMXSupport.isVerbose();
  }

  /**
   * Turns verbose class loading on or off.
   *
   * @param verbose {@code true} if verbose information should
   *  be emitted on class loading.
   */
  static void setVerbose(boolean verbose) {
    JMXSupport.setVerbose(verbose);
  }

}
