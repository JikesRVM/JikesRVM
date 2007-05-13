/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.adaptive.measurements.instrumentation;

import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;

/**
 * VM_MethodNameFunction.java
 *
 *
 * This class takes a compiled method id and returns a string
 * representation of the method name.
 *
 **/

class VM_MethodNameFunction implements VM_CounterNameFunction {

   /**
    * @param key the compiled method id of a method
    */
   public String getName(int key) {
     VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(key);
     if (cm == null) {
       return "OBSOLETE";
     } else {
       return cm.getMethod().toString();
     }
   }
}
