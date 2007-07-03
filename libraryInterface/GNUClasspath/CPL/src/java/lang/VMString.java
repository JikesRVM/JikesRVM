/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Statics;

/**
 * Implementation of string interning for JikesRVM.
 */
final class VMString {
  private static final WeakHashMap<String,WeakReference<String>> internedStrings =
    new WeakHashMap<String,WeakReference<String>>();

  /**
   * Intern the argument string.
   * First check to see if the string is in the string literal
   * dictionary.
   */
  static String intern(String str) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    synchronized (internedStrings) {
      WeakReference<String> ref = internedStrings.get(str);
      if (ref != null) {
        String s = ref.get();
        if (s != null) {
          return s;
        }
      }

      // Check to see if this is a StringLiteral:
      String literal = VM_Statics.findStringLiteral(str);
      if (literal != null) {
        // Should we put it in this map too?
        // Would be faster to find the next time we went looking for it,
        // but will waste space and uselessly increase the number of weak refs
        // in the system because it will _always be reachable from the JTOC.
        return literal;
      }

      // If we get to here, then there is no interned version of the String.
      // So we make one.
      internedStrings.put(str, new WeakReference<String>(str));
    }
    return str;
  }
}
