/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library String operations.
 *
 * @author Stephen Fink
 */

package com.ibm.JikesRVM.librarySupport;
import VM;

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
    return VM.findOrCreateString(s); 
  }
}
