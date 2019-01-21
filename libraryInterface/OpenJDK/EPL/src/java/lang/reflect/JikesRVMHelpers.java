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
package java.lang.reflect;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;

public class JikesRVMHelpers {

  public static RVMField findFieldByName(RVMClass typeForClass, String fieldNameStr) {
    Atom fieldName = Atom.findUnicodeAtom(fieldNameStr);
    if (VM.VerifyAssertions) VM._assert(fieldName != null);
    return typeForClass.findDeclaredField(fieldName);
  }

}
