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
package java.lang;

import gnu.classpath.Configuration;
import gnu.classpath.SystemProperties;
import gnu.java.lang.InstrumentationImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.zip.ZipFile;

import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMType;

import org.jikesrvm.util.ImmutableEntryHashMapRVM;

/**
 * Jikes RVM impl of VMClassLoader.
 * See GNU classpath reference impl for javadoc.
 */
final class VMClassLoader {
  /**
   * A map of maps. The first map is indexed by the classloader. The
   * map this finds then maps String class names to classes
   */
  private static final ImmutableEntryHashMapRVM<ClassLoader,ImmutableEntryHashMapRVM<String,Class<?>>> loadedClasses =
    new ImmutableEntryHashMapRVM<ClassLoader,ImmutableEntryHashMapRVM<String,Class<?>>>();

  /**
   * The map for the boot strap class loader that is often represented by null
   */
  private static final ImmutableEntryHashMapRVM<String,Class<?>> bootStrapLoadedClasses =
    new ImmutableEntryHashMapRVM<String,Class<?>>();

  /** packages loaded by the bootstrap class loader */
  private static final ImmutableEntryHashMapRVM<String,Package> definedPackages =
    new ImmutableEntryHashMapRVM<String,Package>();

  private static final ImmutableEntryHashMapRVM<String,ZipFile> bootjars =
    new ImmutableEntryHashMapRVM<String,ZipFile>();

  static {
    String[] packages = getBootPackages();

    if (packages != null) {
      String specName =
        SystemProperties.getProperty("java.specification.name");
      String vendor =
        SystemProperties.getProperty("java.specification.vendor");
      String version =
        SystemProperties.getProperty("java.specification.version");

      for (String packageName : packages) {
        final Package p = new Package(packageName,
            specName,
            vendor,
            version,
            "GNU Classpath",
            "GNU",
            Configuration.CLASSPATH_VERSION,
            null,
            null);

        definedPackages.put(packageName, p);
      }
    }
  }

  static Class<?> defineClass(ClassLoader cl, String name,
                              byte[] data, int offset, int len,
                              ProtectionDomain pd)
                              throws ClassFormatError {
    RVMType vmType = RVMClassLoader.defineClassInternal(name, data, offset, len, cl);
    Class<?> ans = vmType.getClassForType();
    JikesRVMSupport.setClassProtectionDomain(ans, pd);
    ImmutableEntryHashMapRVM<String,Class<?>> mapForCL;
    if (cl == null || cl == BootstrapClassLoader.getBootstrapClassLoader()) {
      mapForCL = bootStrapLoadedClasses;
    } else {
      mapForCL = loadedClasses.get(cl);
      if (mapForCL == null) {
        mapForCL = new ImmutableEntryHashMapRVM<String,Class<?>>();
        loadedClasses.put(cl, mapForCL);
      }
    }
    mapForCL.put(ans.getName(), ans);
    return ans;
  }

  static void resolveClass(Class<?> c) {
    RVMType cls = JikesRVMSupport.getTypeForClass(c);
    cls.resolve();
    cls.instantiate();
    cls.initialize();
  }

  static Class<?> loadClass(String name, boolean resolve)
  throws ClassNotFoundException {
    return BootstrapClassLoader.getBootstrapClassLoader().loadClass(name, resolve);
  }

  static URL getResource(String name) {
    Enumeration<URL> e = getResources(name);
    if (e.hasMoreElements())
      return e.nextElement();
    return null;
  }

  static Enumeration<URL> getResources(String name) {
    StringTokenizer st = new StringTokenizer(
        SystemProperties.getProperty("java.boot.class.path", "."),
        File.pathSeparator);
    Vector<URL> v = new Vector<URL>();
    while (st.hasMoreTokens()) {
      File file = new File(st.nextToken());
      if (file.isDirectory()) {
        try {
          File f = new File(file, name);
          if (!f.exists()) continue;
          v.add(new URL("file://" + f.getAbsolutePath()));
        } catch (MalformedURLException e) {
          throw new Error(e);
        }
      } else if (file.isFile()) {
        ZipFile zip;
        synchronized(bootjars) {
          zip = bootjars.get(file.getName());
        }
        if(zip == null) {
          try {
            zip = new ZipFile(file);
            synchronized(bootjars) {
              bootjars.put(file.getName(), zip);
            }
          } catch (IOException e) {
            continue;
          }
        }
        String zname = name.startsWith("/") ? name.substring(1) : name;
        if (zip.getEntry(zname) == null)
          continue;
        try {
          v.add(new URL("jar:file://" +
                        file.getAbsolutePath() + "!/" + zname));
        } catch (MalformedURLException e) {
          throw new Error(e);
        }
      }
    }
    return v.elements();
  }

