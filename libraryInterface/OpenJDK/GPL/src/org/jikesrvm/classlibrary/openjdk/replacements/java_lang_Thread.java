/*
 * Copyright (c) 1994, 2010, Oracle and/or its affiliates. All rights reserved.
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
import org.jikesrvm.architecture.StackFrameLayout;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.Thread")
public class java_lang_Thread {

  @ReplaceMember
  public static void registerNatives() {
    VM.sysWriteln("registerNatives: java.lang.Thread");
  }

  @ReplaceMember
  public static Thread currentThread() {
    return RVMThread.getCurrentThread().getJavaLangThread();
  }

  @ReplaceMember
  public final void setPriority0(int newPriority) {
    RVMThread rvmThread = JikesRVMSupport.getThread(thisAsThread());
    // setPriority0 will be called during the constructor when the rvmThread isn't set
    if (rvmThread != null) {
      rvmThread.setPriority(newPriority);
    }
  }

  @ReplaceMember
  public final boolean isAlive() {
    RVMThread rvmThread = JikesRVMSupport.getThread(thisAsThread());
    if (rvmThread != null) {
      return rvmThread.isAlive();
    }
    return false;
  }

  @ReplaceMember
  public final void start0() {
    int stacksize = StackFrameLayout.getStackSizeNormal();
    Thread me = thisAsThread();
    int priority = me.getPriority();
    String name = me.getName();
    boolean daemon = me.isDaemon();
    RVMThread myThread = new RVMThread(me, stacksize, name, daemon, priority);
    JikesRVMSupport.setThread(myThread, me);
    myThread.start();
  }

  @ReplaceMember
  private boolean isInterrupted(boolean ClearInterrupted) {
    RVMThread rvmThread = JikesRVMSupport.getThread(thisAsThread());
    if (VM.VerifyAssertions) VM._assert(rvmThread != null);
    boolean interrupted = rvmThread.isInterrupted();
    if (ClearInterrupted) {
      rvmThread.clearInterrupted();
    }
    return interrupted;
  }

  private Thread thisAsThread() {
    return (Thread) (Object) this;
  }


}
