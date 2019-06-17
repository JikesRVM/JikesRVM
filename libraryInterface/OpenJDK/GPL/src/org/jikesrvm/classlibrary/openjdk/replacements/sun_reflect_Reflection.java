/*
 * Copyright (c) 2001, 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.jikesrvm.classlibrary.openjdk.replacements;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
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
    // TODO OPENJDK/ICEDTEA this implementation is rather messy. If we have to adjust this again,
    // we ought to write a better one, with a test case for all the cases.

    StackBrowser b = new StackBrowser();
    VM.disableGC();

    b.init();
    b.up(); // skip sun.reflect.Reflection.getCallerClass (this call)

    /* Skip Method.invoke and Constructor.newInstance, (if the caller was called by reflection) */
    if (b.currentMethodIs_Java_Lang_Reflect_Method_InvokeMethod() || b.currentMethodIs_Java_Lang_Reflect_Constructor_NewInstance()) {
      b.up();
    }
    /* Work around OpenJDK's work around for Reflection.getCallerClass(..) in java.lang.reflect.Method.invoke(..).
     * The OpenJDK implementation of getCallerClass assumes a fixed stack depth of 2. The Jikes RVM implementation
     * is different so we have to work around OpenJDK's work around */
    if (b.currentMethodIs_Java_Lang_Reflect_Method_GetCallerClass()) {
      b.up();
    }

    /* Skip JNI if necessary */
    while (b.currentMethodIsPartOfJikesRVMJNIImplementation()) {
      b.up();
    }

    /* Don't skip if we're already in the application */
    if (b.currentMethodIsInClassLibrary()) {
      b.up(); // skip method that contains the call
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

  @ReplaceMember
  private static int getClassAccessFlags(Class c) {
    RVMClass clazz = JikesRVMSupport.getTypeForClass(c).asClass();
    return clazz.getOriginalModifiers();
  }

}
