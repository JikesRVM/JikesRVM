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
package java.lang;

import java.security.ProtectionDomain;
import java.lang.instrument.Instrumentation;

import org.jikesrvm.classloader.RVMType;

import org.vmmagic.pragma.*;

import org.jikesrvm.scheduler.RVMThread;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {

  /**
   * Call the Object finalize method on the given object
   */
  public static void invokeFinalize(Object o)  throws Throwable {
    o.finalize();
  }

  public static void initializeInstrumentation(Instrumentation instrumenter) {
    throw new Error("TODO");
  }

  public static Class<?>[] getAllLoadedClasses() {
    throw new Error("TODO");
  }

  public static Class<?>[] getInitiatedClasses(ClassLoader classLoader) {
    throw new Error("TODO");
  }

  public static Class<?> createClass(RVMType type) {
    return Class.create(type);
  }

  public static Class<?> createClass(RVMType type, ProtectionDomain pd) {
    Class<?> c = Class.create(type);
    setClassProtectionDomain(c, pd);
    return c;
  }

  public static RVMType getTypeForClass(Class<?> c) {
    return c.type;
  }

  public static void setClassProtectionDomain(Class<?> c, ProtectionDomain pd) {
    c.pd = pd;
  }

  /***
   * String stuff
   * */

  @Uninterruptible
  public static char[] getBackingCharArray(String str) {
    return str.getValue();
  }

  @Uninterruptible
  public static int getStringLength(String str) {
    return str.length();
  }

  @Uninterruptible
  public static int getStringOffset(String str) {
    // TODO - Harmony
    return str.offset;
  }

  public static String newStringWithoutCopy(char[] data, int offset, int count) {
    // TODO - Harmony doesn't have a backdoor for not making a copy
    return new String(data, offset, count);
  }

  /***
   * Thread stuff
   * */
  public static Thread createThread(RVMThread vmdata, String myName) {
      return new Thread(vmdata, myName);
  }

  public static RVMThread getThread(Thread thread) {
    throw new Error("TODO");
  }

  public static void threadDied(Thread thread) {
    // TODO - Harmony
  }
  public static Throwable getStillBorn(Thread thread) {
    return null;
  }
  public static void setStillBorn(Thread thread, Throwable stillborn) {
    throw new Error("TODO");
  }
  /***
   * Enum stuff
   */
  @Uninterruptible
  public static int getEnumOrdinal(Enum<?> e) {
    // TODO: make e.ordinal() non-uninterruptible
    return e.ordinal();
  }
  @Uninterruptible
  public static String getEnumName(Enum<?> e) {
    // TODO: make Enum.name() non-uninterruptible
    return e.name();
  }
}
