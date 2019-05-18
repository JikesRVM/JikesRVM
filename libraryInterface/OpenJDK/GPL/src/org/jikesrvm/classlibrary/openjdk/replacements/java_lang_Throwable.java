/*
 * Copyright (c) 1994, 2006, Oracle and/or its affiliates. All rights reserved.
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
import org.jikesrvm.runtime.StackTrace;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.Throwable")
public class java_lang_Throwable {

  public static final StackTraceElement[] zeroLengthStackTrace = new StackTraceElement[0];

  @ReplaceMember
  private StackTraceElement[] stackTrace;

  @ReplaceMember
  public synchronized Throwable fillInStackTrace() {
    if (!VM.fullyBooted && !VM.safeToCreateStackTrace) {
      this.stackTrace = zeroLengthStackTrace;
      return (Throwable) (Object) this;
    } else if (RVMThread.getCurrentThread().isCollectorThread()) {
      this.stackTrace = zeroLengthStackTrace;
      VM.sysWriteln("Exception in GC thread");
      RVMThread.dumpVirtualMachine();
      return (Throwable) (Object) this;
    }
    try {
      StackTrace stackTrace = new StackTrace();
      this.stackTrace = StackTrace.convertToJavaClassLibraryStackTrace(stackTrace.getStackTrace((Throwable) (Object) this));
      return (Throwable) (Object) this;
    } catch (OutOfMemoryError oome) {
      return null;
    } catch (Throwable t) {
      VM.sysFail("VMThrowable.fillInStackTrace(): Cannot fill in a stack trace; got a weird Throwable when I tried to");
      return null;
    }
  }

  @ReplaceMember
  private int getStackTraceDepth() {
    return stackTrace.length;
  }

  @ReplaceMember
  private StackTraceElement getStackTraceElement(int index) {
    return stackTrace[index];
  }

}
