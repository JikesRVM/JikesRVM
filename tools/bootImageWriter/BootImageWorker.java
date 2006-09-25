/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$

import java.util.*;
import com.ibm.JikesRVM.classloader.VM_Type;

/**
 * Worker thread for parallel compilation
 * during bootimage writing.
 * @author Perry Cheng
 */
public class BootImageWorker extends Thread {

  public static final int verbose = 0;
  static Enumeration enumeration;
  int id;

  public static void startup (Enumeration e) {
    enumeration = e;
  }

  public void run () {

    int count = 0;
    while (true) {
      VM_Type type = null;
      synchronized (enumeration) {
        if (enumeration.hasMoreElements()) {
          type = (VM_Type) enumeration.nextElement();
          count++;
        }
      }
      if (type == null) 
        return;
      long startTime = 0;
      if (verbose >= 1) {
          BootImageWriterMessages.say("Thread " + id + " instantiating type " + count + " " + type);
          startTime = System.currentTimeMillis();
      }
      type.instantiate();
      if (verbose >= 1) {
        BootImageWriterMessages.say("Thread " + id + " instantiating type " + count + " " + type + " Time: " + (System.currentTimeMillis() - startTime) + "ms");
      }
    }
  }

}
