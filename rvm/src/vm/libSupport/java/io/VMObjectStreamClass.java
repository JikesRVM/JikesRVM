/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.io;

import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Type;

/**
 * java.io.ObjectStream helper implemented for Jikes RVM.
 *
 * @author Dave Grove
 */
final class VMObjectStreamClass {

  static boolean hasClassInitializer (Class cls) {
    VM_Type t = java.lang.JikesRVMSupport.getTypeForClass(cls);
    if (t.isClassType()) {
      return t.asClass().getClassInitializerMethod() != null;
    } else {
      return false;
    }
  }
}
