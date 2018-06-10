/*
 * This file is part of the Jikes RVM project (http://jikesrvm.org). This file
 * is licensed to You under the Eclipse Public License (EPL); You may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.opensource.org/licenses/eclipse-1.0.php See the
 * COPYRIGHT.txt file distributed with this work for information regarding
 * copyright ownership.
 */
package org.jikesrvm.classlibrary.openjdk.replacements;

import java.io.File;

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

}
