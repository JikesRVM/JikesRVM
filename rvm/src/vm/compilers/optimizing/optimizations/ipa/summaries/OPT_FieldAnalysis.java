/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

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
 *
 * @author Stephen Fink
 */
public final class OPT_FieldAnalysis extends OPT_CompilerPhase {
  final static private boolean DEBUG = false;

  public final boolean shouldPerform (OPT_Options options) {
    return options.FIELD_ANALYSIS;
  }

  public final String getName () {
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
  private static boolean isCandidate (VM_TypeReference tref) {
    VM_Type t = tref.peekResolvedType();
    if (t == null) return false;
    if (t.isPrimitiveType())
      return false;
    if (t.isClassType() && t.asClass().isFinal())
      return false;
    if (t.isArrayType()) {
      return isCandidate(tref.getInnermostElementType());
    }
    return true;
  }

  /**
   * Have we determined a single concrete type for a field? If so,
   * return the concrete type.  Else, return null.
   */
  public static VM_TypeReference getConcreteType (VM_Field f) {
    // don't bother for primitives and arrays of primitives
    // and friends
    if (!isCandidate(f.getType()))
      return f.getType();
    // for some special classes, the flow-insensitive summaries
    // are INCORRECT due to using the wrong implementation
    // during boot image writing.  For these special cases,
    // give up.
    if (isTrouble(f))
      return null;
    if (DEBUG) {
      VM_TypeReference t = db.getConcreteType(f);
      if (t != null) VM.sysWriteln("CONCRETE TYPE " + f + " IS " + t);
    }
    return db.getConcreteType(f);
  }

  /**
   * Record field analysis information for an IR.
   *
   * @param ir the governing IR
   */
  final public void perform (OPT_IR ir) {
    // walk over each instructions.  For each putfield or putstatic,
    // record the concrete type assigned to a field; or, record
    // BOTTOM if the concrete type is unknown.
    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); 
        e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (PutField.conforms(s)) {
        OPT_LocationOperand l = PutField.getLocation(s);
        VM_Field f = l.getFieldRef().peekResolvedField();
        if (f == null) continue;
        if (!isCandidate(f.getType())) continue;
        // a little tricky: we cannot draw any conclusions from inlined
        // method bodies, since we cannot assume what information, 
        // gleaned from context, does not hold everywhere
        if (s.position.getMethod() != ir.method)
          continue;
        OPT_Operand value = PutField.getValue(s);
        if (value.isRegister()) {
          if (value.asRegister().isPreciseType()) {
            VM_TypeReference type = value.asRegister().type;
            recordConcreteType(ir.method, f, type);
          } else {
            recordBottom(ir.method, f);
          }
        }
      } else if (PutStatic.conforms(s)) {
        OPT_LocationOperand l = PutStatic.getLocation(s);
        VM_Field f = l.getFieldRef().peekResolvedField();
        if (f == null) continue;
        if (!isCandidate(f.getType())) continue;
        // a little tricky: we cannot draw any conclusions from inlined
        // method bodies, since we cannot assume what information, 
        // gleaned from context, does not hold everywhere
        if (s.position.getMethod() != ir.method)
          continue;
        OPT_Operand value = PutStatic.getValue(s);
        if (value.isRegister()) {
          if (value.asRegister().isPreciseType()) {
            VM_TypeReference type = value.asRegister().type;
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
  private final static OPT_FieldDatabase db = new OPT_FieldDatabase();

  /**
   * Record that a method writes an unknown concrete type to a field.
   */
  private static void recordBottom (VM_Method m, VM_Field f) {
    // for now, only track private fields
    if (!f.isPrivate())
      return;
    if (isTrouble(f))
      return;
    OPT_FieldDatabase.FieldDatabaseEntry entry = db.findOrCreateEntry(f);
    OPT_FieldDatabase.FieldWriterInfo info = entry.findMethodInfo(m);
    if (VM.VerifyAssertions) {
      if (info == null)
        VM.sysWrite("ERROR recordBottom: method " + m + " field " + f);
      VM._assert(info != null);
    }
    info.setBottom();
    info.setAnalyzed();
  }

  /**
   * Record that a method stores an object of a particular concrete type
   * to a field.
   */
  private void recordConcreteType (VM_Method m, VM_Field f, VM_TypeReference t) {
    // for now, only track private fields
    if (!f.isPrivate())
      return;
    OPT_FieldDatabase.FieldDatabaseEntry entry = db.findOrCreateEntry(f);
    OPT_FieldDatabase.FieldWriterInfo info = entry.findMethodInfo(m);
    info.setAnalyzed();
    if (info.isBottom())
      return;
    VM_TypeReference oldType = info.concreteType;
    if (oldType == null) {
      // set a new concrete type for this field.
      info.concreteType = t;
    } else if (oldType != t) {
      // we've previously determined a DIFFERENT! concrete type.
      // meet the two types: ie., change it to bottom.
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
  private static boolean isTrouble (VM_Field f) {
    if (f.getDeclaringClass() == VM_Type.JavaLangStringType)
      return true;
    return false;
  }
}
