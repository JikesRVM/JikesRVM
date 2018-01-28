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
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Callbacks.ClassLoadedMonitor;
import org.jikesrvm.scheduler.Synchronization;
import org.vmmagic.pragma.Entrypoint;

/**
 * Implements functionality to support JMX classloading beans.
 * <p>
 * Pulling the functionality into the core of the VM will hopefully allow us to
 * support multiple class libraries without having to duplicate a lot of code.
 * TODO: we need to add OpenJDK support before we actually know whether that is true.
 */
public final class JMXSupport implements ClassLoadedMonitor {

  public static final JMXSupport CLASS_LOADING_JMX_SUPPORT = new JMXSupport();

   /** the count of loaded classes */
  @SuppressWarnings("unused") // accessed via low-level synchronization
  @Entrypoint
  private int classLoadedCount;

  private JMXSupport() {
    // disallow instantiation
  }

  @Override
  public void notifyClassLoaded(RVMClass klass) {
    increaseClassLoadedCount();
  }

  private static void increaseClassLoadedCount() {
    // Need to use low-level synchronization because this method can be called
    // very early in the boot process. The class loaded monitor is added directly
    // after the bootstrap class loader is booted.
    Synchronization.fetchAndAdd(CLASS_LOADING_JMX_SUPPORT,
        Entrypoints.classLoadedCountField.getOffset(), 1);
  }

  public static int getLoadedClassCount() {
    return Synchronization.fetchAndAdd(CLASS_LOADING_JMX_SUPPORT,
        Entrypoints.classLoadedCountField.getOffset(), 0);
  }

  public static long getUnloadedClassCount() {
    return 0; // class unloading not support yet
  }

  public static boolean isVerbose() {
    return VM.verboseClassLoading;
  }

  public static void setVerbose(boolean verbose) {
    VM.verboseClassLoading = verbose;
  }

  /**
   * Sets the count of loaded classes when writing the bootimage.
   * @param bootimageTypeCount the number of types contained in the bootimage
   */
  public void setClassLoadedCountForBootimage(int bootimageTypeCount) {
    if (VM.VerifyAssertions && VM.runningVM) {
      VM._assert(VM.NOT_REACHED, "This method is intended to be called only by the bootimage writer");
    }
    this.classLoadedCount = bootimageTypeCount;
  }

}
