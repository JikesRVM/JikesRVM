/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM 2007
 */
package java.lang;

import org.jikesrvm.classloader.VM_Atom;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import gnu.java.lang.reflect.ClassSignatureParser;

/**
 * Class library dependent helper methods used to implement
 * class library independent code in java.lang.
 *
 */
class JikesRVMHelpers {

  
  static Type[] getInterfaceTypesFromSignature(Class<?> clazz, VM_Atom sig) {
    ClassSignatureParser p = new ClassSignatureParser(clazz, sig.toString());
    return p.getInterfaceTypes();
  }

  static Type getSuperclassType(Class<?> clazz, VM_Atom sig) {
    ClassSignatureParser p = new ClassSignatureParser(clazz, sig.toString());
    return p.getSuperclassType();
  }

  static TypeVariable<?>[] getTypeParameters(Class<?> clazz, VM_Atom sig) {
    ClassSignatureParser p = new ClassSignatureParser(clazz, sig.toString());
    return p.getTypeParameters();
  }


}
