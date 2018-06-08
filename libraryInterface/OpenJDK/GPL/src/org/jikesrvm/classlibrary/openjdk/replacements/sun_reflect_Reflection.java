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
package org.jikesrvm.classlibrary.openjdk.replacements;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.StackBrowser;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "sun.reflect.Reflection")
public class sun_reflect_Reflection {

  private static final boolean DEBUG_GET_CALLER_CLASS = false;

  @ReplaceMember
  public static Class<?> getCallerClass() {
    StackBrowser b = new StackBrowser();
    VM.disableGC();

    b.init();
    b.up(); // skip sun.reflect.Reflection.getCallerClass (this call)

    /* Skip Method.invoke, (if the caller was called by reflection) */
    if (b.currentMethodIs_Java_Lang_Reflect_Method_InvokeMethod()) {
      b.up();
    }

    RVMType ret = b.getCurrentClass();
    VM.enableGC();

    Class<?> clazz = ret.getClassForType();
    if (DEBUG_GET_CALLER_CLASS) {
      VM.sysWriteln("Returning caller class " + clazz + " for stack:");
      RVMThread.dumpStack();
    }
    return clazz;
  }

}
