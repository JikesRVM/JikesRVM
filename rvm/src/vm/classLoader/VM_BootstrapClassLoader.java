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

import java.net.MalformedURLException;
import java.net.URL;

import java.io.*;

/** 
 * Implements an object that functions as the bootstrap class loader.
 * This class is a Singleton pattern.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public final class VM_BootstrapClassLoader extends java.lang.ClassLoader {

  private HashMap loaded = new HashMap(); // Map Strings to VM_Types.

  private final static boolean DBG = false;

  /** Places whence we load bootstrap .class files. */
  private static String bootstrapClasspath;
  
  /**
   * Set list of places to be searched for vm classes and resources.
   * @param bootstrapClasspath path specification in standard "classpath"
   *    format 
   */
  public static void setBootstrapRepositories(String bootstrapClasspath) {
    VM_BootstrapClassLoader.bootstrapClasspath = bootstrapClasspath;
  }

  /**
   * @return List of places to be searched for VM classes and resources,
   *      in standard "classpath" format 
   */
  public static String getBootstrapRepositories() {
    return bootstrapClasspath;
  }

  /**
   * Initialize for execution.
   * @param bootstrapClasses names of directories containing the bootstrap
   * .class files, and the names of any .zip/.jar files.  
   * These are the ones that implement the VM and its
   * standard runtime libraries.  This may contain several names separated
   * with colons (':'), just 
   * as a classpath may.   (<code>null</code> ==> use the values specified by
   * {@link #setBootstrapRepositories} when the boot image was created.  This
   * feature is not actually used, but may be helpful in avoiding trouble.)
   */
  public static void boot(String bootstrapClasspath) {
    if (bootstrapClasspath != null)
      VM_BootstrapClassLoader.bootstrapClasspath = bootstrapClasspath;
    zipFileCache = new HashMap();
    if (VM.runningVM) {
      try {
        /* Here, we have to replace the fields that aren't carried over from
         * boot image writing time to run time.
         * This would be the following, if the fields weren't final:
         *
         * bootstrapClassLoader.definedPackages    = new HashMap();
         * bootstrapClassLoader.loadedClasses      = new HashMap();
         */
        VM_Entrypoints.classLoaderDefinedPackages.setObjectValueUnchecked(bootstrapClassLoader, new HashMap());
        VM_Entrypoints.classLoaderLoadedClasses.setObjectValueUnchecked(bootstrapClassLoader, new HashMap());
        if (DBG)
          VM.sysWriteln("VM_BootstrapClassLoader.boot(): loadedClasses is: ", VM_Entrypoints.classLoaderLoadedClasses.getObjectValueUnchecked(bootstrapClassLoader) == null ? "NULL" : "set");
        
        // We probably should really do something like the following, but we
        // don't, since there's no point in priming the cache -- this is
        // run time:
        //      for key in loaded {
        //        add an entry to loadedClasses, use VM_Type.getClassForType.
        //      }

      } catch (Exception e) {
        VM.sysWriteln("Failed to setup bootstrap class loader");
        VM.sysExit(-1);
      }
    }
  }

  /** Prevent other classes from constructing one. */
  private VM_BootstrapClassLoader() { 
    super(null); 
  }

  /* Interface */
  private final static VM_BootstrapClassLoader bootstrapClassLoader =
    new VM_BootstrapClassLoader();

  public static VM_BootstrapClassLoader getBootstrapClassLoader() { 
    return bootstrapClassLoader;
  }
  

  /**
   * Backdoor for use by VM_TypeReference.resolve when !VM.runningVM.
   * As of this writing, it is not used by any other classes. 
   * @throws NoClassDefFoundError
   */
  synchronized VM_Type loadVMClass(String className) throws NoClassDefFoundError {
    try {           
      InputStream is = getResourceAsStream(className.replace('.','/') + ".class");
      if (is == null) throw new NoClassDefFoundError(className);
      DataInputStream dataInputStream = new DataInputStream(is);
      VM_Type type = null;
      try {
        // Debugging:
        // VM.sysWriteln("loadVMClass: trying to resolve className " + className);
        type = VM_ClassLoader.defineClassInternal(className, dataInputStream, this);
        loaded.put(className, type);
      } finally {
        try {
          // Make sure the input stream is closed.
          dataInputStream.close();
        } catch (IOException e) { }
      }
      return type;
    } catch (NoClassDefFoundError e) {
      throw e;
    } catch (Throwable e) {
      // We didn't find the class, or it wasn't valid, etc.
      NoClassDefFoundError ncdf = new NoClassDefFoundError(className);
      ncdf.initCause(e);
      throw ncdf;
    }
  }

  public synchronized Class loadClass(String className, boolean resolveClass)
    throws ClassNotFoundException {
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
   * Search the bootstrap class loader's classpath for given class.
   *
   * @param className the name of the class to load
   * @return the class object, if it was found
   * @exception ClassNotFoundException if the class was not found, or was invalid
   */
  public Class findClass (String className) throws ClassNotFoundException {
    if (className.startsWith("[")) {
      VM_TypeReference typeRef = VM_TypeReference.findOrCreate(this, 
                                                               VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')));
      VM_Type ans = typeRef.resolve();
      loaded.put(className, ans);
      return ans.getClassForType();
    } else {    
      if ( ! VM.dynamicClassLoadingEnabled ) {
        VM.sysWrite("Trying to load a class (");
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
  public final static String myName = "BootstrapCL";
  
  public String toString() { return myName; }

  private static HashMap zipFileCache;
    
  private interface Handler {
    void process(ZipFile zf, ZipEntry ze) throws Exception;
    void process(File f) throws Exception;
    Object getResult();
  }

  public InputStream getResourceAsStream(final String name) {
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

  private Object getResourceInternal(String name, Handler h, boolean multiple) {
    if (name.startsWith(File.separator)) {
      name = name.substring(File.separator.length());
    }

    StringTokenizer tok = new StringTokenizer(getBootstrapRepositories(), File.pathSeparator);

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
