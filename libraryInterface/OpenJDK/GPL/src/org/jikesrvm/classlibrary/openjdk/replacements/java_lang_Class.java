/*
 * Copyright (c) 1994, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;

import org.jikesrvm.VM;
import org.jikesrvm.classlibrary.ClassLibraryHelpers;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

import sun.reflect.ConstantPool;

@ReplaceClass(className = "java.lang.Class")
public class java_lang_Class<T> {

  // TODO compare with GNU Classpath implementation and extract shared code when implementation is done

  @ReplaceMember
  private static void registerNatives() {
    // nothing to do
  }

  @ReplaceMember
  private static Class<?> forName0(String name, boolean initialize, ClassLoader loader, Class<?> caller)
      throws ClassNotFoundException {
    VM.sysFail("forName0");
    return null;
  }

  @ReplaceMember
  public boolean isInstance(Object obj) {
    VM.sysFail("isInstance: " + obj);
    return false;
  }

  @ReplaceMember
  public boolean isAssignableFrom(Class<?> cls) {
    VM.sysFail("isAssignableFrom: " + cls);
    return false;
  }

  @ReplaceMember
  public boolean isInterface() {
     RVMType myType = JikesRVMSupport.getTypeForClass((Class<?>) (Object) this);
    return myType.isClassType() && myType.asClass().isInterface();
  }

  @ReplaceMember
  public boolean isArray() {
    return JikesRVMSupport.getTypeForClass((Class<?>) (Object) this).isArrayType();
  }

  @ReplaceMember
  public boolean isPrimitive() {
    return JikesRVMSupport.getTypeForClass((Class<?>) (Object) this).isPrimitiveType();
  }

  @ReplaceMember
  private String getName0() {
    return JikesRVMSupport.getTypeForClass((Class<?>) (Object) this).toString();
  }

  @ReplaceMember
  ClassLoader getClassLoader0() {
    ClassLoader classLoader = JikesRVMSupport.getTypeForClass((Class<?>) (Object) this).getClassLoader();
    if (classLoader == BootstrapClassLoader.getBootstrapClassLoader()) return null;
    return classLoader;
  }

  @ReplaceMember
  public Class<? super T> getSuperclass() {
    VM.sysFail("getSuperclass");
    return null;
  }

  @ReplaceMember
  public Class<?>[] getInterfaces() {
    VM.sysFail("getInterfaces");
    return null;
  }

  @ReplaceMember
  public Class<?> getComponentType() {
    RVMType typeForClass = JikesRVMSupport.getTypeForClass((Class<?>) (Object) this);
    if (typeForClass.isArrayType()) {
      return typeForClass.asArray().getElementType().getClassForType();
    }
    return null;
  }

  @ReplaceMember
  public int getModifiers() {
    VM.sysFail("getModifiers");
    return 0;
  }

  @ReplaceMember
  public Object[] getSigners() {
    VM.sysFail("getSigners");
    return null;
  }

  @ReplaceMember
  void setSigners(Object[] signers) {
    VM.sysFail("setSigners");
  }

  @ReplaceMember
  private Object[] getEnclosingMethod0() {
    VM.sysFail("getEnclosingMethod0");
    return null;
  }

  @ReplaceMember
  private Class<?> getDeclaringClass0() {
    VM.sysFail("getDeclaringClass0");
    return null;
  }

  @ReplaceMember
  private java.security.ProtectionDomain getProtectionDomain0() {
    return (java.security.ProtectionDomain) Magic.getObjectAtOffset(this, ClassLibraryHelpers.protectionDomainField.getOffset());
  }

  @ReplaceMember
  void setProtectionDomain0(java.security.ProtectionDomain pd) {
    JikesRVMSupport.setClassProtectionDomain((Class<?>) (Object) this, pd);
  }

  @ReplaceMember
  static Class<?> getPrimitiveClass(String className) {
    TypeReference typeRef = TypeReference.mapPrimitiveClassNameToTypeReference(className);
    if (typeRef != null) {
      return typeRef.resolve().getClassForType();
    }
    VM.sysFail("Unknown primitive type name: " + className);
    return null;
  }

  @ReplaceMember
  private String getGenericSignature() {
    VM.sysFail("getGenericSignature");
    return null;
  }

  @ReplaceMember
  byte[] getRawAnnotations() {
    VM.sysFail("getRawAnnotations");
    return null;
  }

  @ReplaceMember
  ConstantPool getConstantPool() {
    VM.sysFail("getConstantPool");
    return null;
  }

  @ReplaceMember
  private Field[] getDeclaredFields0(boolean publicOnly) {
    // TODO move out of this class
    RVMClass myType = java.lang.JikesRVMSupport.getTypeForClass((Class<?>) (Object) this).asClass();
    RVMField[] declaredFields = myType.getDeclaredFields();
    List<Field> myFields = new ArrayList<Field>();
    for (RVMField field : declaredFields) {
      if (publicOnly) {
        if (field.isPublic()) {
          Field createdField = java.lang.reflect.JikesRVMSupport.createField(field);
          myFields.add(createdField);
        } else {
          continue;
        }
      } else {
        Field createdField = java.lang.reflect.JikesRVMSupport.createField(field);
        myFields.add(createdField);
      }
    }
    return myFields.toArray(new Field[0]);
  }

  @ReplaceMember
  private Method[] getDeclaredMethods0(boolean publicOnly) {
    VM.sysFail("getDeclaredMethods0");
    return null;
  }

  @ReplaceMember
  private Constructor<T>[] getDeclaredConstructors0(boolean publicOnly) {
    VM.sysFail("getDeclaredConstructors0");
    return null;
  }

  @ReplaceMember
  private Class<?>[] getDeclaredClasses0() {
    VM.sysFail("getDeclaredClasses0");
    return null;
  }

  @ReplaceMember
  private static boolean desiredAssertionStatus0(Class<?> clazz) {
    return JikesRVMSupport.getTypeForClass(clazz).getDesiredAssertionStatus();
  }

}
