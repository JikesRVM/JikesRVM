/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import java.io.UTFDataFormatException;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Statics;
import com.ibm.JikesRVM.classloader.VM_Atom;

/**
 * Implementation of string interning for JikesRVM.
 * 
 * This code will only be active when combined with 
 * classpath changes we have submitted as patch 1686.
 * 
 * @author Dave Grove <groved@us.ibm.com>
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
      try {
        VM_Atom atom = VM_Atom.findUnicodeAtom(str);
        if (atom != null) {
          String literal = VM_Statics.findStringLiteral(atom);
          if (literal != null) {
            // Should we put it in this map too?
            // Would be faster to find the next time we went looking for it,
            // but will waste space and uselessly increase the number of weak refs
            // in the system because it will _always be reachable from the JTOC.
            return literal; 
          }
        }
      } catch (java.io.UTFDataFormatException e) { }

      // If we get to here, then there is no interned version of the String.
      // So we make one.
      internedStrings.put(str, new WeakReference(str));
    }
    return str;
  }
}
