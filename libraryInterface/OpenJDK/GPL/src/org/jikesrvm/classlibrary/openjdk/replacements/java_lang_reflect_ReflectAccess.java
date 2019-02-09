/*
 * Copyright (c) 2001, 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
