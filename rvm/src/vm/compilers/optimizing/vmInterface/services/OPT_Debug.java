/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Vector;

/** 
 * OPT_Debug.java
 *
 * This file implements run-time services to aid with opt-compiler
 * debugging.
 *
 * @author Stephen Fink
 */
public final class OPT_Debug {
  private static Vector messages = new Vector();

  /**
   * Print a message.
   */
  public static void say (int id) {
    String msg = (String)messages.elementAt(id);
    VM.sysWrite(msg);
  }

  /**
   * Register a String.
   */
  public static int registerMessage (String s) {
    messages.addElement(s);
    return  messages.size() - 1;
  }
}



