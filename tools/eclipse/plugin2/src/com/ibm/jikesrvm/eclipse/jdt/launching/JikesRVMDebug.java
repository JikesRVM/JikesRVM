/*
 * (C) Copyright IBM Corp 2001,2002
 *
 * ==========
 * $Source$
 * $Revision$
 * $Date$
 * $Author$
 * $Id$
 */
package com.ibm.jikesrvm.eclipse.jdt.launching;

import java.io.*;

/**
 * Provides simple debugging for the installation of the Jikes
 * RVM into Eclipse.
 *
 * @author Jeffrey Palm
 * @since  2002.06.18
 */
public final class JikesRVMDebug {

  public final static JikesRVMDebug d = new JikesRVMDebug();

  private JikesRVMDebug() {}

  final boolean trace = true; //false;

  private final boolean debug = true; //false;
  public boolean debug() { 
    return debug; 
  }

  private PrintStream err = System.err;
  public void setPrintStream(PrintStream err) {
    if (err != null) this.err = err;
  }

  public void todo(String msg) {
    bug("TODO: " + msg);
  }

  public void bug(String msg) {
    if (debug) err.println(" [JikesRVMInstall] " + msg);
  }

  public void handle(Throwable t) {
    if (!debug) return;
    System.err.println(" <<< JikesRVMDebug >>>");
    t.printStackTrace(System.err);
    System.err.println(" <<<     Done      >>>");
  }

  public void bug(boolean v) { bug(v+""); }
  public void bug(byte    v) { bug(v+""); }
  public void bug(char    v) { bug(v+""); }
  public void bug(double  v) { bug(v+""); }
  public void bug(float   v) { bug(v+""); }
  public void bug(int     v) { bug(v+""); }
  public void bug(long    v) { bug(v+""); }
  public void bug(short   v) { bug(v+""); }

}
