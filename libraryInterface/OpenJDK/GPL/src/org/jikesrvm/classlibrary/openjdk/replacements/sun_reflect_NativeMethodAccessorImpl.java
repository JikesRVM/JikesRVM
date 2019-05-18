/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jikesrvm.VM;
import org.jikesrvm.classlibrary.ClassLibraryHelpers;
import org.jikesrvm.classlibrary.JavaLangReflectSupport;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.ReflectionBase;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "sun.reflect.NativeMethodAccessorImpl")
public class sun_reflect_NativeMethodAccessorImpl {

  @ReplaceMember
  private static Object invoke0(Method m, Object obj, Object[] args) throws InvocationTargetException, ExceptionInInitializerError, IllegalAccessException {
    RVMClass type = JikesRVMSupport.getTypeForClass(m.getDeclaringClass()).asClass();
    if (VM.VerifyAssertions) VM._assert(type.isClassType());

    // Run the constructor on the it.
    RVMMethod rvmMethod = java.lang.reflect.JikesRVMSupport.getMethodOf(m);
    if (rvmMethod == null) {
      Class[] parameterTypes = m.getParameterTypes();
      StringBuilder descriptorBuilder = new StringBuilder();
      descriptorBuilder.append("(");
      for (Class parameterType : parameterTypes) {
        descriptorBuilder.append(java.lang.JikesRVMSupport.getTypeForClass(parameterType).getDescriptor().toString());
      }
      descriptorBuilder.append(")V");
      String descriptorAsString = descriptorBuilder.toString();
      Atom methodName = Atom.findOrCreateUnicodeAtom(m.getName());
      Atom methodDescriptor = Atom.findOrCreateUnicodeAtom(descriptorAsString);
      RVMMethod realMethod = type.findDeclaredMethod(methodName, methodDescriptor);
      if (VM.VerifyAssertions) VM._assert(realMethod != null);
      Magic.setObjectAtOffset(m, ClassLibraryHelpers.javaLangReflectMethod_rvmMethodField.getOffset(), realMethod);
      rvmMethod = java.lang.reflect.JikesRVMSupport.getMethodOf(m);
    }

    if (VM.VerifyAssertions) VM._assert(rvmMethod != null);

    RVMClass callerClass = RVMClass.getClassFromStackFrame(4);
    ReflectionBase invoker = (ReflectionBase) Magic.getObjectAtOffset(m, ClassLibraryHelpers.javaLangReflectMethod_invokerField.getOffset());
    return JavaLangReflectSupport.invoke(obj, args, rvmMethod, m, callerClass, invoker);
  }

}
