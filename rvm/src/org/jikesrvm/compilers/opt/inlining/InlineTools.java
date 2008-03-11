/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.inlining;

import java.util.Stack;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.opt.driver.Constants;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.VM;
import org.vmmagic.pragma.Inline;

/**
 * This class provides some utilities that are useful for inlining.
 */
public abstract class InlineTools implements Constants {

  private static final VM_Atom arraycopyName = VM_Atom.findOrCreateAsciiAtom("arraycopy");
  private static final VM_Atom objectArrayCopyDescriptor =
      VM_Atom.findOrCreateAsciiAtom("([Ljava/lang/Object;I[Ljava/lang/Object;II)V");

  /**
   * Does class <code>A</code> directly implement the interface <code>B</code>?
   */
  public static boolean implementsInterface(Class<?> A, Class<?> B) {
    for (Class<?> i : A.getInterfaces()) {
      if (i == B) {
        return true;
      }
    }
    return false;
  }

  /**
   * Does the callee method have a body?
   * @param callee The callee method
   * @return <code>true</code> if it has bytecodes, false otherwise.
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
    return !(callee.isFinal() ||
             callee.getDeclaringClass().isFinal() ||
             callee.isPrivate() ||
             callee.isObjectInitializer() ||
             callee.isStatic());
  }

  /**
   * Is the method CURRENTLY final (not overridden by any subclass)?
   * Note that this says nothing about whether or not the method will
   * be overriden by future dynamically loaded classes.
   */
  public static boolean isCurrentlyFinal(VM_Method callee, boolean searchSubclasses) {
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
      Stack<VM_Class> s = new Stack<VM_Class>();
      for (VM_Class subClass1 : subClasses) {
        s.push(subClass1);
      }
      while (!s.isEmpty()) {
        VM_Class subClass = s.pop();
        if (subClass.findDeclaredMethod(callee.getName(), callee.getDescriptor()) != null) {
          return false;        // found an overridding method
        }
        subClasses = subClass.getSubClasses();
        for (VM_Class subClass1 : subClasses) {
          s.push(subClass1);
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
  public static int inlinedSizeEstimate(VM_NormalMethod callee, CompilationState state) {
    int sizeEstimate = callee.inlinedSizeEstimate();
    // Adjust size estimate downward to account for optimizations enabled
    // by constant parameters.
    Instruction callInstr = state.getCallInstruction();
    int numArgs = Call.getNumberOfParams(callInstr);
    double reductionFactor = 1.0;               // no reduction.
    for (int i = 0; i < numArgs; i++) {
      Operand op = Call.getParam(callInstr, i);
      if (op instanceof RegisterOperand) {
        RegisterOperand rop = (RegisterOperand) op;
        if (rop.isExtant()) {
          reductionFactor -= 0.05;      //  5% credit for being extant.
        }
        if (rop.isPreciseType()) {
          reductionFactor -= 0.15;      // 15% credit for being a precise type.
        }
        if (rop.isDeclaredType()) {
          reductionFactor -= 0.01;      //  1% credit for being a declared type.
        }
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
    return (int) (sizeEstimate * reductionFactor);
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
  public static boolean hasInlinePragma(VM_Method callee, CompilationState state) {
    if (callee.hasInlineAnnotation()) {
      Inline ann = callee.getAnnotation(Inline.class);
      if (ann == null) {
        // annotation was lost, assume it was Always
        return true;
      }
      switch (ann.value()) {
      case Always:
        return true;
      case AllArgumentsAreConstant: {
        boolean result = true;
        Instruction s = state.getCallInstruction();
        for (int i=0, n=Call.getNumberOfParams(s); i < n; i++) {
          if (!Call.getParam(s, i).isConstant()) {
            result = false;
            break;
          }
        }
        if (result) {
          return true;
        }
        break;
      }
      case ArgumentsAreConstant: {
        boolean result = true;
        Instruction s = state.getCallInstruction();
        int[] args = ann.arguments();
        for (int arg : args) {
          if (VM.VerifyAssertions) {
            VM._assert(arg >= 0, "argument is invalid: " + arg);
            VM._assert(arg < Call.getNumberOfParams(s), "argument is invalid: " + arg);
          }
          if (!Call.getParam(s, arg).isConstant()) {
            result = false;
            break;
          }
        }
        if (result) {
          return true;
        }
        break;
      }
      }
    }
    return false;
  }

  /**
   * Should the callee method be barred from ever being considered for inlining?
   *
   * @param callee the method being considered for inlining
   * @param state the compilation state of the caller.
   * @return whether or not the callee should be unconditionally barred
   *         from being inlined.
   */
  public static boolean hasNoInlinePragma(VM_Method callee, CompilationState state) {
    return callee.hasNoInlinePragma();
  }

  /**
   * Is it safe to speculatively inline the callee into the caller?
   *
   * Some forms of speculative inlining are unsafe to apply to
   * methods of the core virtual machine because if we are forced to
   * invalidate the methods, we will be unable to compile their
   * replacement method.
   * The current test is overly conservative, but past attempts at
   * defining a more precise set of "third rail" classes have
   * always resulted in missing some (only to discover them later
   * when Jikes RVM hangs or crashes.)
   *
   * @param caller the caller method
   * @param callee the callee method
   * @return Whether or not we are allowed to speculatively inline
   *         the callee into the caller.
   */
  public static boolean isForbiddenSpeculation(VM_Method caller, VM_Method callee) {
    return caller.getDeclaringClass().isInBootImage() && !callee.getDeclaringClass().getDescriptor().isRVMDescriptor();
  }
}



