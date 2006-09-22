/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;

/**
 * VM_MethodNameFunction.java
 *
 * @author Stephen Fink
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
