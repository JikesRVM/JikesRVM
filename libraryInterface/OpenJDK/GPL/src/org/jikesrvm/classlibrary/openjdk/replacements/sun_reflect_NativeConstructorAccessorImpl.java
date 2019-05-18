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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.jikesrvm.VM;
import org.jikesrvm.classlibrary.ClassLibraryHelpers;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "sun.reflect.NativeConstructorAccessorImpl")
public class sun_reflect_NativeConstructorAccessorImpl {

  // FIXME review whether this is a good idea or needs to be implemented in another way
  @ReplaceMember
  private static Object newInstance0(Constructor c, Object[] args)
      throws InstantiationException, IllegalArgumentException,
      InvocationTargetException {

    RVMClass type = JikesRVMSupport.getTypeForClass(c.getDeclaringClass()).asClass();
    if (VM.VerifyAssertions) VM._assert(type.isClassType());

    // Allocate an uninitialized instance;
    Object obj = RuntimeEntrypoints.resolvedNewScalar(type.asClass());

    // Run the constructor on the it.
    RVMMethod constructorMethod = java.lang.reflect.JikesRVMSupport.getMethodOf(c);
    if (constructorMethod == null) {
      Class[] parameterTypes = c.getParameterTypes();
      StringBuilder descriptorBuilder = new StringBuilder();
      descriptorBuilder.append("(");
      for (Class parameterType : parameterTypes) {
        descriptorBuilder.append(java.lang.JikesRVMSupport.getTypeForClass(parameterType).getDescriptor().toString());
      }
      descriptorBuilder.append(")V");
      String descriptorAsString = descriptorBuilder.toString();
      Atom constructorDescriptor = Atom.findOrCreateUnicodeAtom(descriptorAsString);
      RVMMethod initMethod = type.findInitializerMethod(constructorDescriptor);
      if (VM.VerifyAssertions) VM._assert(initMethod != null);
      Magic.setObjectAtOffset(c, ClassLibraryHelpers.javaLangReflectConstructor_rvmMethodField.getOffset(), initMethod);
      constructorMethod = java.lang.reflect.JikesRVMSupport.getMethodOf(c);
    }

    if (VM.VerifyAssertions) VM._assert(constructorMethod != null);
    Reflection.invoke(constructorMethod, null, obj, args, true);

    return obj;
  }

}
