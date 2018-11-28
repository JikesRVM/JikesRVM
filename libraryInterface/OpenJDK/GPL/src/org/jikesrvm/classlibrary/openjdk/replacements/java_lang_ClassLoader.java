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

import java.io.File;

import org.jikesrvm.classlibrary.ClassLoaderSupport;
import org.jikesrvm.runtime.DynamicLibrary;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.ClassLoader")
public class java_lang_ClassLoader {

  /**
   * @param fromClass
   *          the requesting class; used to find the classloader
   * @param file
   *          the dynamic library
   * @return {@code true} if the library is usable after the call returns (i.e.
   *         this call loaded it or it was already loaded), {@code false} if
   *         it's not (e.g. because the file doesn't exist)
   */
  @ReplaceMember
  private static boolean loadLibrary0(Class<?> fromClass, final File file) {
    String libraryFile = file.getName();
    int code = DynamicLibrary.load(libraryFile);
    if (code == 0) {
      return false;
    }
    return true;
  }

  @ReplaceMember
  private  Class<?> findLoadedClass0(String name) {
    return ClassLoaderSupport.findLoadedClass(thisAsClassLoader(), name);
  }

  private ClassLoader thisAsClassLoader() {
    return (ClassLoader) (Object) this;
  }

}
