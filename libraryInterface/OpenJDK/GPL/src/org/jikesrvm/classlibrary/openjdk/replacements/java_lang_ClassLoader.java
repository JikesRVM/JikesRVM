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

import java.security.ProtectionDomain;

import org.jikesrvm.classlibrary.ClassLoaderSupport;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.ClassLoader")
public class java_lang_ClassLoader {

  @ReplaceMember
  private  Class<?> findLoadedClass0(String name) {
    return ClassLoaderSupport.findLoadedClass(thisAsClassLoader(), name);
  }

  @ReplaceMember
  private Class<?> findBootstrapClass(String name) throws ClassNotFoundException {
    if (name.startsWith("L") && name.endsWith(";")) {
      name = name.substring(1, name.length() - 2);
    }
    BootstrapClassLoader bootstrapCL = BootstrapClassLoader.getBootstrapClassLoader();
    Class<?> loadedBootstrapClass = bootstrapCL.findLoadedBootstrapClass(name);
    if (loadedBootstrapClass != null) {
      return loadedBootstrapClass;
    }
    return bootstrapCL.findClass(name);
  }

  @ReplaceMember
  private Class defineClass1(String name, byte[] b, int off, int len,
      ProtectionDomain pd, String source) {
    // TODO OPENJDK/ICEDTEA do we need to do something with source? Seems to be the location property
    // from CodeSource
    return ClassLoaderSupport.defineClass(thisAsClassLoader(), name, b, off, len, pd);
  }

  private ClassLoader thisAsClassLoader() {
    return (ClassLoader) (Object) this;
  }

}
