/*
 * (C) Copyright IBM Corp 2002, 2005
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_FileSystem;
import com.ibm.JikesRVM.VM_Magic;

import java.io.File;

import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.net.URL;

import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * The class loader used by Jikes RVM to load the application program.  Since
 * version 1.2 of the Sun API docs, the Application ClassLoader  and the
 * System Class Loader are officially the same thing.  (What Jikes RVM used to
 * call the "System Class Loader" is officially the "Bootstrap Class
 * Loader".)
 *
 * We use a two-link chain.  An ordinary user's class is loaded by this class
 * loader.  This class loader first delegates to its parent (the Bootstrap
 * Class Loader) before trying the class itself.
 *
 * @author Julian Dolby
 *
 * @modified Steven Augart, 2004-Mar-04 
 *  Renamed the former "system class loader" to the "bootstrap class loader".
 */
public class ApplicationClassLoader extends URLClassLoader {
  

  final static boolean DBG = false;
  
  static int numInstantiations = 0;

  /** For status printing, to make sure that, if an application class loader is
   *  created at boot image writing time, it won't leak out into the next
   *  mode.   This is actually not used any more, but it should never hurt,
   *  and can give one a sense of confidence when debugging Jikes RVM's
   *  classloaders.
   *  */
  boolean createdAtBootImageWritingTime;
  boolean createdWithRunningVM;

  public ApplicationClassLoader(String specifiedClasspath) {
    super(new URL[0]);
    if (DBG)
      VM.sysWriteln("The Application Class Loader has been instantiated ", numInstantiations, " times");
    ++numInstantiations;

    createdAtBootImageWritingTime =  VM.writingBootImage;
    createdWithRunningVM =  VM.runningVM;
    
    try {
      if (specifiedClasspath == null) {
        addURL(new URL("file", null, -1, System.getProperty("user.dir") + File.separator));
      } else {
        StringTokenizer tok = new StringTokenizer(specifiedClasspath, File.pathSeparator);
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
      VM.sysFail("JikesRVM: ApplicationClassLoader: Initialization Failed with a MalformedURLException; there was an error setting the application's classpath: " + e);
    }
  }

  /** Name of the Application Class Loader.  Actually used by Jikes RVM's
   * serialization code.
   * <P>
   * I intended this name to reflect both "SystemClassLoader" and
   * "ApplicationClassLoader".
   */ 
  public final static String myName = "SystemAppCL";

  public String toString() { 
    return myName
      + (createdAtBootImageWritingTime ? "-createdAtBootImageWritingTime" : "")
      + (createdWithRunningVM ? "" : "-NOTcreatedWithRunningVM")
      + (DBG 
         ? "@" + VM.addressAsHexString(VM_Magic.objectAsAddress(this)) 
         : "");
  }

  protected String findLibrary(String libName) {
    return null;
  }
}

                    
