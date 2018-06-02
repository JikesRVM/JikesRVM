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
    TypeReference typeRef = null;
    // TODO extract mapping of primitive type name to typeReference to extra
    // method in TypeReference. This can't be done on the OpenJDK branch
    // because a unit test for that would require executing on a built image
    // which doesn't yet work for OpenJDK.
    if (className.equals("int")) {
      typeRef = TypeReference.Int;
    } else if (className.equals("boolean")) {
      typeRef = TypeReference.Boolean;
    } else if (className.equals("byte")) {
      typeRef = TypeReference.Byte;
    } else if (className.equals("char")) {
      typeRef = TypeReference.Char;
    } else if (className.equals("double")) {
      typeRef = TypeReference.Double;
    } else if (className.equals("float")) {
      typeRef = TypeReference.Float;
    } else if (className.equals("long")) {
      typeRef = TypeReference.Long;
    } else if (className.equals("short")) {
      typeRef = TypeReference.Short;
    } else if (className.equals("void")) {
      typeRef = TypeReference.Void;
    }
    if (typeRef != null) {
      return typeRef.resolve().getClassForType();
    }
    VM.sysFail("Unknown primitive type name: " + className);
    return null;
  }

}
