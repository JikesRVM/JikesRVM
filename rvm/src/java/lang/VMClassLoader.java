/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002,2005,2006
 */
// $Id$

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

import com.ibm.jikesrvm.classloader.VM_BootstrapClassLoader;
import com.ibm.jikesrvm.classloader.VM_ClassLoader;
import com.ibm.jikesrvm.classloader.VM_Type;

import com.ibm.jikesrvm.util.VM_HashMap;

/**
 * Jikes RVM impl of VMClassLoader.
 * See GNU classpath reference impl for javadoc.
 *
 * @author Julian Dolby
 * @author Ian Rogers
 */
final class VMClassLoader
{
  /**
   * A map of maps. The first map is indexed by the classloader. The
   * map this finds then maps String class names to classes
   */
  private static final VM_HashMap<ClassLoader,VM_HashMap<String,Class<?>>> loadedClasses = 
    new VM_HashMap<ClassLoader,VM_HashMap<String,Class<?>>>();

  /** packages loaded by the bootstrap class loader */
  private static final VM_HashMap<String,Package> definedPackages = 
    new VM_HashMap<String,Package>();

  private static final VM_HashMap<String,ZipFile> bootjars = 
    new VM_HashMap<String,ZipFile>();

  static
  {
    String[] packages = getBootPackages();

    if( packages != null)
    {
      String specName = 
        SystemProperties.getProperty("java.specification.name");
      String vendor =
        SystemProperties.getProperty("java.specification.vendor");
      String version =
        SystemProperties.getProperty("java.specification.version");

      Package p;

      for(int i = 0; i < packages.length; i++)
      {
        p = new Package(packages[i],
            specName,
            vendor,
            version,
            "GNU Classpath",
            "GNU",
            Configuration.CLASSPATH_VERSION,
            null,
            null);

        definedPackages.put(packages[i], p);
      }
    }
  }

  static final Class<?> defineClass(ClassLoader cl, String name,
      byte[] data, int offset, int len,
      ProtectionDomain pd)
      throws ClassFormatError
      {
    VM_Type vmType = VM_ClassLoader.defineClassInternal(name, data, offset, len, cl);
    Class<?> ans = vmType.getClassForType();
    JikesRVMSupport.setClassProtectionDomain(ans, pd);
    VM_HashMap<String,Class<?>> mapForCL = loadedClasses.get(cl);
    if (mapForCL == null) {
      mapForCL = new VM_HashMap<String,Class<?>>();
      loadedClasses.put(cl, mapForCL);
    }
    mapForCL.put(name, ans);
    return ans;
      }

  static final void resolveClass(Class<?> c) {
    VM_Type cls = JikesRVMSupport.getTypeForClass(c);
    cls.resolve();
    cls.instantiate();
    cls.initialize();
  }

  static final Class<?> loadClass(String name, boolean resolve)
  throws ClassNotFoundException
  {
    return VM_BootstrapClassLoader.getBootstrapClassLoader().loadClass(name, resolve);
  }

  static URL getResource(String name)
  {
    Enumeration<URL> e = getResources(name);
    if (e.hasMoreElements())
      return (URL)e.nextElement();
    return null;
  }

  static Enumeration<URL> getResources(String name)
  {
    StringTokenizer st = new StringTokenizer(
        SystemProperties.getProperty("java.boot.class.path", "."),
        File.pathSeparator);
    Vector<URL> v = new Vector<URL>();
    while (st.hasMoreTokens())
    {
      File file = new File(st.nextToken());
      if (file.isDirectory())
      {
        try
        {
          File f = new File(file, name);
          if (!f.exists()) continue;
          v.add(new URL("file://" + f.getAbsolutePath()));
        }
        catch (MalformedURLException e)
        {
          throw new Error(e);
        }
      }
      else if (file.isFile())
      {
        ZipFile zip;
        synchronized(bootjars)
        {
          zip = (ZipFile) bootjars.get(file.getName());
        }
        if(zip == null)
        {
          try
          {
            zip = new ZipFile(file);
            synchronized(bootjars)
            {
              bootjars.put(file.getName(), zip);
            }
          }
          catch (IOException e)
          {
            continue;
          }
        }
        String zname = name.startsWith("/") ? name.substring(1) : name;
        if (zip.getEntry(zname) == null)
          continue;
        try
        {
          v.add(new URL("jar:file://"
              + file.getAbsolutePath() + "!/" + zname));
        }
        catch (MalformedURLException e)
        {
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
        return (String[]) packageSet.toArray(new String[packageSet.size()]);
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
      packages[idx++] = (Package)it.next();
    }
    return packages;
  }

  static final Class<?> getPrimitiveClass(char type) {
    VM_Type t;
    switch (type) {
    case 'Z': 
      t = VM_Type.BooleanType;
      break;
    case 'B':
      t = VM_Type.ByteType;
      break;
    case 'C':
      t = VM_Type.CharType;
      break;
    case 'D':
      t = VM_Type.DoubleType;
      break;
    case 'F':
      t = VM_Type.FloatType;
      break;
    case 'I':
      t = VM_Type.IntType;
      break;
    case 'J':
      t = VM_Type.LongType;
      break;
    case 'S':
      t = VM_Type.ShortType;
      break;
    case 'V':
      t = VM_Type.VoidType;
      break;
    default:
      throw new NoClassDefFoundError("Invalid type specifier: " + type);
    }
    return t.getClassForType();
  }

  static final boolean defaultAssertionStatus() {
    return true;
  }

  @SuppressWarnings({"unchecked","unused"}) // TODO should this method be deleted ?
  private static final Map packageAssertionStatus() {
    return new HashMap();
  }

  @SuppressWarnings({"unchecked","unused"}) // TODO should this method be deleted ?
  private static final Map classAssertionStatus() {
    return new HashMap();
  }

  static ClassLoader getSystemClassLoader() {
    return VM_ClassLoader.getApplicationClassLoader();
  }

  static Class<?>[] getAllLoadedClasses() {
    Vector<Class<?>> classList = new Vector<Class<?>>();
    for (VM_HashMap<String,Class<?>> classes : loadedClasses.values()) {
      for (Class<?> cl : classes.values()) {
        classList.add(cl);
      }
    }
    Class<?>[] result = new Class[classList.size()];
    return (Class[])classList.toArray(result);
  }

  static Class<?>[] getInitiatedClasses(ClassLoader classLoader) {
    VM_HashMap<String,Class<?>> mapForCL = loadedClasses.get(classLoader);
    if (mapForCL == null) return new Class[]{};
    Vector<Class<?>> classList = new Vector<Class<?>>();
    for (Class<?> cl : mapForCL.values())
      classList.add(cl);
    Class<?>[] result = new Class[classList.size()];
    return (Class[])classList.toArray(result);
  }

  static Class<?> findLoadedClass(ClassLoader cl, String name) {
    VM_HashMap<String,Class<?>> mapForCL = loadedClasses.get(cl);
    if (mapForCL == null) return null;
    return (Class<?>)mapForCL.get(name);
  }

  private static Instrumentation instrumenter = null;

  static void setInstrumenter(Instrumentation theInstrumenter) {
    instrumenter = theInstrumenter;
  }

  static final Class<?> defineClassWithTransformers(ClassLoader loader, String name, byte[] data,
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
