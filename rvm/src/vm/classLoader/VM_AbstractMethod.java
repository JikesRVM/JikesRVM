/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import java.io.DataInputStream;
import java.io.IOException;

//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.*;
//-#endif

/**
 * An abstract method of a java class.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_AbstractMethod extends VM_Method {

  /**
   * @param declaringClass the VM_Class object of the class that declared this method
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param exceptionTypes exceptions thrown by this method.
   */
  VM_AbstractMethod(VM_Class declaringClass, VM_MemberReference memRef,
                    int modifiers, VM_TypeReference[] exceptionTypes) {
    super(declaringClass, memRef, modifiers, exceptionTypes);
  }

  /**
   * Generate the code for this method
   */
  protected VM_CompiledMethod genCode() {
    VM_Entrypoints.unexpectedAbstractMethodCallMethod.compile();
    return VM_Entrypoints.unexpectedAbstractMethodCallMethod.getCurrentCompiledMethod();
  }
}
