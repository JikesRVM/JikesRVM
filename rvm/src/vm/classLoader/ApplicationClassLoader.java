/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_FileSystem;
import java.io.File;
import java.util.StringTokenizer;
import java.net.*;

/**
 * The class loader used by Jikes RVM to load the application program.  Since
 * version 1.2 of the Sun API docs, the Application ClassLoader  and the
 * System Class Loader are officially the same thing.  (What Jikes RVM used to
 * call the "System Class Loader" is officially the "Bootstrap Class
 * Loader".)
 *
 * We use a two-link chain.  An ordinary user's class is loaded by this class
 * loader.  This class loader first delegates to its parent before trying the
 * class itself.
 *
 * @author Julian Dolby
 *
 * @modified Steven Augart, 2004-Mar-04 
 *  Renamed the former "system class loader" to the "bootstrap class loader".
 */
public class ApplicationClassLoader extends URLClassLoader {

  public ApplicationClassLoader(String specifiedClassPath) {
    super(new URL[0]);

    try {
      if (specifiedClassPath == null) {
        addURL(new URL("file", null, -1, System.getProperty("user.dir") + File.separator));
      } else {
        StringTokenizer tok = new StringTokenizer(specifiedClassPath, File.pathSeparator);
        while (tok.hasMoreElements()) {
          String elt = tok.nextToken();
          
          if (!(elt.endsWith(".jar") || elt.endsWith(".zip"))) {
            if (! elt.endsWith( File.separator )) {
              elt += File.separator;
            }
          }

          if (elt.indexOf(":") != -1) {
            addURL(new URL(elt));
          } else if (elt.startsWith(File.separator)) {
            addURL(new URL("file", null, -1, elt));
          } else {
            addURL(new URL("file", null, -1, System.getProperty("user.dir") + File.separator + elt));
          }
        }
      }
    } catch (MalformedURLException e) {
      VM.sysWrite("error setting classpath " + e);
      VM.sysExit(-1);
    }
  }

  public String toString() { return "SystemAppCL"; }

  protected String findLibrary(String libName) {
    return null;
  }
}

                    
