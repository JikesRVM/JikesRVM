/*
 * (C) Copyright IBM Corp. 2001
 */
import java.io.*;

/** 
 * VM_SystemClassLoader.java
 *
 * Implements an object that functions as a system class loader.
 * This class is a Singleton pattern.
 */
public final class VM_SystemClassLoader extends java.lang.ClassLoader {

  /* Interface */
  public static VM_SystemClassLoader getVMClassLoader() { 
    return classLoader;
  }

  /* Implementation */
  private static VM_SystemClassLoader classLoader = new VM_SystemClassLoader();

  // prevent other classes from constructing
  private VM_SystemClassLoader() {}

  public InputStream getResourceAsStream (String resName) {
    try {
      VM_BinaryData vd = VM_ClassLoader.getClassOrResourceData( resName );
      if ( vd == null )
        return null;
      else
        return vd.asInputStream();
    } catch ( FileNotFoundException e ) {
      return null;
    }
    catch ( IOException e ) {
      return null;
    }
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

  public String toString() { return "System ClassLoader!"; }

}
