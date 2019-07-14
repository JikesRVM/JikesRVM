/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

public final class ClassNameHelpers {

  /**
   * Converts a normal class name to an internal binary class name.
   * @param className a class name
   * @return the internal binary form of the class name
   *
   * @see <a href="https://docs.oracle.com/javase/specs/index.html">
   * Java Virtual Machine Specification, Section "Binary Class and Interface Names"</a>
   */
  public static String convertClassnameToInternalName(String className) {
    if (className == null) {
      return null;
    }
    return className.replace('.', '/');
  }

}
