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
 * The class loader used by Jikes RVM to load the application program
 *
 * @author Julian Dolby
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

  public String toString() { return "AppCL"; }

  protected String findLibrary(String libName) {
    String platformLibName = System.mapLibraryName(libName);
    String path = VM_ClassLoader.getSystemNativePath();
    String lib = path + File.separator + platformLibName;
    return VM_FileSystem.access(lib, VM_FileSystem.ACCESS_R_OK) == 0 ? lib : null;
  }
}

                    
