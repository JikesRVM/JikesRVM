/*
 * Copyright © IBM Corp 2002, 2004
 */
//$Id$

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_FileSystem;
import com.ibm.JikesRVM.classloader.VM_ClassLoader;

import java.io.File;
import java.util.StringTokenizer;
import java.net.URLClassLoader;
import java.net.URL;
import java.util.HashMap;

import java.net.MalformedURLException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/**
 * A class loader for loading alternative instances of the Jikes RVM,
 * java.*, and gnu.classpath classes, when we want to be able to write out a
 * new boot image from an already running VM.
 *
 * This will, I hope, attempt to adapt to such methods.
 *
 * @author Steven Augart, based on code by Julian Dolby
 */
public class AlternateRealityClassLoader extends URLClassLoader {

  private HashMap loaded = new HashMap();

  // /** This is a final method in ClassLoader, so we won't override it yet. */
//   public Class findLoadedClass(String name) {
//     return (Class) loaded.get(name);
//   }


  final static boolean verbose = true;

  private String nativeLibDir = null;
  final static Util u = null;

  private AlternateRealityClassLoader(String specifiedClassPath,
                                      String specifiedNativeLibDir) {
    super(new URL[0]);
    nativeLibDir = specifiedNativeLibDir;

    final String cp = specifiedClassPath;
    if (cp == null)
      return;                   // all done.  Not very useful, is it?
    try {
      StringTokenizer tok = new StringTokenizer(cp, File.pathSeparator);
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
    } catch (MalformedURLException e) {
      VM.sysWrite("Error setting the classpath for the AlternateRealityClassLoader " + e);
      VM.sysExit(-1);
    }

  }

  public String toString() { return "AlternateRealityCL"; }
  /**
   * Load a class using this ClassLoader or its parent, possibly resolving
   * it as well using <code>resolveClass()</code>.
   *
   * Subclasses do not normally override this method, but this subclass does,
   * because of the AlternateRealityClassLoader's  special handling of VM
   * classes and java.lang classes.
   *
   *
   * @param name the fully qualified name of the class to load
   * @param resolve whether or not to resolve the class
   * @return the loaded class
   * @throws ClassNotFoundException if the class cannot be found
   */
  protected synchronized Class loadClass(String name, boolean resolve)
    throws ClassNotFoundException
  {
    if (verbose)
      u.epln("ENTERED: loadClass(" + name + ", " + resolve + ")");
    
    /* If we've already loaded this class, return it. */
    Class c = findLoadedClass(name);
    if (c != null)
      return c;

    // Do not delegate up; we're in an alternate reality!

    c = findClass(name);        // if failure, throws ClassNotFoundException
    loaded.put(name, c);
    if (resolve)
      resolveClass(c);
    return c;
  }


  public static ClassLoader init(String specifiedClassPath, 
                                 String specifiedNativeLibDir) 
    throws IllegalStateException 
  {
    if (verbose)
      u.epln("**Entering: AlternateRealityClassLoader.init(" + specifiedClassPath
           + ", " + specifiedNativeLibDir + ")");

    if (verbose)
      u.epln("Trying for an Alternate Reality ClassLoader via new AlternateRealityClassLoader(,)");
    ClassLoader altCL
      = new AlternateRealityClassLoader(specifiedClassPath, 
                                        specifiedNativeLibDir);

    /* Try to set this as the Alternate Reality Class loader, under Jikes
       RVM.  Under Jikes RVM, the "VM" here will automatically resolve to the
       running VM.  Under other systems, it will resolve to the one in the
       standard application classpath. */
    if (VM.runningVM) {
      if (verbose)
        u.epln("VM.runningVM is true.  We're under Jikes RVM.  It makes sense for us to set the AlternateRealityClassLoader.");
      if (VM_ClassLoader.getAlternateRealityClassLoader() != null)
        throw new IllegalStateException("The VM already has an alternate Reality Class Loader set up; this should never happen.");
      VM_ClassLoader.setAlternateRealityClassLoader(altCL);
    }
    return altCL;
  }

//   protected String findLibrary(String libName) {
//     if (nativeLibDir == null)
//       throw new Error("This function should never be called; the alternate reality class loader classes shouldn't have any native methods that get invoked.  Should they?");
//      String platformLibName = System.mapLibraryName(libName);
//      String lib = nativeLibDir + File.separator + platformLibName;
//      return VM_FileSystem.access(lib, VM_FileSystem.ACCESS_R_OK) == 0 ? lib : null;
//   }
}

                    
