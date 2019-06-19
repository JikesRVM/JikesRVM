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
package org.jikesrvm.classlibrary;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.zip.ZipFile;

import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;

public class ClassLoaderSupport {

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

  public static void registerDefinedPackage(String name, Package p) {
    definedPackages.put(name, p);
  }

  public static Class<?> defineClass(ClassLoader cl, String name, byte[] data,
      int offset, int len, ProtectionDomain pd) throws ClassFormatError {
    RVMType vmType = RVMClassLoader.defineClassInternal(name, data, offset, len, cl, pd);
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

  public static void resolveClass(Class<?> c) {
    RVMType cls = JikesRVMSupport.getTypeForClass(c);
    cls.prepareForFirstUse();
  }

  public static Class<?> loadClass(String name, boolean resolve)
  throws ClassNotFoundException {
    return BootstrapClassLoader.getBootstrapClassLoader().loadClass(name, resolve);
  }

  public static Class<?> findLoadedClass(ClassLoader cl, String name) {
    ImmutableEntryHashMapRVM<String,Class<?>> mapForCL = loadedClasses.get(cl);
    if (mapForCL == null) return null;
    return mapForCL.get(name);
  }

  public static URL getResource(String bootClasspath, String name) {
    Enumeration<URL> e = getResources(bootClasspath, name);
    if (e.hasMoreElements())
      return e.nextElement();
    return null;
  }

  public static Enumeration<URL> getResources(String bootClasspath, String name) {
    StringTokenizer st = new StringTokenizer(
        bootClasspath,
        File.pathSeparator);
    LinkedList<URL> v = new LinkedList<URL>();
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
        synchronized (bootjars) {
          zip = bootjars.get(file.getName());
        }
        if (zip == null) {
          try {
            zip = new ZipFile(file);
            synchronized (bootjars) {
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
    return Collections.enumeration(v);
  }

  public static Package getPackage(String name) {
    return definedPackages.get(name);
  }

  public static Package[] getPackages() {
    Package[] packages = new Package[definedPackages.size()];

    Iterator<Package> it = definedPackages.valueIterator();
    int idx = 0;
    while (it.hasNext()) {
      packages[idx++] = it.next();
    }
    return packages;
  }

  public static Class<?> getPrimitiveClass(char type) {
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

  public static boolean defaultAssertionStatus() {
    return true;
  }

  public static ClassLoader getSystemClassLoader() {
    return RVMClassLoader.getApplicationClassLoader();
  }

  public static Class<?>[] getAllLoadedClasses() {
    LinkedList<Class<?>> classList = new LinkedList<Class<?>>();
    for (ImmutableEntryHashMapRVM<String,Class<?>> classes : loadedClasses.values()) {
      for (Class<?> cl : classes.values()) {
        classList.add(cl);
      }
    }
    Class<?>[] result = new Class[classList.size()];
    return classList.toArray(result);
  }

  public static Class<?>[] getInitiatedClasses(ClassLoader classLoader) {
    ImmutableEntryHashMapRVM<String,Class<?>> mapForCL = loadedClasses.get(classLoader);
    if (mapForCL == null) return new Class[]{};
    LinkedList<Class<?>> classList = new LinkedList<Class<?>>();
    for (Class<?> cl : mapForCL.values())
      classList.add(cl);
    Class<?>[] result = new Class[classList.size()];
    return classList.toArray(result);
  }

}
