/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002, 2004, 2005
 */
//$Id: JikesRVMSupport.java,v 1.16 2006/03/01 12:23:56 dgrove-oss Exp $
package gnu.java.lang;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

import com.ibm.JikesRVM.classloader.*;

import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Runtime;


/**
 * @author Elias Naur
 */
public final class JikesRVMSupport {
  public static Instrumentation createInstrumentation() {
    //-#if RVM_WITH_CLASSPATH_0_92
    try {
      // Horrific backdoor to workaround the private (not package)
      // constructor for InstrumentationImpl found in classpath 0.92
      VM_Class cls = java.lang.JikesRVMSupport.getTypeForClass(InstrumentationImpl.class).asClass();
        
      // Find the defaultConstructor
      VM_Method defaultConstructor = null;
      VM_Method methods[] = cls.getConstructorMethods();
      for (int i = 0; i < methods.length; i++) {
        VM_Method method = methods[i];
        if (method.getParameterTypes().length == 0) {
          defaultConstructor = method;
          break;
        }
      }

      if (!cls.isInitialized()) {
        try {
          VM_Runtime.initializeClassForDynamicLink(cls);
        } catch (Throwable e) {
          ExceptionInInitializerError ex = new ExceptionInInitializerError();
          ex.initCause(e);
          throw ex;
        }
      }

      // Allocate an uninitialized instance;
      Object obj = VM_Runtime.resolvedNewScalar(cls);

      // Run the default constructor on it.
      VM_Reflection.invoke(defaultConstructor, obj, null);

      return (Instrumentation)obj;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    //-#else
    return new InstrumentationImpl();
    //-#endif
  }
}
