/*
 * (C) Copyright IBM Corp 2002, 2004
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_FileSystem;
import java.io.File;
import java.util.StringTokenizer;
import java.net.*;

/**
 * The class loader used by Jikes RVM to load instances of the Jikes RVM
 * and java.* classes, when we want to be able to write out a new boot image
 * from an already running VM.
 *
 * @author Steven Augart, based on code by Julian Dolby
 */
public class AlternateRealityClassLoader extends URLClassLoader {

  public AlternateRealityClassLoader(String specifiedClassPath) {
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
      VM.sysWrite("Error setting the classpath for the AlternateRealityClassLoader " + e);
      VM.sysExit(-1);
    }
  }

  public String toString() { return "SystemAppCL"; }

  protected String findLibrary(String libName) {
    String platformLibName = System.mapLibraryName(libName);
    String path = VM_ClassLoader.getSystemNativePath();
    String lib = path + File.separator + platformLibName;
    return VM_FileSystem.access(lib, VM_FileSystem.ACCESS_R_OK) == 0 ? lib : null;
  }
}

                    
