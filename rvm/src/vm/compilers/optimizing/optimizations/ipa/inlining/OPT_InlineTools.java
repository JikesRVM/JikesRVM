/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.Stack;

/**
 * This class provides some utilities that are useful for inlining.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
public abstract class OPT_InlineTools implements OPT_Constants {

  /**
   * Does class A directly implement the interface B?
   */
  public static boolean implementsInterface(Class A, Class B) {
    Class[] interfaces = A.getInterfaces();
    for (int i = 0; i < interfaces.length; i++) {
      if (interfaces[i] == B)
        return true;
    }
    return false;
  }

  /**
   * Is it possible to inline the callee into the caller?
   * @param caller the caller method
   * @param callee the callee method
   * @return true if legal, false otherwise
   */
  public static boolean legalToInline(VM_Method caller, VM_Method callee) {
    if (callee == null) {
      return false;            // Unable to idenitfy callee
    }
    if (!callee.getDeclaringClass().isLoaded()) {
      return false;
    }
    if (OPT_ClassLoaderProxy.needsDynamicLink(callee, caller.getDeclaringClass())) {
      return false;  // Can't inline due to class loading state of callee
    }
    return true;
  }

  public static boolean hasBody(VM_Method callee) {
    return !(callee.isNative() || callee.isAbstract());
  }

  /**
   * Does an inlined call to callee need a guard, to protect against
   * a mispredicted dynamic dispatch?
   * 
   * @param callee the callee method
   */
  public static boolean needsGuard(VM_Method callee) {
    if (callee.isFinal() || 
        callee.getDeclaringClass().isFinal() || 
        callee.isPrivate() || 
        callee.isObjectInitializer() || callee.isStatic())
      return false; 
    else 
      return true;
  }

  /**
   * Is the method CURRENTLY final (not overridden by any subclass)?
   * Note that this says nothing about whether or not the method will
   * be overriden by future dynamically loaded classes.
   */
  public static boolean isCurrentlyFinal(VM_Method callee, 
					 boolean searchSubclasses) {
    VM_Class klass = callee.getDeclaringClass();
    if (klass.isInterface()) {
      // interface methods are not final.
      return false;
    }
    VM_Class[] subClasses = klass.getSubClasses();
    if (subClasses.length == 0) {
      //  Currently no subclasses, so trivially not overridden
      return true;
    } else if (searchSubclasses) {
      // see if any subclasses have overridden the method
      Stack s = new Stack();
      for (int i = 0; i < subClasses.length; i++) {
        s.push(subClasses[i]);
      }
      while (!s.isEmpty()) {
        VM_Class subClass = (VM_Class)s.pop();
        if (subClass.findDeclaredMethod(callee.getName(), 
            callee.getDescriptor()) != null) {
          return false;        // found an overridding method
        }
        subClasses = subClass.getSubClasses();
        for (int i = 0; i < subClasses.length; i++) {
          s.push(subClasses[i]);
        }
      }
      return true;  // didn't find an overridding method in all currently resolved subclasses
    } else {
      return false; // could be one, so be conservative.
    }
  }

  /**
   * Given the currently available information at the call site,
   * what's our best guess on the inlined size of the callee?
   * @param callee the method to be inlined
   * @param state the compilation state decribing the call site where it 
   *              is to be inlined
   * @return an inlined size estimate (number of machine code instructions)
   */
  public static int inlinedSizeEstimate(VM_Method callee, 
					OPT_CompilationState state) {
    int sizeEstimate = VM_OptMethodSummary.inlinedSizeEstimate(callee);
    // Adjust size estimate downward to account for optimizations enabled 
    // by constant parameters.
    OPT_Instruction callInstr = state.getCallInstruction();
    int numArgs = Call.getNumberOfParams(callInstr);
    double reductionFactor = 1.0;               // no reduction.
    for (int i = 0; i < numArgs; i++) {
      OPT_Operand op = Call.getParam(callInstr, i);
      if (op instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rop = (OPT_RegisterOperand)op;
        if (rop.isExtant())
          reductionFactor -= 0.05;      //  5% credit for being extant.
        if (rop.isPreciseType())
          reductionFactor -= 0.15;      // 15% credit for being a precise type.
        if (rop.isDeclaredType())
          reductionFactor -= 0.01;      //  1% credit for being a declared type.
      } else if (op.isIntConstant()) {
        reductionFactor -= 0.10;        // 10% credit for being an int constant
      } else if (op.isNullConstant()) {
        reductionFactor -= 0.15;        // 15% credit for being 'null'
      } else if (op.isStringConstant()) {
        reductionFactor -= 0.10;      // 10% credit for being a string constant
      }
    }
    reductionFactor = Math.max(reductionFactor, 0.40); // bound credits at 60% 
                                                       // off.
    return (int)(sizeEstimate*reductionFactor);
  }

  /**
   * Should the callee method always be inlined?
   * Usually this is becuase of a programmer directive (VM_PragmaInline),
   * but we also use this mechanism to hardwire a couple special cases.
   *
   * @param callee the method being considered for inlining
   * @param state the compilation state of the caller.
   * @return whether or not the callee should be unconditionally inlined. 
   */
  public static boolean hasInlinePragma(VM_Method callee, 
					OPT_CompilationState state) {
    if (callee.hasInlinePragma()) return true;
    // If we know what kind of array "src" (argument 0) is
    // then we always want to inline java.lang.System.arraycopy.
    // TODO: Would be nice to discover this automatically!!!
    //       There have to be other methods with similar properties.
    if (callee == VM_Entrypoints.sysArrayCopy) {
      OPT_Operand src = Call.getParam(state.getCallInstruction(), 0);
      return src.getType() != OPT_ClassLoaderProxy.JavaLangObjectType;
    }
    // More arraycopy hacks.  If we the two starting indices are constant and
    // it's not the object array version 
    // (too big...kills other inlining), then inline it.
    if (callee.getDeclaringClass() == OPT_ClassLoaderProxy.VM_Array_type
        && callee.getName() == arraycopyName && callee.getDescriptor()
        != objectArrayCopyDescriptor) {
      return Call.getParam(state.getCallInstruction(), 1).isConstant()
          && Call.getParam(state.getCallInstruction(), 3).isConstant();
    }
    return false;
  }

  private static VM_Atom arraycopyName = 
    VM_Atom.findOrCreateAsciiAtom("arraycopy");
  private static VM_Atom objectArrayCopyDescriptor = 
      VM_Atom.findOrCreateAsciiAtom(
      "([Ljava/lang/Object;I[Ljava/lang/Object;II)V");

  /**
   * Should the callee method be barred from ever being considered for inlining?
   *
   * @param callee the method being considered for inlining
   * @param state the compilation state of the caller.
   * @return whether or not the callee should be unconditionally barred 
   *         from being inlined.
   */
  public static boolean hasNoInlinePragma (VM_Method callee, 
					   OPT_CompilationState state) {
    return callee.hasNoInlinePragma();
  }

    private static final VM_Atom[] thirdRailClasses = new VM_Atom[]{
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/opt/OPT_InvalidationDatabase;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/opt/VM_OptCompiledMethod;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Barriers;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_BasicBlock;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_BaselineCompiler;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_BaselineCompiledMethod;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_BaselineExceptionTable;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_BaselineOptions;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_BuildBB;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_BuildReferenceMaps;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Compiler;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_DynamicLink;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_DynamicLinker;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_DynamicLinker$DL_Helper;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_ForwardReference;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_HardwareTrapCompiledMethod;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_JSRSubroutineInfo;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_MagicCompiler;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_MultianewarrayHelper;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_PendingRETInfo;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_ReferenceMaps;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_Runtime;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_TableBasedDynamicLinker;"),
	VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_UnusualMaps;"),
	VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Runtime;"),
	VM_Atom.findOrCreateAsciiAtom("Ljava/lang/System;")
    };


    public static boolean isForbiddenSpeculation(VM_Method caller, VM_Method callee) {
	if (! callee.getDeclaringClass().getName().startsWith("com.ibm.JikesRVM.VM_")) {
	    VM_Atom defn = caller.getDeclaringClass().getDescriptor();
	    for(int i = 0; i < thirdRailClasses.length; i++) {
		if (defn == thirdRailClasses[i])
		    return true;
	    }
	}

	return false;
    }
}



