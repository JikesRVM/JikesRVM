/*
 * Copyright (c) 1994, 2009, Oracle and/or its affiliates. All rights reserved.
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
import org.jikesrvm.runtime.DynamicLibrary;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.ClassLoader$NativeLibrary")
public class java_lang_ClassLoader_NativeLibrary {

  @ReplaceMember
  long handle;

  @ReplaceMember
  void load(String name) {
    int booleanOk = DynamicLibrary.load(name);
    if (booleanOk != 1) {
      String msg = "Failed to load native library " + name;
      if (VM.safeToCreateStackTrace) {
        throw new UnsatisfiedLinkError(msg);
      } else {
        VM.sysFail(msg);
      }
    }
    handle = DynamicLibrary.getHandleForLibrary(name).toLong();
  }

  @ReplaceMember
  long find(String name) {
    return DynamicLibrary.resolveSymbol(name).toLong();
  }

  @ReplaceMember
  void unload() {
    // NYI for Jikes RVM: see DynamicLibrary.unload()
  }

}
