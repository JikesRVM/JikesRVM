/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;

/**
 * Implements an object that functions as the bootstrap class loader.
 * This class is a Singleton pattern.
 */
public final class BootstrapClassLoader extends java.lang.ClassLoader {

  private final ImmutableEntryHashMapRVM<String, RVMType> loaded =
    new ImmutableEntryHashMapRVM<String, RVMType>();

  /** Places whence we load bootstrap .class files. */
  private static String bootstrapClasspath;

  /**
   * Set list of places to be searched for vm classes and resources.
   * @param bootstrapClasspath path specification in standard "classpath"
   *    format
   */
  public static void setBootstrapRepositories(String bootstrapClasspath) {
    BootstrapClassLoader.bootstrapClasspath = bootstrapClasspath;
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
   * @param bootstrapClasspath names of directories containing the bootstrap
   * .class files, and the names of any .zip/.jar files.
   * These are the ones that implement the VM and its
   * standard runtime libraries.  This may contain several names separated
   * with colons (':'), just
   * as a classpath may.   (<code>null</code> ==> use the values specified by
   * {@link #setBootstrapRepositories} when the boot image was created.  This
   * feature is not actually used, but may be helpful in avoiding trouble.)
   */
  public static void boot(String bootstrapClasspath) {
    if (bootstrapClasspath != null) {
      BootstrapClassLoader.bootstrapClasspath = bootstrapClasspath;
    }
    zipFileCache = new HashMap<String, ZipFile>();
    if (VM.runningVM) {
      try {
        /* Here, we have to replace the fields that aren't carried over from
         * boot image writing time to run time.
         * This would be the following, if the fields weren't final:
         *
         * bootstrapClassLoader.definedPackages    = new HashMap();
         */
        Entrypoints.classLoaderDefinedPackages.setObjectValueUnchecked(bootstrapClassLoader,
                                                                          new java.util.HashMap<String, Package>());
      } catch (Exception e) {
        VM.sysFail("Failed to setup bootstrap class loader");
      }
    }
  }

  /** Prevent other classes from constructing one. */
  private BootstrapClassLoader() {
    super(null);
  }

  /* Interface */
  private static final BootstrapClassLoader bootstrapClassLoader = new BootstrapClassLoader();

  public static BootstrapClassLoader getBootstrapClassLoader() {
    return bootstrapClassLoader;
  }

  /**
   * Backdoor for use by TypeReference.resolve when !VM.runningVM.
   * As of this writing, it is not used by any other classes.
   * @throws NoClassDefFoundError
   */
  synchronized RVMType loadVMClass(String className) throws NoClassDefFoundError {
    try {
      InputStream is = getResourceAsStream(className.replace('.', File.separatorChar) + ".class");
      if (is == null) throw new NoClassDefFoundError(className);
      DataInputStream dataInputStream = new DataInputStream(is);
      RVMType type = null;
      try {
        // Debugging:
        // VM.sysWriteln("loadVMClass: trying to resolve className " + className);
        type = RVMClassLoader.defineClassInternal(className, dataInputStream, this);
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

  public synchronized Class<?> loadClass(String className, boolean resolveClass) throws ClassNotFoundException {
    if (!VM.runningVM) {
      return super.loadClass(className, resolveClass);
    }
    if (className.startsWith("L") && className.endsWith(";")) {
      className = className.substring(1, className.length() - 2);
    }
    RVMType loadedType = loaded.get(className);
    Class<?> loadedClass;
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
  public Class<?> findClass(String className) throws ClassNotFoundException {
    final boolean DBG=false;
    if (!VM.runningVM) {
      return super.findClass(className);
    }
    if (className.startsWith("[")) {
      TypeReference typeRef =
          TypeReference.findOrCreate(this, Atom.findOrCreateAsciiAtom(className.replace('.', '/')));
      RVMType ans = typeRef.resolve();
      loaded.put(className, ans);
      return ans.getClassForType();
    } else {
      if (!VM.fullyBooted) {
        VM.sysWrite("Trying to load a class (");
        VM.sysWrite(className);
        VM.sysWrite(") too early in the booting process, before dynamic");
        VM.sysWriteln(" class loading is enabled; aborting.");
        VM.sysFail("Trying to load a class too early in the booting process");
      }
      // class types: try to find the class file
      try {
        if (className.startsWith("L") && className.endsWith(";")) {
          className = className.substring(1, className.length() - 2);
        }
        InputStream is = getResourceAsStream(className.replace('.', File.separatorChar) + ".class");
        if (is == null) throw new ClassNotFoundException(className);
        DataInputStream dataInputStream = new DataInputStream(is);
        Class<?> cls = null;
        try {
          RVMType type = RVMClassLoader.defineClassInternal(className, dataInputStream, this);
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
          VM.sysWrite("About to throw ClassNotFoundException(", className, ") because we got this Throwable:");
          e.printStackTrace();
        }
        // We didn't find the class, or it wasn't valid, etc.
        throw new ClassNotFoundException(className, e);
      }
    }
  }

  /** Keep this a static field, since it's looked at in
   *  {@link MemberReference#parse}. */
  public static final String myName = "BootstrapCL";

  public String toString() { return myName; }

  private static HashMap<String, ZipFile> zipFileCache;

  private interface Handler<T> {
    void process(ZipFile zf, ZipEntry ze) throws Exception;

    void process(File f) throws Exception;

    T getResult();
  }

  public InputStream getResourceAsStream(final String name) {
    Handler<InputStream> findStream = new Handler<InputStream>() {
      InputStream stream;

      public InputStream getResult() { return stream; }

      public void process(ZipFile zf, ZipEntry ze) throws Exception {
        stream = zf.getInputStream(ze);
      }

      public void process(File file) throws Exception {
        stream = new FileInputStream(file);
      }
    };

    return getResourceInternal(name, findStream, false);
  }

  public URL findResource(final String name) {
    Handler<URL> findURL = new Handler<URL>() {
      URL url;

      public URL getResult() { return url; }

      public void process(ZipFile zf, ZipEntry ze) throws Exception {
        url = new URL("jar", null, -1, "file:" + zf.getName() + "!/" + name);
      }

      public void process(File file) throws Exception {
        url = new URL("file", null, -1, file.getName());
      }
    };

    return getResourceInternal(name, findURL, false);
  }

  public Enumeration<URL> findResources(final String name) {
    Handler<Enumeration<URL>> findURL = new Handler<Enumeration<URL>>() {
      Vector<URL> urls;

      public Enumeration<URL> getResult() {
        if (urls == null) urls = new Vector<URL>();
        return urls.elements();
      }

      public void process(ZipFile zf, ZipEntry ze) throws Exception {
        if (urls == null) urls = new Vector<URL>();
        urls.addElement(new URL("jar", null, -1, "file:" + zf.getName() + "!/" + name));
      }

      public void process(File file) throws Exception {
        if (urls == null) urls = new Vector<URL>();
        urls.addElement(new URL("file", null, -1, file.getName()));
      }
    };

    return getResourceInternal(name, findURL, true);
  }

  private <T> T getResourceInternal(String name, Handler<T> h, boolean multiple) {
    if (name.startsWith(File.separator)) {
      name = name.substring(File.separator.length());
    }

    StringTokenizer tok = new StringTokenizer(getBootstrapRepositories(), File.pathSeparator);

    while (tok.hasMoreElements()) {
      try {
        String path = tok.nextToken();
        if (path.endsWith(".jar") || path.endsWith(".zip")) {
          ZipFile zf = zipFileCache.get(path);
          if (zf == null) {
            zf = new ZipFile(path);
            if (zf == null) {
              continue;
            } else {
              zipFileCache.put(path, zf);
            }
          }
          // Zip spec. states that separator must be '/' in the path
          if (File.separatorChar != '/') {
            name = name.replace(File.separatorChar, '/');
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
          }
        } else {
          File file = new File(path + File.separator + name);
          if (file.exists()) {
            h.process(file);
            if (!multiple) return h.getResult();
          }
        }
      } catch (Exception e) {
      }
    }

    return (multiple) ? h.getResult() : null;
  }
}
