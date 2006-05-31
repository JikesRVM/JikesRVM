/*
 * (C) Copyright IBM Corp 2002,2005,2006
 */
// $Id$

package java.lang;

import gnu.classpath.Configuration;
import gnu.classpath.SystemProperties;
//-#if !RVM_WITH_CLASSPATH_0_90
import gnu.java.lang.InstrumentationImpl;
//-#endif

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
//-#if !RVM_WITH_CLASSPATH_0_90
import java.lang.instrument.Instrumentation;
//-#endif
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

import com.ibm.JikesRVM.classloader.VM_BootstrapClassLoader;
import com.ibm.JikesRVM.classloader.VM_ClassLoader;
import com.ibm.JikesRVM.classloader.VM_Type;

import com.ibm.JikesRVM.util.VM_HashMap;

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
  private static final VM_HashMap loadedClasses = new VM_HashMap();

  /** packages loaded by the bootstrap class loader */
  private static final VM_HashMap definedPackages = new VM_HashMap();

  //-#if !RVM_WITH_CLASSPATH_0_90
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
  //-#endif
  
  static final Class defineClass(ClassLoader cl, String name,
                                 byte[] data, int offset, int len,
                                 ProtectionDomain pd)
    throws ClassFormatError
  {
    VM_Type vmType = VM_ClassLoader.defineClassInternal(name, data, offset, len, cl);
    Class ans = vmType.createClassForType(pd);
    VM_HashMap mapForCL = (VM_HashMap)loadedClasses.get(cl);
    if (mapForCL == null) {
      mapForCL = new VM_HashMap();
      loadedClasses.put(cl, mapForCL);
    }
    mapForCL.put(name, ans);
    return ans;
  }

  static final void resolveClass(Class c) {
    VM_Type cls = JikesRVMSupport.getTypeForClass(c);
    cls.resolve();
    cls.instantiate();
    cls.initialize();
  }

  static final Class loadClass(String name, boolean resolve)
    throws ClassNotFoundException
  {
    return VM_BootstrapClassLoader.getBootstrapClassLoader().loadClass(name, resolve);
  }

  static URL getResource(String name)
  {
    Enumeration e = getResources(name);
    if (e.hasMoreElements())
      return (URL)e.nextElement();
    return null;
  }

  private static final VM_HashMap bootjars = new VM_HashMap();
  
  static Enumeration getResources(String name)
  {
    StringTokenizer st = new StringTokenizer(
      SystemProperties.getProperty("java.boot.class.path", "."),
      File.pathSeparator);
    Vector v = new Vector();
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
        Set packageSet = new HashSet();
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
    return (Package)definedPackages.get(name);
  }


  
  static Package[] getPackages() {
    Package[] packages = new Package[definedPackages.size()];
    Iterator it = definedPackages.valueIterator();
    int idx = 0;
    while (it.hasNext()) {
      packages[idx++] = (Package)it.next();
    }
    return packages;
  }

  static final Class getPrimitiveClass(char type) {
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

  static final Map packageAssertionStatus() {
    return new HashMap();
  }

  static final Map classAssertionStatus() {
    return new HashMap();
  }

  static ClassLoader getSystemClassLoader() {
    return VM_ClassLoader.getApplicationClassLoader();
  }

  static Class findLoadedClass(ClassLoader cl, String name) {
    VM_HashMap mapForCL = (VM_HashMap)loadedClasses.get(cl);
    if (mapForCL == null) return null;
    return (Class)mapForCL.get(name);
  }

  //-#if !RVM_WITH_CLASSPATH_0_90
  static final Instrumentation instrumenter = null;
  static final Class defineClassWithTransformers(ClassLoader loader, String name, byte[] data,
                                                 int offset, int len, ProtectionDomain pd) {
    
    if (instrumenter != null) {
      byte[] modifiedData = new byte[len];
      System.arraycopy(data, offset, modifiedData, 0, len);
      modifiedData =
        ((InstrumentationImpl)instrumenter).callTransformers(loader, name, null, pd, modifiedData);
        
      return defineClass(loader, name, modifiedData, 0, modifiedData.length, pd);
    } else {
      return defineClass(loader, name, data, offset, len, pd);
    }
  }
  //-#endif
}