  private static String[] getBootPackages() {
    URL indexList = getResource("META-INF/INDEX.LIST");
    if (indexList != null) {
      try {
        Set<String> packageSet = new HashSet<String>();
        String line;
        int lineToSkip = 3;
        BufferedReader reader = new BufferedReader(new InputStreamReader(indexList.openStream()));
        while ((line = reader.readLine()) != null) {
          if (lineToSkip == 0) {
            if (line.length() == 0) {
              lineToSkip = 1;
            } else {
              packageSet.add(line.replace('/', '.'));
            }
          } else {
            lineToSkip--;
          }
        }
        reader.close();
        return packageSet.toArray(new String[packageSet.size()]);
      } catch (IOException e) {
        return new String[0];
      }
    } else {
      return new String[0];
    }
  }


  static Package getPackage(String name) {
    return definedPackages.get(name);
  }

  static Package[] getPackages() {
    Package[] packages = new Package[definedPackages.size()];

    Iterator<Package> it = definedPackages.valueIterator();
    int idx = 0;
    while (it.hasNext()) {
      packages[idx++] = it.next();
    }
    return packages;
  }

  static Class<?> getPrimitiveClass(char type) {
    RVMType t;
    switch (type) {
    case 'Z':
      t = RVMType.BooleanType;
      break;
    case 'B':
      t = RVMType.ByteType;
      break;
    case 'C':
      t = RVMType.CharType;
      break;
    case 'D':
      t = RVMType.DoubleType;
      break;
    case 'F':
      t = RVMType.FloatType;
      break;
    case 'I':
      t = RVMType.IntType;
      break;
    case 'J':
      t = RVMType.LongType;
      break;
    case 'S':
      t = RVMType.ShortType;
      break;
    case 'V':
      t = RVMType.VoidType;
      break;
    default:
      throw new NoClassDefFoundError("Invalid type specifier: " + type);
    }
    return t.getClassForType();
  }

  static boolean defaultAssertionStatus() {
    return true;
  }

  @SuppressWarnings({"unchecked","unused"}) // TODO should this method be deleted ?
  private static Map packageAssertionStatus() {
    return new HashMap();
  }

  @SuppressWarnings({"unchecked","unused"}) // TODO should this method be deleted ?
  private static Map classAssertionStatus() {
    return new HashMap();
  }

  static ClassLoader getSystemClassLoader() {
    return RVMClassLoader.getApplicationClassLoader();
  }

  static Class<?>[] getAllLoadedClasses() {
    Vector<Class<?>> classList = new Vector<Class<?>>();
    for (ImmutableEntryHashMapRVM<String,Class<?>> classes : loadedClasses.values()) {
      for (Class<?> cl : classes.values()) {
        classList.add(cl);
      }
    }
    Class<?>[] result = new Class[classList.size()];
    return classList.toArray(result);
  }

  static Class<?>[] getInitiatedClasses(ClassLoader classLoader) {
    ImmutableEntryHashMapRVM<String,Class<?>> mapForCL = loadedClasses.get(classLoader);
    if (mapForCL == null) return new Class[]{};
    Vector<Class<?>> classList = new Vector<Class<?>>();
    for (Class<?> cl : mapForCL.values())
      classList.add(cl);
    Class<?>[] result = new Class[classList.size()];
    return classList.toArray(result);
  }

  static Class<?> findLoadedClass(ClassLoader cl, String name) {
    ImmutableEntryHashMapRVM<String,Class<?>> mapForCL = loadedClasses.get(cl);
    if (mapForCL == null) return null;
    return mapForCL.get(name);
  }

  private static Instrumentation instrumenter = null;

  static void setInstrumenter(Instrumentation theInstrumenter) {
    instrumenter = theInstrumenter;
  }

  static Class<?> defineClassWithTransformers(ClassLoader loader, String name, byte[] data,
      int offset, int len, ProtectionDomain pd) {

    if (instrumenter != null) {
      byte[] modifiedData = new byte[len];
      System.arraycopy(data, offset, modifiedData, 0, len);
      String jvmName = name.replace('.', '/');
      modifiedData =
        ((InstrumentationImpl)instrumenter).callTransformers(loader, jvmName, null, pd, modifiedData);

      return defineClass(loader, name, modifiedData, 0, modifiedData.length, pd);
    } else {
      return defineClass(loader, name, data, offset, len, pd);
    }
  }
}
