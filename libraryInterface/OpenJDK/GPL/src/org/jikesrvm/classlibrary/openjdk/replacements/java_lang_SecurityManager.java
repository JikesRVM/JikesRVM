/*
 * Copyright (c) 1995, 2006, Oracle and/or its affiliates. All rights reserved.
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
import org.jikesrvm.runtime.StackBrowser;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.SecurityManager")
public class java_lang_SecurityManager {

  @ReplaceMember
  protected Class[] getClassContext() {
    StackBrowser b = new StackBrowser();
    VM.disableGC();
    b.init();
    int frameCount = b.countFrames();
    VM.enableGC();

    Class[] classes = new Class[frameCount];

    int lastEntry = 0;
    VM.disableGC();
    // Process all frames, skipping the first one as it belongs to this method
    do {
      b.up();
      RVMClass clazz = b.getCurrentClass();
      if (clazz != null) {
        classes[lastEntry++] = clazz.getClassForType();
      }
    } while (b.hasMoreFrames());
    VM.enableGC();

    return classes;
  }

}
