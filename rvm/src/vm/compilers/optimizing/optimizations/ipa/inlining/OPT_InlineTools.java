/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.Stack;

import org.vmmagic.pragma.*;

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
   * Does the callee method have a body?
   * @param callee the callee method
   * @return true if it has bytecodes, false otherwise.
   */
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
  public static int inlinedSizeEstimate(VM_NormalMethod callee, 
                                        OPT_CompilationState state) {
    int sizeEstimate = callee.inlinedSizeEstimate();
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
   * Usually this is becuase of a programmer directive (InlinePragma),
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
      return src.getType() != VM_TypeReference.JavaLangObject;
    }
    // More arraycopy hacks.  If we the two starting indices are constant and
    // it's not the object array version 
    // (too big...kills other inlining), then inline it.
    if (callee.getDeclaringClass().getTypeRef() == VM_TypeReference.VM_Array &&
        callee.getName() == arraycopyName && 
        callee.getDescriptor() != objectArrayCopyDescriptor) {
      return Call.getParam(state.getCallInstruction(), 1).isConstant()
          && Call.getParam(state.getCallInstruction(), 3).isConstant();
    }
    return false;
  }

  private static VM_Atom arraycopyName = VM_Atom.findOrCreateAsciiAtom("arraycopy");
  private static VM_Atom objectArrayCopyDescriptor = 
    VM_Atom.findOrCreateAsciiAtom("([Ljava/lang/Object;I[Ljava/lang/Object;II)V");

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

  /**
   * Is it safe to speculatively inline the callee into the caller.
   * Some forms of speculative inlining are unsafe to apply to 
   * methods of the core virtual machine because if we are forced to 
   * invalidate the methods, we will be unable to compile their 
   * replacement method.
   * The current test is overly conservative, but past attempts at
   * defining a more precise set of "third rail" classes have
   * always resulted in missing some (only to discover them later
   * when Jikes RVM hangs or crashes.
   * @param caller the caller method
   * @param calleee the callee method
   * @return whether or not we are allowed to speculatively inline
   *         the callee into the caller.
   */
  public static boolean isForbiddenSpeculation(VM_Method caller, VM_Method callee) {
    return caller.getDeclaringClass().isInBootImage() &&
      !callee.getDeclaringClass().getDescriptor().isRVMDescriptor();
  }
}



