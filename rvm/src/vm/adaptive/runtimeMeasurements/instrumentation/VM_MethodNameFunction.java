/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

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
      return VM_CompiledMethods.getCompiledMethod(key).getMethod().toString();
   }
}
