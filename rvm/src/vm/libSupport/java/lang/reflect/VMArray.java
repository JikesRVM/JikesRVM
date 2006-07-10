/*
 * (C) Copyright The University of Manchester 2006
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.classloader.VM_TypeReference;
import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_BootstrapClassLoader;
import com.ibm.JikesRVM.VM_Runtime;

/**
 * VM dependent Array operations
 *
 * @author Chris Kirkham
 * @author Ian Rogers
 */
class VMArray {
  /**
   * Dynamically create an array of objects.
   *
   * @param type guaranteed to be a valid object type
   * @param dim the length of the array
   * @return the new array
   * @throws NegativeArraySizeException if dim is negative
   * @throws OutOfMemoryError if memory allocation fails
   */
  static Object createObjectArray(Class type, int dim)
    throws OutOfMemoryError, NegativeArraySizeException {
    ClassLoader loader = type.getClassLoader();
    VM_Atom name = VM_Atom.findOrCreateAsciiAtom("[L"+type.getName()+";");
    if (loader == null) { // Use System loader
      loader = VM_BootstrapClassLoader.getBootstrapClassLoader();
    }
    int id = VM_TypeReference.findOrCreate(loader, name).getId();
    return VM_Runtime.unresolvedNewArray(dim, id);
  }
}
