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
import org.jikesrvm.classlibrary.JavaLangSupport;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.System")
public class java_lang_System {

  static {
    VM.sysWriteln("Static init called");
  }

  @ReplaceMember
  public static void registerNatives() {
    VM.sysWriteln("registerNativesCalled");
    // no natives
  }

  @ReplaceMember
  public static void arraycopy(Object src,  int  srcPos, Object dest, int destPos, int length) {
    JavaLangSupport.arraycopy(src, srcPos, dest, destPos, length);
  }

  @ReplaceMember
  public static int identityHashCode(Object obj) {
    return JavaLangSupport.identityHashCode(obj);
  }

}
