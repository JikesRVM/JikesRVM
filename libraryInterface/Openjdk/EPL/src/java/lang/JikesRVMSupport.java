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

import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.RVMField;

import org.jikesrvm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.runtime.Magic;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {

  private static final RVMField JavaLangStringCharsField = RVMType.JavaLangStringType.findDeclaredField(Atom.findAsciiAtom("value"));
  private static final RVMField JavaLangStringOffsetField = RVMType.JavaLangStringType.findDeclaredField(Atom.findAsciiAtom("offset"));
  private static final Offset STRING_CHARS_OFFSET = JavaLangStringCharsField.getOffset();
  private static final Offset STRING_OFFSET_OFFSET = JavaLangStringOffsetField.getOffset();

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
      return (char[])Magic.getObjectAtOffset(str, STRING_CHARS_OFFSET);
      //    return str.getValue();      
      //   throw new Error("GetBackingCharArray");

      
  }

  @Uninterruptible
  public static int getStringLength(String str) {
      return str.length();
      //throw new Error("GetStringLength");

  }

  @Uninterruptible
  public static int getStringOffset(String str) {
      return Magic.getIntAtOffset(str, STRING_OFFSET_OFFSET);
    // TODO - Harmony
      //      return str.offset;
      //      throw new Error("GetStringOffset");

  }

  public static String newStringWithoutCopy(char[] data, int offset, int count) {
    // TODO - Harmony doesn't have a backdoor for not making a copy
    return new String(data, offset, count);
  }

  /***
   * Thread stuff
   * */
  public static Thread createThread(RVMThread vmdata, String myName) {
      VM.sysWriteln("Creating Java thread");
      return new Thread(vmdata, myName);
	   //           throw new Error("CreateThread");
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
       //   throw new Error("GetEnumOrdinal");
       //   TODO: make e.ordinal() non-uninterruptible
	   return e.ordinal();
   }
  @Uninterruptible
  public static String getEnumName(Enum<?> e) {
      //      throw new Error("GetEnumName");
    // TODO: make Enum.name() non-uninterruptible
          return e.name();
  }
}
