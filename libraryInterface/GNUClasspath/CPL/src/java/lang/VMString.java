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
package java.lang;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;

/**
 * Implementation of string interning for JikesRVM.
 */
final class VMString {
  /**
   * Intern the argument string.
   * First check to see if the string is in the string literal
   * dictionary.
   */
  static String intern(String str) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    return Atom.internString(str);
  }
}
