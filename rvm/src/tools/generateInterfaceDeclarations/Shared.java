/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2004
 *
 * $Id$
 */
// package com.ibm.JikesRVM.GenerateInterfaceDeclarations;

import java.io.PrintStream;

/** A class for shared code among GenerateInterfaceDeclarations et al.  This
 * contains shorthand methods for printing and error reporting, plus some
 * other common utility functions.  All methods are static.
 *
 * @author Steven Augart
 * @date 11 March 2003
 */


class Shared extends Util {
  /** 
      These routines all handle I/O.  (More below)
   **/

  /* Leave a default constructor in place, so this can be shared. */

  static PrintStream out = System.out;
  static String outFileName;

  static {
    setPreface("GenerateInterfaceDeclarations: While we were creating InterfaceDeclarations.h, there was a problem.\nGenerateInterfaceDeclarations: ");
  }

  static void printAfterword() {
    if (outFileName != null) {
      ep("The build system (my caller) should delete the output file");
      ep(" ");
      epln(outFileName);
    }
  }

  static void p(String s) {
    out.print(s);
  }

  static void pln(String s) {
    out.println(s);
  }

  static void pln() {
    out.println();
  }
  
}
