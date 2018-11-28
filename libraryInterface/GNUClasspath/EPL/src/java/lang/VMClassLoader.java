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
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jikesrvm.classlibrary.ClassLoaderSupport;

/**
 * Jikes RVM impl of VMClassLoader.
 * See GNU classpath reference impl for javadoc.
 */
final class VMClassLoader {

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

        ClassLoaderSupport.registerDefinedPackage(packageName, p);
      }
    }
  }

  static Class<?> defineClass(ClassLoader cl, String name,
                              byte[] data, int offset, int len,
                              ProtectionDomain pd)
                              throws ClassFormatError {
    return ClassLoaderSupport.defineClass(cl, name, data, offset, len, pd);
  }

  static void resolveClass(Class<?> c) {
    ClassLoaderSupport.resolveClass(c);
  }

  static Class<?> loadClass(String name, boolean resolve)
  throws ClassNotFoundException {
    return ClassLoaderSupport.loadClass(name, resolve);
  }

  public static String getBootClasspath() {
    return SystemProperties.getProperty("java.boot.class.path", ".");
  }

  static URL getResource(String name) {
    return ClassLoaderSupport.getResource(getBootClasspath(), name);
  }

  static Enumeration<URL> getResources(String name) {
    return ClassLoaderSupport.getResources(getBootClasspath(), name);
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
    return ClassLoaderSupport.getPackage(name);
  }

  static Package[] getPackages() {
    return ClassLoaderSupport.getPackages();
  }

  static Class<?> getPrimitiveClass(char type) {
    return ClassLoaderSupport.getPrimitiveClass(type);
  }

  static boolean defaultAssertionStatus() {
    return ClassLoaderSupport.defaultAssertionStatus();
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
    return ClassLoaderSupport.getSystemClassLoader();
  }

  static Class<?>[] getAllLoadedClasses() {
    return ClassLoaderSupport.getAllLoadedClasses();
  }

  static Class<?>[] getInitiatedClasses(ClassLoader classLoader) {
    return ClassLoaderSupport.getInitiatedClasses(classLoader);
  }

  static Class<?> findLoadedClass(ClassLoader cl, String name) {
    return ClassLoaderSupport.findLoadedClass(cl, name);
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
