/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
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
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;
import org.vmmagic.unboxed.Offset;

@ReplaceClass(className = "java.lang.reflect.Constructor")
public class java_lang_reflect_Constructor {

  @ReplaceMember
  Constructor copy() {
    // "copy" constructor
    Constructor source = (Constructor) (Object) this;
    RVMMethod rvmMethod = java.lang.reflect.JikesRVMSupport.getMethodOf(source);
    Constructor newConstructor = java.lang.reflect.JikesRVMSupport.createConstructor(rvmMethod);
    // set rest of fields from old field
    RVMClass typeForClass = java.lang.JikesRVMSupport.getTypeForClass(Constructor.class).asClass();

    // set root field
    RVMField rootField = java.lang.reflect.JikesRVMHelpers.findFieldByName(typeForClass, "root");
    Offset rootOffset = rootField.getOffset();
    Magic.setObjectAtOffset(newConstructor, rootOffset, source);

    // copy constructorAccessor from this
    RVMField constructorAccessorField = java.lang.reflect.JikesRVMHelpers.findFieldByName(typeForClass, "constructorAccessor");
    Offset constructorAccessorOffset = constructorAccessorField.getOffset();
    Object sourceConstructorAccessor = Magic.getObjectAtOffset(source, constructorAccessorOffset);
    Magic.setObjectAtOffset(newConstructor, constructorAccessorOffset, sourceConstructorAccessor);

    return newConstructor;
  }

}
