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

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.Class")
public class java_lang_Class {

  @ReplaceMember
  static Class<?> getPrimitiveClass(String className) {
    TypeReference typeRef = TypeReference.mapPrimitiveClassNameToTypeReference(className);
    if (typeRef != null) {
      return typeRef.resolve().getClassForType();
    }
    VM.sysFail("Unknown primitive type name: " + className);
    return null;
  }

}
