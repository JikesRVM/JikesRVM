/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.util.Hashtable;
import java.io.*;

/** 
 * VM_SystemClassLoader.java
 *
 * Implements an object that functions as a system class loader.
 * This class is a Singleton pattern.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */

//-#if RVM_WITH_GNU_CLASSPATH
public final class VM_SystemClassLoader extends gnu.java.lang.SystemClassLoader {

  void parsePath()
  {
  }
//-#else
public final class VM_SystemClassLoader extends com.ibm.oti.vm.AbstractClassLoader {
//-#endif

    /* Interface */
    private static VM_SystemClassLoader vmClassLoader =
      new VM_SystemClassLoader();

    public static VM_SystemClassLoader getVMClassLoader() { 
      return vmClassLoader;
    }

    // prevent other classes from constructing
    private VM_SystemClassLoader() {
      //-#if RVM_WITH_GNU_CLASSPATH
      super(null);
      //-#endif
    }

    public Class loadClass(String className, boolean resolveClass)
      throws ClassNotFoundException
      {
        Class loadedClass = null;

        // Ask the VM to look in its cache.
        loadedClass = findLoadedClassInternal(className);

        if (loadedClass == null) loadedClass = findClass(className);

        // resolve if required
        if (resolveClass) resolveClass(loadedClass);

        return loadedClass;
      }

    protected Class findClass (String className) throws ClassNotFoundException {
      VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
      VM_Class cls = (VM_Class) VM_ClassLoader.findOrCreateType(classDescriptor, this);
      try {	    
        InputStream is =
          getResourceAsStream(classDescriptor.classFileNameFromDescriptor());

        if (is == null) throw new NullPointerException();

        if (!cls.isLoaded()) {
          cls.load(new DataInputStream(is));
        }
      } catch (Throwable e) {
        throw new ClassNotFoundException(className);
      }
      return cls.getClassForType();
    }

    /**
     * Attempts to find and return a class which has already
     * been loaded by the virtual machine. Note that the class
     * may not have been linked and the caller should call
     * resolveClass() on the result if necessary.
     *
     * @return              java.lang.Class
     *                                      the class or null.
     * @param               className String
     *                                      the name of the class to search for.
     */
    public final Class findLoadedClassInternal (String className) {
      // make a descriptorfrom the class name string
      VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();

      // check if the type dictionary has a loaded class
      int typeId = VM_TypeDictionary.findId(classDescriptor); 
      if (typeId == -1) return null;
      VM_Type t = VM_TypeDictionary.getValue(typeId);
      if (t == null) return null;
      if (!t.isLoaded()) return null;

      // found it. return the class
      return t.getClassForType();
    }

    public String toString() { return "VM_System ClassLoader!"; }

  }


