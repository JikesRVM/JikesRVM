/*
 * (C) Copyright IBM Corp 2001,2002,2005
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;

import java.util.StringTokenizer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;
import java.util.zip.*;

import java.net.URL;

import java.io.*;

/** 
 * Implements an object that functions as the application class loader.
 * This class is a Singleton pattern.  It is intended to be a working
 * replacement for our old ApplicationClassLoader class, the one that
 * inherits from java.net.URLClassLoader (and which requires that all
 * sorts of stuff be enabled just so that it can be initialized)
 * <p>
 * Almost all of this is cribbed from VM_BootstrapClassLoader.  The
 * Right Thing to do would be for us to write a common class for both of
 * them to extend, since they are virtually identical.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public final class VM_ApplicationClassLoader2 extends java.lang.ClassLoader {

  VM_BootstrapClassLoader parent = VM_BootstrapClassLoader.getBootstrapClassLoader();

  private HashMap loaded = new HashMap(); // Map Strings to VM_Types.

  private final static boolean DBG = false;

  /** Places whence we load application .class files. */
  private static String applicationClasspath = ".";
  
  /**
   * Set list of places to be searched for application classes and resources.
   * @param applicationClasspath path specification in standard "classpath"
   *    format.   It is the names of directories containing the application
   * .class files, and the names of any .zip/.jar files.  
   * This may contain several names separated with File.pathSeparator (a
   * colon ':' on every platform we currently run on), just as a classpath may.
   */
  public static void setApplicationRepositories(String applicationClasspath) {
    VM_ApplicationClassLoader2.applicationClasspath = applicationClasspath;
  }

  /**
   * @return List of places to be searched for VM classes and resources,
   *      in standard "classpath" format 
   */
  public static String getApplicationRepositories() {
    return applicationClasspath;
  }

  /**
   * Initialize for execution.
   */
  public static void boot(String applicationClasspath) {
    if (applicationClasspath != null)
      VM_ApplicationClassLoader2.applicationClasspath = applicationClasspath;
    zipFileCache = new HashMap();
    if (VM.runningVM) {
      try {
        /* Here, we have to replace the fields that aren't carried over from
         * boot image writing time to run time.
         * This would be the following, if the fields weren't final:
         *
         * appClassLoader.definedPackages    = new HashMap();
         * appClassLoader.loadedClasses      = new HashMap();
         */
        VM_Entrypoints.classLoaderDefinedPackages.setObjectValueUnchecked(appClassLoader, new HashMap());
        VM_Entrypoints.classLoaderLoadedClasses.setObjectValueUnchecked(appClassLoader, new HashMap());
        if (DBG)
          VM.sysWriteln("VM_ApplicationClassLoader2.boot(): loadedClasses is: ", VM_Entrypoints.classLoaderLoadedClasses.getObjectValueUnchecked(appClassLoader) == null ? "NULL" : "set");
        
        // We probably should really do something like the following, but we
        // don't, since there's no point in priming the cache -- this is
        // run time:
        //      for key in loaded {
        //        add an entry to loadedClasses, use VM_Type.getClassForType.
        //      }

      } catch (Exception e) {
        VM.sysWriteln("Failed to setup application class loader");
        VM.sysExit(-1);
      }
    }
  }

  /** Prevent other classes from constructing one. */
  private VM_ApplicationClassLoader2() { 
    super(null);                // We could make this a reference to the
                                // bootstrap class loader instead of null --
                                // it comes out to the same thing, or should.
    
  }

  /* Interface */
  private final static VM_ApplicationClassLoader2 appClassLoader =
    new VM_ApplicationClassLoader2();

  public static VM_ApplicationClassLoader2 getApplicationClassLoader() { 
    return appClassLoader;
  }
  

  public synchronized Class loadClass(String className, boolean resolveClass)
    throws ClassNotFoundException 
  {
    try {
      return parent.loadClass(className, resolveClass);
    } catch (ClassNotFoundException cnfe) {
      // Not in the system path; try here in the application  instead.
    }

    if (className.startsWith("L") && className.endsWith(";")) {
      className = className.substring(1, className.length()-2);
    }
    VM_Type loadedType = (VM_Type)loaded.get(className);
    Class loadedClass;
    if (loadedType == null) {
      loadedClass = findClass(className);
    } else {
      loadedClass = loadedType.getClassForType();
    }
    if (resolveClass) {
      resolveClass(loadedClass);
    }
    return loadedClass;
  }

  /**
   * Search the application class loader's classpath for given class.
   *
   * @param className the name of the class to load
   * @return the class object, if it was found
   * @exception ClassNotFoundException if the class was not found, or was invalid
   */
  public Class findClass(String className) throws ClassNotFoundException {
    try {
      return parent.findClass(className);
    } catch (ClassNotFoundException cnfe) {
      // Not in the system path; try here instead.
    }

    if (className.startsWith("[")) {
      VM_TypeReference typeRef = VM_TypeReference.findOrCreate(this, 
                                                               VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')));
      VM_Type ans = typeRef.resolve();
      loaded.put(className, ans);
      return ans.getClassForType();
    } else {    
      if ( ! VM.dynamicClassLoadingEnabled ) {
        VM.sysWrite("Trying to load an Application class (");
        VM.sysWrite(className);
        VM.sysWrite(") too early in the booting process, before dynamic");
        VM.sysWriteln(" class loading is enabled; aborting.");
        VM.sysFail("Trying to load a class too early in the booting process");
      }
      // class types: try to find the class file
      try {         
        if (className.startsWith("L") && className.endsWith(";")) {
          className = className.substring(1, className.length()-2);
        }
        InputStream is = getResourceAsStream(className.replace('.','/') + ".class");
        if (is == null) throw new ClassNotFoundException(className);
        DataInputStream dataInputStream = new DataInputStream(is);
        Class cls = null;
        try {
          VM_Type type = VM_ClassLoader.defineClassInternal(className, dataInputStream, this);
          loaded.put(className, type);
          cls = type.getClassForType();
        } finally {
          try {
            // Make sure the input stream is closed.
            dataInputStream.close();
          } catch (IOException e) { }
        }
        return cls;
      } catch (ClassNotFoundException e) {
        throw e;
      } catch (Throwable e) {
        if (DBG) {
          VM.sysWrite("About to throw ClassNotFoundException(", className,
                      ") because we got this Throwable:");
          e.printStackTrace();
        }
        // We didn't find the class, or it wasn't valid, etc.
        throw new ClassNotFoundException(className, e);
      }
    }
  }
  
  /** Keep this a static field, since it's looked at in
   *  {@link VM_MemberReference#parse}. */ 
  public final static String myName = "ApplicationCL";
  
  public String toString() { return myName; }

  private static HashMap zipFileCache;
    
  private interface Handler {
    void process(ZipFile zf, ZipEntry ze) throws Exception;
    void process(File f) throws Exception;
    Object getResult();
  }

  public InputStream getResourceAsStream(final String name) {
    InputStream ret = parent.getResourceAsStream(name);
    if (ret != null)
      return ret;

    Handler findStream = new Handler() {
        InputStream stream;

        public Object getResult() { return stream; }

        public void process(ZipFile zf, ZipEntry ze) throws Exception {
          stream = zf.getInputStream(ze);
        }

        public void process(File file) throws Exception {
          stream = new FileInputStream(file);
        }
      };

    return (InputStream)getResourceInternal(name, findStream, false);
  }

  public URL findResource(final String name) {
    URL ret = parent.findResource(name);
    if (ret != null)
      return ret;
    Handler findURL = new Handler() {
        URL url;

        public Object getResult() { return url; }

        public void process(ZipFile zf, ZipEntry ze) throws Exception {
          url = new URL("jar", null, -1, "file:" + zf.getName() + "!/" +name);
        }

        public void process(File file) throws Exception {
          url = new URL("file", null, -1, file.getName());
        }
      };

      return (URL)getResourceInternal(name, findURL, false);
  }

  public Enumeration findResources(final String name) {
    Enumeration ret = parent.findResources(name);
    if (ret != null && ret.hasMoreElements()) {
      return ret;
    }
    ret = null;

    Handler findURL = new Handler() {
        Vector urls;

        public Object getResult() { 
          if (urls == null) urls = new Vector();
          return urls.elements(); 
        }
        
        public void process(ZipFile zf, ZipEntry ze) throws Exception {
          if (urls == null) urls = new Vector();
          urls.addElement(new URL("jar", null, -1, "file:" + zf.getName() + "!/" +name));
        }

        public void process(File file) throws Exception {
          if (urls == null) urls = new Vector();
          urls.addElement(new URL("file", null, -1, file.getName()));
        }
      };

    return (Enumeration)getResourceInternal(name, findURL, true);
  }

  /** This is quite clever, since we only parse when we have to. */
  private Object getResourceInternal(String name, Handler h, boolean multiple) {
    if (name.startsWith(File.separator)) {
      name = name.substring(File.separator.length());
    }

    StringTokenizer tok = new StringTokenizer(getApplicationRepositories(), File.pathSeparator);

    while (tok.hasMoreElements()) {
      try {
        String path = tok.nextToken();
        if (path.endsWith(".jar") || path.endsWith(".zip")) {
          ZipFile zf = (ZipFile) zipFileCache.get(path);
          if (zf == null) {
            zf = new ZipFile(path);
            if (zf == null) {
              continue;
            } else {
              zipFileCache.put(path, zf);
            }
          }

          ZipEntry ze = zf.getEntry(name);
          if (ze == null) continue;
          
          h.process(zf, ze);
          if (!multiple) return h.getResult();
        } else if (path.endsWith(File.separator)) {
          File file = new File(path + name);
          if (file.exists()) {
            h.process(file);
            if (!multiple) return h.getResult();
          } else {
            continue;
          }
        } else {
          File file = new File(path + File.separator + name);
          if (file.exists()) {
            h.process(file);
            if (!multiple) return h.getResult();
          } else {
            continue;
          }
        }
      } catch (Exception e) {
        continue;
      }
    }

    return (multiple)? h.getResult() : null;
  }
}
