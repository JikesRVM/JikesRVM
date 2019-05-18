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
package org.jikesrvm.classlibrary;

import java.io.UTFDataFormatException;
import java.lang.reflect.Field;
import java.lang.reflect.Member;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;

public class OpenJDKConstantPool extends sun.reflect.ConstantPool {

  private RVMClass type;

  public OpenJDKConstantPool(RVMClass type) {
    this.type = type;
  }

  static {
    sun.reflect.Reflection.registerFieldsToFilter(OpenJDKConstantPool.class, new String[] { "type" });
  }

  @Override
  public int getSize() {
    return type.getConstantPoolSize();
  }

  @Override
  public Class getClassAt(int index) {
    TypeReference typeRef = type.getTypeRef(index);
    if (typeRef.isResolved()) {
      return typeRef.peekType().getClassForType();
    } else {
      RVMType resolvedType = typeRef.resolve();
      return resolvedType.getClassForType();
    }
  }

  @Override
  public Class getClassAtIfLoaded(int index) {
    TypeReference typeRef = type.getTypeRef(index);
    if (typeRef.isResolved()) {
      return typeRef.peekType().getClassForType();
    } else {
      return null;
    }
  }

  @Override
  public Member getMethodAt(int index) {
    MethodReference methodRef = type.getMethodRef(index);
    if (methodRef.isResolved()) {
      return java.lang.reflect.JikesRVMSupport.createMethod(methodRef.peekResolvedMethod());
    } else {
      RVMMethod resolvedMethod = methodRef.resolve();
      return java.lang.reflect.JikesRVMSupport.createMethod(resolvedMethod);
    }
  }

  @Override
  public Member getMethodAtIfLoaded(int index) {
    MethodReference methodRef = type.getMethodRef(index);
    if (methodRef.isResolved()) {
      return java.lang.reflect.JikesRVMSupport.createMethod(methodRef.peekResolvedMethod());
    } else {
      return null;
    }
  }

  @Override
  public Field getFieldAt(int index) {
    FieldReference fieldRef = type.getFieldRef(index);
    if (fieldRef.isResolved()) {
      return java.lang.reflect.JikesRVMSupport.createField(fieldRef.peekResolvedField());
    } else {
      RVMField resolvedField = fieldRef.resolve();
      return java.lang.reflect.JikesRVMSupport.createField(resolvedField);
    }
  }

  @Override
  public Field getFieldAtIfLoaded(int index) {
    FieldReference fieldRef = type.getFieldRef(index);
    if (fieldRef.isResolved()) {
      return java.lang.reflect.JikesRVMSupport.createField(fieldRef.peekResolvedField());
    } else {
      return null;
    }
  }

  @Override
  public String[] getMemberRefInfoAt(int index) {
    MemberReference memberRef = type.getMemberRef(index);
    String[] info = new String[3];
    RVMMember resolvedMember = memberRef.resolveMember();
    RVMClass declaringClass = resolvedMember.getDeclaringClass().asClass();
    String className = declaringClass.getClassForType().getName();
    Atom memberNameAtom = resolvedMember.getName();
    String memberName = null;
    try {
      memberName = memberNameAtom.toUnicodeString();
    } catch (UTFDataFormatException e) {
      throw new Error(e);
    }
    if (VM.VerifyAssertions) VM._assert(memberName != null);
    Atom memberDescriptorAtom = resolvedMember.getDescriptor();
    String descriptor = null;
    try {
      descriptor = memberDescriptorAtom.toUnicodeString();
    } catch (UTFDataFormatException e) {
      throw new Error(e);
    }
    if (VM.VerifyAssertions) VM._assert(descriptor != null);
    info[0] = className;
    info[1] = memberName;
    info[2] = descriptor;
    return info;
  }

  @Override
  public int getIntAt(int index) {
    return type.getIntLiteral(index);
  }

  @Override
  public long getLongAt(int index) {
    return type.getLongLiteral(index);
  }

  @Override
  public float getFloatAt(int index) {
    return type.getFloatLiteral(index);
  }

  @Override
  public double getDoubleAt(int index) {
    return type.getDoubleLiteral(index);
  }

  @Override
  public String getStringAt(int index) {
    return type.getStringLiteral(index);
  }

  @Override
  public String getUTF8At(int index) {
    Atom utf = type.getUtf(index);
    String utfString = null;
    try {
      utfString = utf.toUnicodeString();
    } catch (UTFDataFormatException e) {
      throw new Error(e);
    }
    if (VM.VerifyAssertions) VM._assert(utfString != null);
    return utfString;
  }

}
