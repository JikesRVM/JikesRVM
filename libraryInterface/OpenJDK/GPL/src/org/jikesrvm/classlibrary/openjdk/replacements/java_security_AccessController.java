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

import java.security.AccessControlContext;

import org.jikesrvm.VM;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.security.AccessController")
public class java_security_AccessController {

  @ReplaceMember
  private static AccessControlContext getStackAccessControlContext() {
    VM.sysFail("NYI: getStackAccessControlContext");
    return null;
  }

}
