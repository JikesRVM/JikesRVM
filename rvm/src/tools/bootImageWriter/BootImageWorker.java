/*
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

  public static int verbose = 1;
  static Enumeration enum;
  int id;

  public static void startup (Enumeration e) {
    enum = e;
  }

  public void run () {

    int count = 0;
    while (true) {
      VM_Type type = null;
      synchronized (enum) {
        if (enum.hasMoreElements()) {
          type = (VM_Type) enum.nextElement();
          count++;
        }
      }
      if (type == null) 
        return;
      if (verbose >= 1) 
          BootImageWriterMessages.say("Thread " + id + " instantiating type " + count + " " + type);
      type.instantiate();
    }
  }

}
