/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.librarySupport;
import VM_Atom;
import VM_Statics;

/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library String operations.
 *
 * @author Stephen Fink
 */
public class StringSupport {
  /**
   * Searches an internal table of strings for a string equal to this String.
   * If the string is not in the table, it is added. Answers the string contained
   * in the table which is equal to this String. The same string object is always
   * answered for strings which are equal.
   *
   * @arg               s the string to search for.
   * @return		the interned string equal to this String
   */
  public static String intern(String s) { 
    //!!TODO: This pollutes the jtoc with strings that needn't be allocated statically.
    //        We need to keep a separate table for intern'd strings that
    //        don't originate from class constant pools. [--DL]
    try {
      VM_Atom atom = VM_Atom.findOrCreateUnicodeAtom(s);
      int     slot = VM_Statics.findOrCreateStringLiteral(atom);
      return (String)VM_Statics.getSlotContentsAsObject(slot);
    } catch (java.io.UTFDataFormatException x) {
      throw new InternalError();
    }
  }
}
