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
package org.jikesrvm.classlibrary.openjdk.replacements;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.TypeReference;
import org.vmmagic.pragma.ReplaceClass;

@ReplaceClass(className = "java.lang.reflect.ReflectAccess")
public class java_lang_reflect_ReflectAccess {

  public Field newField(Class declaringClass,
      String name,
      Class type,
      int modifiers,
      int slot,
      String signature,
      byte[] annotations) {
    RVMClass declClass = JikesRVMSupport.getTypeForClass(declaringClass).asClass();
    Atom nameAtom = Atom.findUnicodeAtom(name);
    if (VM.VerifyAssertions) VM._assert(nameAtom != null);
    Atom signatureAtom = Atom.findUnicodeAtom(signature);
    if (VM.VerifyAssertions) VM._assert(signatureAtom != null);
    TypeReference fieldType = JikesRVMSupport.getTypeForClass(type).getTypeRef();

    RVMField[] staticFields = declClass.getStaticFields();
    for (RVMField f : staticFields) {
      if (f.getName() == nameAtom && f.getSignature() == signatureAtom && f.getType() == fieldType) {
        return java.lang.reflect.JikesRVMSupport.createField(f);
      }
    }

    RVMField[] instanceFields = declClass.getInstanceFields();for (RVMField f : instanceFields) {
      if (f.getName() == nameAtom && f.getSignature() == signatureAtom && f.getType() == fieldType) {
        return java.lang.reflect.JikesRVMSupport.createField(f);
      }
    }
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    return null;
  }


  public Method newMethod(Class declaringClass,
      String name,
      Class[] parameterTypes,
      Class returnType,
      Class[] checkedExceptions,
      int modifiers,
      int slot,
      String signature,
      byte[] annotations,
      byte[] parameterAnnotations,
      byte[] annotationDefault) {
    RVMClass declClass = JikesRVMSupport.getTypeForClass(declaringClass).asClass();
    Atom nameAtom = Atom.findUnicodeAtom(name);
    if (VM.VerifyAssertions) VM._assert(nameAtom != null);
    Atom signatureAtom = Atom.findUnicodeAtom(signature);
    if (VM.VerifyAssertions) VM._assert(signatureAtom != null);
    VM.sysFail("NYI");
    return null;
    }


  public <T> Constructor<T> newConstructor(Class<T> declaringClass,
      Class[] parameterTypes,
      Class[] checkedExceptions,
      int modifiers,
      int slot,
      String signature,
      byte[] annotations,
      byte[] parameterAnnotations) {
    VM.sysFail("NYI");
    return null;
  }

}
