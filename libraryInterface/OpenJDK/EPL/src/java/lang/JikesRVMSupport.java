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
import java.util.HashMap;
import java.lang.instrument.Instrumentation;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.classloader.RVMField;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

import org.jikesrvm.scheduler.RVMThread;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {

  private static final Atom OFFSET_ATOM = Atom.findAsciiAtom("offset");
  private static final Atom VALUE_ATOM = Atom.findAsciiAtom("value");
  private static final RVMField JavaLangStringCharsField = RVMType.JavaLangStringType.findDeclaredField(VALUE_ATOM);
  private static final RVMField JavaLangStringOffsetField = RVMType.JavaLangStringType.findDeclaredField(OFFSET_ATOM);
  private static final Offset STRING_CHARS_OFFSET = JavaLangStringCharsField.getOffset();
  private static final Offset STRING_OFFSET_OFFSET = JavaLangStringOffsetField.getOffset();

  public static HashMap<Thread, RVMThread> threadMap;

  public static void initThreadMap() {
    threadMap = new HashMap<Thread, RVMThread>();
  }

  /**
   * Call the Object finalize method on the given object
   */
  public static void invokeFinalize(Object o)  throws Throwable {
    o.finalize();
  }

  public static void initializeInstrumentation(Instrumentation instrumenter) {
    throw new Error("InitializeInstrumentation is not implemented");
    //    VMClassLoader.setInstrumenter(instrumenter);
  }

  public static Class<?>[] getAllLoadedClasses() {
    //    VM.sysWriteln("get allloadedclasses is not implemented");
    throw new Error("GetAllLoadedClasses is not implemented");
  }

  public static Class<?>[] getInitiatedClasses(ClassLoader classLoader) {
    //    return VMClassLoader.getInitiatedClasses(classLoader);
    throw new Error("GetInitiatedClasses is not implemented");
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

  @Uninterruptible
  public static RVMType getTypeForClassUninterruptible(Class<?> c) {
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
    RVMType typeForClass = JikesRVMSupport.getTypeForClassUninterruptible(String.class);
    RVMField[] instanceFields = typeForClass.getInstanceFields();
    for (RVMField f : instanceFields) {
      if (f.getName() == VALUE_ATOM) {
        return  (char[]) Magic.getObjectAtOffset(str, f.getOffset());
      }
    }
    VM.sysFail("field 'value' not found");
    return null;
  }

  @Uninterruptible
  public static int getStringLength(String str) {
    // Note: made uninterruptible via AnnotationAdder
    return str.length();
  }

  @Uninterruptible
  public static int getStringOffset(String str) {
    RVMType typeForClass = JikesRVMSupport.getTypeForClassUninterruptible(String.class);
    RVMField[] instanceFields = typeForClass.getInstanceFields();
    for (RVMField f : instanceFields) {
      if (f.getName() == OFFSET_ATOM) {
        return Magic.getIntAtOffset(str, f.getOffset());
      }
    }
    VM.sysFail("field offset not found");
    return -1;
  }

  public static String newStringWithoutCopy(char[] data, int offset, int count) {
    //No back door
    return new String(data, offset, count);
  }


  /***
   * Thread stuff
   * */
  public static synchronized Thread createThread(RVMThread vmdata, String myName) {
    ThreadGroup tg = null;
    if (vmdata.isSystemThread()) {
      tg = RVMThread.getThreadGroupForSystemThreads();
    } else if (vmdata.getJavaLangThread() != null) {
      tg = vmdata.getJavaLangThread().getThreadGroup();
    }
    if (VM.VerifyAssertions) VM._assert(tg != null);
    Thread thread = new Thread(tg, myName);
    threadMap.put(thread, vmdata);
    return thread;
  }

  public static synchronized RVMThread getThread(Thread thread) {
    if (thread == null)
      return null;
    else
      return threadMap.get(thread);
  }

  public static void threadDied(Thread thread) {
    threadMap.remove(thread);
    ThreadGroup threadGroup = thread.getThreadGroup();
    if (threadGroup != null) {
      threadGroup.remove(thread);
    } else {
      if (VM.VerifyAssertions) {
        VM._assert(threadGroup != null, "Every thread must have a threadGroup in OpenJDK");
      }
    }
  }
  public static Throwable getStillBorn(Thread thread) {
    return null;
  }
  public static void setStillBorn(Thread thread, Throwable stillborn) {
  }
  /***
   * Enum stuff
   */
   @Uninterruptible
  public static int getEnumOrdinal(Enum<?> e) {
     return e.ordinal();
   }
  @Uninterruptible
  public static String getEnumName(Enum<?> e) {
    return e.name();
  }
}
