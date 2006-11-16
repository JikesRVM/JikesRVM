/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import java.io.UTFDataFormatException;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_Statics;
import com.ibm.jikesrvm.classloader.VM_Atom;

/**
 * Implementation of string interning for JikesRVM.
 * 
 * @author Dave Grove
 * @author Ian Rogers
 */
final class VMString {
  private static final WeakHashMap internedStrings = new WeakHashMap();

  /** 
   * Intern the argument string.
   * First check to see if the string is in the string literal
   * dictionary.
   */
  static String intern(String str) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    synchronized (internedStrings) {
      WeakReference ref = (WeakReference)internedStrings.get(str);
      if (ref != null) {
        String s = (String)ref.get();
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
      internedStrings.put(str, new WeakReference(str));
    }
    return str;
  }
}
