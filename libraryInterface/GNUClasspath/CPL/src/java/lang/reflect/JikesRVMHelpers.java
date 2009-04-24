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
package java.lang.reflect;

import org.jikesrvm.classloader.Atom;

import gnu.java.lang.ClassHelper;
import gnu.java.lang.reflect.MethodSignatureParser;
import gnu.java.lang.reflect.FieldSignatureParser;

/**
 * Class library dependent helper methods used to implement
 * class library independent code in java.lang.reflect
 */
class JikesRVMHelpers {

  static String getUserName(Class<?> c) {
    return ClassHelper.getUserName(c);
  }

  static <T> TypeVariable<Constructor<T>>[] getTypeParameters(Constructor<T> constructor, Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(constructor, sig.toString());
    return p.getTypeParameters();
  }

  static Type[] getGenericExceptionTypes(Constructor<?> constructor, Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(constructor, sig.toString());
    return p.getGenericExceptionTypes();
  }

  static Type[] getGenericParameterTypes(Constructor<?> constructor, Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(constructor, sig.toString());
    return p.getGenericParameterTypes();
  }

  static TypeVariable<Method>[] getTypeParameters(Method method, Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(method, sig.toString());
    return p.getTypeParameters();
  }

  static Type[] getGenericExceptionTypes(Method method, Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(method, sig.toString());
    return p.getGenericExceptionTypes();
  }

  static Type[] getGenericParameterTypes(Method method, Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(method, sig.toString());
    return p.getGenericParameterTypes();
  }

  static Type getGenericReturnType(Method method, Atom sig) {
    MethodSignatureParser p = new MethodSignatureParser(method, sig.toString());
    return p.getGenericReturnType();
  }

  static Type getFieldType(Field field, Atom sig) {
    FieldSignatureParser p = new FieldSignatureParser(field.getDeclaringClass(), sig.toString());
    return p.getFieldType();
  }

}


