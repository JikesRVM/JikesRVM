/*
 * (C) Copyright IBM Corp 2001,2002
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
 * TODO: Perhaps this should be renamed one day to VM_BootstrapClassLoader.
 * HOWEVER, under our current source code control system (CVS), it is a major
 * hassle to rename files, especially if you want to preserve their history.
 * Moreover, at least one of the core team members uses "rdist" to copy files
 * among machines; leaving behind a class named VM_SystemClassLoader would
 * be confusing at best.  (rdist is good at adding files and changing modified
 * files, but not so good at deleting them.)   --Steve Augart, 24 March 2004
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public final class VM_SystemClassLoader extends java.lang.ClassLoader {

  private HashMap loaded = new HashMap(); // Map Strings to VM_Types.


  public static void boot() {
    zipFileCache = new HashMap();
    // the following idiot reflection hack is because the field is final :(
    if (VM.runningVM) {
      try {
        VM_Entrypoints.classLoaderDefinedPackages.setObjectValueUnchecked(vmClassLoader, new HashMap());
      } catch (Exception e) {
        VM.sysWriteln("failed to setup system class loader");
        VM.sysExit(-1);
      }
    }
  }

  // prevent other classes from constructing
  private VM_SystemClassLoader() { super(null); }

  /* Interface */
  private final static VM_SystemClassLoader vmClassLoader =
    new VM_SystemClassLoader();

  public static VM_SystemClassLoader getVMClassLoader() { 
    return vmClassLoader;
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
   * Search the system class loader's classpath for given class.
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
        // We didn't find the class, or it wasn't valid, etc.
        throw new ClassNotFoundException(className);
      }
    }
  }
  
  public String toString() { return "BootstrapCL"; }

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

    StringTokenizer tok = new StringTokenizer(VM_ClassLoader.getVmRepositories(), File.pathSeparator);

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
