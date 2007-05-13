/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM 2007
 */
package java.lang.reflect;

import org.jikesrvm.classloader.VM_Atom;

import gnu.java.lang.ClassHelper;
import gnu.java.lang.reflect.MethodSignatureParser;
import gnu.java.lang.reflect.FieldSignatureParser;

/**
 * Class library dependent helper methods used to implement
 * class library independent code in java.lang.reflect
 *
 */
class JikesRVMHelpers {

  static String getUserName(Class<?> c) {
    return ClassHelper.getUserName(c);
  }

  static TypeVariable<?>[] getTypeParameters(Constructor constructor, VM_Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(constructor, sig.toString());
    return p.getTypeParameters();
  }
  
  static Type[] getGenericExceptionTypes(Constructor constructor, VM_Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(constructor, sig.toString());
    return p.getGenericExceptionTypes();
  }

  static Type[] getGenericParameterTypes(Constructor constructor, VM_Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(constructor, sig.toString());
    return p.getGenericParameterTypes();
  }

  static TypeVariable<?>[] getTypeParameters(Method method, VM_Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(method, sig.toString());
    return p.getTypeParameters();
  }

  static Type[] getGenericExceptionTypes(Method method, VM_Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(method, sig.toString());
    return p.getGenericExceptionTypes();
  }

  static Type[] getGenericParameterTypes(Method method, VM_Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(method, sig.toString());
    return p.getGenericParameterTypes();
  }

  static Type getGenericReturnType(Method method, VM_Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(method, sig.toString());
    return p.getGenericReturnType();
  }

  static Type getFieldType(Field field, VM_Atom sig) {
    FieldSignatureParser p = new FieldSignatureParser(field.getDeclaringClass(), sig.toString());
    return p.getFieldType();
  }

}


