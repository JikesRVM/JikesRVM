/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.StackBrowser;
import org.jikesrvm.util.LinkedListRVM;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.security.AccessController")
public class java_security_AccessController {

  @ReplaceMember
  private static AccessControlContext getStackAccessControlContext() {
    StackBrowser b = new StackBrowser();
    VM.disableGC();
    b.init();
    int frameCount = b.countFrames();
    VM.enableGC();

    RVMClass classes[] = new RVMClass[frameCount];

    int lastEntry = 0;
    VM.disableGC();
    // Process all frames, skipping the first one as it belongs to this method
    do {
      b.up();
      RVMClass clazz = b.getCurrentClass();
      if (clazz != null) {
        classes[lastEntry++] = clazz;
      }
    } while (b.hasMoreFrames());
    VM.enableGC();

    LinkedListRVM<ProtectionDomain> protectionDomains = new LinkedListRVM<ProtectionDomain>();
    for (int i = 0; i < lastEntry; i++) {
      ProtectionDomain pd = classes[i].getClassForType().getProtectionDomain();
      if (pd != null) {
        protectionDomains.add(pd);
      }
    }

    ProtectionDomain result[] = new ProtectionDomain[protectionDomains.size()];
    result = protectionDomains.toArray(result);
    AccessControlContext context = new AccessControlContext(result);
    return context;
  }

  // FIXME OPENJDK/ICEDTEA not sure if the semantics of this are correct
  @ReplaceMember
  static AccessControlContext getInheritedAccessControlContext() {
    Thread thisThread = Thread.currentThread();
    return (AccessControlContext) Entrypoints.inheritedAccessControlContext_Field.getObjectUnchecked(thisThread);
  }

  // TODO OPENJDK/ICEDTEA actually add restrictions
  @ReplaceMember
  public static <T> T doPrivileged(PrivilegedAction<T> action) {
    return action.run();
  }

  // TODO OPENJDK/ICEDTEA actually add restrictions
  @ReplaceMember
  public static <T> T doPrivileged(PrivilegedExceptionAction<T> action) throws PrivilegedActionException {
    try {
      return action.run();
    } catch (Exception e) {
      throw new PrivilegedActionException(e);
    }
  }

  // TODO OPENJDK/ICEDTEA actually add restrictions
  @ReplaceMember
  public static <T> T doPrivileged(PrivilegedAction<T> action, AccessControlContext context) {
    return action.run();
  }

  // TODO OPENJDK/ICEDTEA actually add restrictions
  @ReplaceMember
  public static <T> T doPrivileged(PrivilegedExceptionAction<T> action, AccessControlContext context) throws PrivilegedActionException {
    try {
      return action.run();
    } catch (Exception e) {
      throw new PrivilegedActionException(e);
    }
  }

}
