/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

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
