/*
 * Copyright (c) 1994, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;

import org.jikesrvm.VM;
import org.jikesrvm.classlibrary.JavaLangSupport;
import org.jikesrvm.runtime.Time;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.System")
public class java_lang_System {

  @ReplaceMember
  public static void registerNatives() {
    // no natives
  }

  @ReplaceMember
  private static Properties initProperties(Properties props) {
    JavaLangSupport.setupStandardJavaProperties(props);

    // OpenJDK specific properties
    String bootClassPath = props.getProperty("java.boot.class.path");
    props.setProperty("sun.boot.class.path", bootClassPath);
    // sun.boot.library.path is mapped to sys_paths in java.lang.ClassLoader on OpenJDK 6
    String libraryPath = props.getProperty("java.library.path");
    props.setProperty("sun.boot.library.path", libraryPath);

    if (VM.BuildFor32Addr) {
      props.setProperty("sun.arch.data.model", "32");
    } else {
      props.setProperty("sun.arch.data.model", "64");
    }

    // TODO OPENJDK/ICEDTEA add remaining properties when they're known

    return props;
  }

  @ReplaceMember
  public static void arraycopy(Object src,  int  srcPos, Object dest, int destPos, int length) {
    JavaLangSupport.arraycopy(src, srcPos, dest, destPos, length);
  }

  @ReplaceMember
  public static int identityHashCode(Object obj) {
    return JavaLangSupport.identityHashCode(obj);
  }

  @ReplaceMember
  public static String mapLibraryName(String libname) {
    return JavaLangSupport.mapLibraryName(libname);
  }

  @ReplaceMember
  private static void setIn0(InputStream in) {
    JavaLangSupport.setSystemStreamField("in", in);
  }

  @ReplaceMember
  private static void setOut0(PrintStream out) {
    JavaLangSupport.setSystemStreamField("out", out);
  }

  @ReplaceMember
  private static void setErr0(PrintStream err) {
    JavaLangSupport.setSystemStreamField("err", err);
  }

  @ReplaceMember
  public static long currentTimeMillis() {
    return Time.currentTimeMillis();
  }

  @ReplaceMember
  public static long nanoTime() {
    return Time.nanoTime();
  }

}
