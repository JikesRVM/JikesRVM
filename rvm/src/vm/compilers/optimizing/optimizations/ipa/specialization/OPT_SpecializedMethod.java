/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;

/**
 * This is the top-level class to support specialized versions of Java methods
 *
 * @author Rajesh Bordawekar
 * @author Manish Gupta
 * @modified by Stephen Fink
 */
public class OPT_SpecializedMethod {
  /**
   * The method that was specialized
   */
  VM_NormalMethod method;

  /**
   * Corresponding compiled method
   */
  VM_CompiledMethod compiledMethod;             

  /** 
   * Specialized Method index into the SpecializedMethods table
   */
  int smid;

  /**
   * Encodes the rules for generating the specialized code.
   */
  OPT_SpecializationContext context;

  /**
   * constructor for OPT compiler.
   */
  OPT_SpecializedMethod (VM_NormalMethod source, OPT_SpecializationContext context) {
    this.method = source;
    this.context = context;
    this.smid = OPT_SpecializedMethodPool.createSpecializedMethodID();
  }

  /**
   * generate the specialized code for this method
   */
  void compile () {
    compiledMethod = context.specialCompile(method);
  }

  public VM_NormalMethod getMethod () {
    return method;
  }

  public OPT_SpecializationContext getSpecializationContext () {
    return context;
  }

  public VM_CompiledMethod getCompiledMethod () {
    return compiledMethod;
  }

  public void setCompiledMethod(VM_CompiledMethod cm) {
    compiledMethod = cm;
  }

  public int getSpecializedMethodIndex () {
    return smid;
  }

  public String toString () {
    return "Specialized " + method + "  (Context: " + context + ")";
  }
}



