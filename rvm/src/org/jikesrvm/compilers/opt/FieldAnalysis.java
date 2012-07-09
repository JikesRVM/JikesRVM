/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * Flow-insensitive, context-insensitive, interprocedural analysis
 * of fields.
 *
 * <ul>
 * <li> TODO: handle more than just private fields
 * <li> TODO: make this re-entrant
 * <li> TODO: better class hierarchy analysis
 * <li> TODO: context-sensitive or flow-sensitive summaries.
 * <li> TODO: force eager analysis of methods
 * </ul>
 */
public final class FieldAnalysis extends CompilerPhase {
  private static final boolean DEBUG = false;

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public boolean shouldPerform(OptOptions options) {
    return options.FIELD_ANALYSIS;
  }

  @Override
  public String getName() {
    return "Field Analysis";
  }

  /**
   * Is a type a candidate for type analysis?
   * <p> NO iff:
   * <ul>
   *    <li> it's a primitive
   *    <li> it's final
   *    <li> it's an array of primitive
   *    <li> it's an array of final
   * </ul>
   */
  private static boolean isCandidate(TypeReference tref) {
    RVMType t = tref.peekType();
    if (t == null) return false;
    if (t.isPrimitiveType() || t.isUnboxedType()) {
      return false;
    }
    if (t.isClassType() && t.asClass().isFinal()) {
      return false;
    }
    if (t.isArrayType()) {
      return isCandidate(tref.getInnermostElementType());
    }
    return true;
  }

  /**
   * Have we determined a single concrete type for a field? If so,
   * return the concrete type.  Else, return null.
   */
  public static TypeReference getConcreteType(RVMField f) {
    // don't bother for primitives and arrays of primitives
    // and friends
    if (!isCandidate(f.getType())) {
      return f.getType();
    }
    // for some special classes, the flow-insensitive summaries
    // are INCORRECT due to using the wrong implementation
    // during boot image writing.  For these special cases,
    // give up.
    if (isTrouble(f)) {
      return null;
    }
    if (DEBUG) {
      TypeReference t = db.getConcreteType(f);
      if (t != null) VM.sysWriteln("CONCRETE TYPE " + f + " IS " + t);
    }
    return db.getConcreteType(f);
  }

  /**
   * Record field analysis information for an IR.
   *
   * @param ir the governing IR
   */
  @Override
  public void perform(IR ir) {
    // walk over each instructions.  For each putfield or putstatic,
    // record the concrete type assigned to a field; or, record
    // BOTTOM if the concrete type is unknown.
    for (InstructionEnumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.next();
      if (PutField.conforms(s)) {
        LocationOperand l = PutField.getLocation(s);
        RVMField f = l.getFieldRef().peekResolvedField();
        if (f == null) continue;
        if (!isCandidate(f.getType())) continue;
        // a little tricky: we cannot draw any conclusions from inlined
        // method bodies, since we cannot assume what information,
        // gleaned from context, does not hold everywhere
        if (s.position.getMethod() != ir.method) {
          continue;
        }
        Operand value = PutField.getValue(s);
        if (value.isRegister()) {
          if (value.asRegister().isPreciseType()) {
            TypeReference type = value.asRegister().getType();
            recordConcreteType(ir.method, f, type);
          } else {
            recordBottom(ir.method, f);
          }
        }
      } else if (PutStatic.conforms(s)) {
        LocationOperand l = PutStatic.getLocation(s);
        RVMField f = l.getFieldRef().peekResolvedField();
        if (f == null) continue;
        if (!isCandidate(f.getType())) continue;
        // a little tricky: we cannot draw any conclusions from inlined
        // method bodies, since we cannot assume what information,
        // gleaned from context, does not hold everywhere
        if (s.position.getMethod() != ir.method) {
          continue;
        }
        Operand value = PutStatic.getValue(s);
        if (value.isRegister()) {
          if (value.asRegister().isPreciseType()) {
            TypeReference type = value.asRegister().getType();
            recordConcreteType(ir.method, f, type);
          } else {
            recordBottom(ir.method, f);
          }
        }
      }
    }
  }

  /**
   * The backing store
   */
  private static final FieldDatabase db = new FieldDatabase();

  /**
   * Record that a method writes an unknown concrete type to a field.
   */
  private static synchronized void recordBottom(RVMMethod m, RVMField f) {
    // for now, only track private fields
    if (!f.isPrivate()) {
      return;
    }
    if (isTrouble(f)) {
      return;
    }
    FieldDatabase.FieldDatabaseEntry entry = db.findOrCreateEntry(f);
    FieldDatabase.FieldWriterInfo info = entry.findMethodInfo(m);
    if (VM.VerifyAssertions) {
      if (info == null) {
        VM.sysWrite("ERROR recordBottom: method " + m + " field " + f);
      }
      VM._assert(info != null);
    }
    info.setBottom();
    info.setAnalyzed();
  }

  /**
   * Record that a method stores an object of a particular concrete type
   * to a field.
   */
  private static synchronized void recordConcreteType(RVMMethod m, RVMField f, TypeReference t) {
    // for now, only track private fields
    if (!f.isPrivate()) {
      return;
    }
    FieldDatabase.FieldDatabaseEntry entry = db.findOrCreateEntry(f);
    FieldDatabase.FieldWriterInfo info = entry.findMethodInfo(m);
    info.setAnalyzed();
    if (info.isBottom()) {
      return;
    }
    TypeReference oldType = info.concreteType;
    if (oldType == null) {
      // set a new concrete type for this field.
      info.concreteType = t;
    } else if (oldType != t) {
      // we've previously determined a DIFFERENT! concrete type.
      // meet the two types: i.e., change it to bottom.
      info.setBottom();
    }
  }

  /**
   * For some special classes, the flow-insensitive summaries
   * are INCORRECT due to using the wrong implementation
   * during boot image writing.  For these special cases,
   * give up. TODO: work around this by recomputing the summary at
   * runtime?
   */
  private static boolean isTrouble(RVMField f) {
    return f.getDeclaringClass() == RVMType.JavaLangStringType;
  }
}
