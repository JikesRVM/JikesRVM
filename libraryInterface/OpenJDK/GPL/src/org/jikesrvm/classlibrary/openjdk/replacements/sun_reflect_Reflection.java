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
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "sun.reflect.Reflection")
public class sun_reflect_Reflection {

  @ReplaceMember
  public static Class<?> getCallerClass() {
    VM.sysFail("getCallerClass");
    // TODO implement this. This can probably be done using StackBrowser.
    // TODO Evaluate whether we should copy functionality from our
    // VMStackWalker implementation from GNU Classpath and/or make changes
    // to StackBrowser.
    return null;
  }

}
