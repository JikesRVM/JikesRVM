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
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.SpecializedMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.objectmodel.JavaHeaderConstants;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.Magic;
import org.mmtk.plan.TransitiveClosure;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.SpecializedMethodInvoke;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * A method that scan objects and is specialized to a specific MMTk
 * TransitiveClosure type.
 *
 * In general as there may not be a 1-1 mapping between objects and the
 * specialized methods this class is responsible for performing the
 * mapping.
 *
 * Specialized methods must have a static 'invoke' method that matches
 * the given signature and return type.
 */
@Uninterruptible
public final class SpecializedScanMethod extends SpecializedMethod implements SizeConstants, JavaHeaderConstants {

  /** Use specialized scanning ? */
  public static final boolean ENABLED = true;

  /** This method's signature: the object to be scanned and the trace to use */
  private static final TypeReference[] signature = new TypeReference[] {
    TypeReference.JavaLangObject,
    TypeReference.findOrCreate(TransitiveClosure.class)};

  /** The return type of this method: void */
  private static final TypeReference returnType = TypeReference.Void;

  /** Our type reference */
  private static final TypeReference specializedScanMethodType = TypeReference.findOrCreate(SpecializedScanMethod.class);

  /** Objects with no references or primitive arrays */
  private static final int NULL_PATTERN = 0;
  /** Number of patterns we will specialize */
  private static final int SPECIALIZED_PATTERNS = 64;
  /** Reference arrays */
  private static final int REFARRAY_PATTERN = 64;
  /** Fallback to a slower path that is not specialized */
  private static final int FALLBACK_PATTERN = 65;
  /** The total number of patterns */
  private static final int PATTERNS = 66;
  /** Maximum field offset we can deal with */
  private static final int MAX_SPECIALIZED_OFFSET = 6 << LOG_BYTES_IN_ADDRESS;

  /** We keep the specialized methods for key object reference patterns here.*/
  private final CompiledMethod[] specializedMethods = new CompiledMethod[PATTERNS];

  /** The specialized signature of the method */
  private final TypeReference[] specializedSignature;

  public SpecializedScanMethod(int id, TypeReference specializedTrace) {
    super(id);
    this.specializedSignature = new TypeReference[] { TypeReference.JavaLangObject, specializedTrace };

    if (!VM.BuildWithBaseBootImageCompiler) {
      /* Compile our specialized methods when we are opt compiling */
      RVMClass myClass = specializedScanMethodType.peekType().asClass();
      for(int i=0; i < PATTERNS; i++) {
        RVMMethod method = myClass.findStaticMethod(templateMethodName(i), specializedMethodDescriptor);
        specializedMethods[i] = compileSpecializedMethod(method, specializedSignature);
      }
    }
  }

  /**
   * Get the pattern index for a given type
   */
  @Interruptible
  private static int getPattern(RVMType type) {
    /* Handle array types */
    if (type.isArrayType()) {
      if (type.asArray().getElementType().isReferenceType()) {
        return REFARRAY_PATTERN;
      }
      return NULL_PATTERN;
    }

    /* Build a bitmap if the object is compact enough and is not a reference array */
    int[] offsets = type.asClass().getReferenceOffsets();

    if (offsets.length == 0) {
      return NULL_PATTERN;
    }
    if ((offsets.length << LOG_BYTES_IN_ADDRESS) > SPECIALIZED_PATTERNS) {
      return FALLBACK_PATTERN;
    }

    int base = FIELD_ZERO_OFFSET.toInt();
    int pattern = 0;

    for(int i=0; i < offsets.length; i++) {
      int reference = (offsets[i] - base);
      if (reference > MAX_SPECIALIZED_OFFSET) {
        return FALLBACK_PATTERN;
      }
      pattern |= 1 << (reference >> LOG_BYTES_IN_ADDRESS);
    }

    if (pattern < 0 || pattern > 63) {
      pattern = FALLBACK_PATTERN;
    }

    return pattern;
  }

  /**
   * Return the specialized method for the given type.
   *
   * TODO: Lazily compile specialized methods?
   */
  @Interruptible
  public synchronized CodeArray specializeMethod(RVMType type) {
    /* Work out which pattern this type uses */
    int pattern = getPattern(type);

    if (VM.BuildWithBaseBootImageCompiler) {
      /* There is no point specializing if we aren't opt compiling */
      return null;
    }

    /* Ensure we have a compiled method cached. */
    if (VM.VerifyAssertions) VM._assert(specializedMethods[pattern] != null);

    /* Return the code entry array */
    return specializedMethods[pattern].getEntryCodeArray();
  }

  /**
   * @return the method signature of the specialized method's invoke.
   */
  public TypeReference[] getSignature() {
    return signature;
  }

  /**
   * @return the return type of the specialized method's invoke
   */
  public TypeReference getReturnType() {
    return returnType;
  }

  /**
   * This method peforms the scanning of a given object.
   *
   * This is the method that (may) be hijacked by the compiler to call the specialized method.
   *
   * It is safe for a compiler to ignore the potential gains and just use this method
   * directly.
   *
   * @param id The specialized method id
   * @param object The object to scan
   * @param trace The trace to scan
   */
  @SpecializedMethodInvoke
  @NoInline
  public static void invoke(int id, Object object, TransitiveClosure trace) {
    /* By default we call a non-specialized fallback */
    fallback(object, trace);
  }

  /** Fallback */
  public static void fallback(Object object, TransitiveClosure trace) {
    ObjectReference objectRef = ObjectReference.fromObject(object);
    RVMType type = ObjectModel.getObjectType(objectRef.toObject());
    if (type.isClassType()) {
      RVMClass klass = type.asClass();
      int[] offsets = klass.getReferenceOffsets();
      for(int i=0; i < offsets.length; i++) {
        trace.processEdge(objectRef, objectRef.toAddress().plus(offsets[i]));
      }
    } else if (type.isArrayType() && type.asArray().getElementType().isReferenceType()) {
      for(int i=0; i < ObjectModel.getArrayLength(objectRef.toObject()); i++) {
        trace.processEdge(objectRef, objectRef.toAddress().plus(i << LOG_BYTES_IN_ADDRESS));
      }
    }
  }

  /** All Scalars */
  public static void scalar(Object object, TransitiveClosure trace) {
    Address base = Magic.objectAsAddress(object);
    int[] offsets = ObjectModel.getObjectType(object).asClass().getReferenceOffsets();
    for (int i = 0; i < offsets.length; i++) {
      trace.processEdge(ObjectReference.fromObject(object), base.plus(offsets[i]));
    }
  }

  /** Reference Arrays */
  public static void referenceArray(Object object, TransitiveClosure trace) {
    Address base = Magic.objectAsAddress(object);
    int length = ObjectModel.getArrayLength(object);
    for (int i=0; i < length; i++) {
      trace.processEdge(ObjectReference.fromObject(object), base.plus(i << LOG_BYTES_IN_ADDRESS));
    }
  }

  /** No Reference fields / Primitive Arrays */
  public static void noReferences(Object object, TransitiveClosure trace) {}

  /** All patterns bottom out here */
  @Inline
  public static void pattern(int pattern, Object object, TransitiveClosure trace) {
    Address base = Magic.objectAsAddress(object).plus(FIELD_ZERO_OFFSET);
    if ((pattern &  1) != 0) {
      trace.processEdge(ObjectReference.fromObject(object), base.plus(0));
    }
    if ((pattern &  2) != 0) {
      trace.processEdge(ObjectReference.fromObject(object), base.plus(1 << LOG_BYTES_IN_ADDRESS));
    }
    if ((pattern &  4) != 0) {
      trace.processEdge(ObjectReference.fromObject(object), base.plus(2 << LOG_BYTES_IN_ADDRESS));
    }
    if ((pattern &  8) != 0) {
      trace.processEdge(ObjectReference.fromObject(object), base.plus(3 << LOG_BYTES_IN_ADDRESS));
    }
    if ((pattern & 16) != 0) {
      trace.processEdge(ObjectReference.fromObject(object), base.plus(4 << LOG_BYTES_IN_ADDRESS));
    }
    if ((pattern & 32) != 0) {
      trace.processEdge(ObjectReference.fromObject(object), base.plus(5 << LOG_BYTES_IN_ADDRESS));
    }
  }

  /**
   * Find the template method name for a given pattern.
   *
   * @param pattern The pattern to look for
   * @return The method name that will be used.
   */
  private Atom templateMethodName(int pattern) {
    switch(pattern) {
      case  1: return Names.scalarRNNNNN;
      case  2: return Names.scalarNRNNNN;
      case  3: return Names.scalarRRNNNN;
      case  4: return Names.scalarNNRNNN;
      case  5: return Names.scalarRNRNNN;
      case  6: return Names.scalarNRRNNN;
      case  7: return Names.scalarRRRNNN;
      case  8: return Names.scalarNNNRNN;
      case  9: return Names.scalarRNNRNN;
      case 10: return Names.scalarNRNRNN;
      case 11: return Names.scalarRRNRNN;
      case 12: return Names.scalarNNRRNN;
      case 13: return Names.scalarRNRRNN;
      case 14: return Names.scalarNRRRNN;
      case 15: return Names.scalarRRRRNN;
      case 16: return Names.scalarNNNNRN;
      case 17: return Names.scalarRNNNRN;
      case 18: return Names.scalarNRNNRN;
      case 19: return Names.scalarRRNNRN;
      case 20: return Names.scalarNNRNRN;
      case 21: return Names.scalarRNRNRN;
      case 22: return Names.scalarNRRNRN;
      case 23: return Names.scalarRRRNRN;
      case 24: return Names.scalarNNNRRN;
      case 25: return Names.scalarRNNRRN;
      case 26: return Names.scalarNRNRRN;
      case 27: return Names.scalarRRNRRN;
      case 28: return Names.scalarNNRRRN;
      case 29: return Names.scalarRNRRRN;
      case 30: return Names.scalarNRRRRN;
      case 31: return Names.scalarRRRRRN;
      case 32: return Names.scalarNNNNNR;
      case 33: return Names.scalarRNNNNR;
      case 34: return Names.scalarNRNNNR;
      case 35: return Names.scalarRRNNNR;
      case 36: return Names.scalarNNRNNR;
      case 37: return Names.scalarRNRNNR;
      case 38: return Names.scalarNRRNNR;
      case 39: return Names.scalarRRRNNR;
      case 40: return Names.scalarNNNRNR;
      case 41: return Names.scalarRNNRNR;
      case 42: return Names.scalarNRNRNR;
      case 43: return Names.scalarRRNRNR;
      case 44: return Names.scalarNNRRNR;
      case 45: return Names.scalarRNRRNR;
      case 46: return Names.scalarNRRRNR;
      case 47: return Names.scalarRRRRNR;
      case 48: return Names.scalarNNNNRR;
      case 49: return Names.scalarRNNNRR;
      case 50: return Names.scalarNRNNRR;
      case 51: return Names.scalarRRNNRR;
      case 52: return Names.scalarNNRNRR;
      case 53: return Names.scalarRNRNRR;
      case 54: return Names.scalarNRRNRR;
      case 55: return Names.scalarRRRNRR;
      case 56: return Names.scalarNNNRRR;
      case 57: return Names.scalarRNNRRR;
      case 58: return Names.scalarNRNRRR;
      case 59: return Names.scalarRRNRRR;
      case 60: return Names.scalarNNRRRR;
      case 61: return Names.scalarRNRRRR;
      case 62: return Names.scalarNRRRRR;
      case 63: return Names.scalarRRRRRR;
      case NULL_PATTERN:     return Names.noReferences;
      case REFARRAY_PATTERN: return Names.referenceArray;
      case FALLBACK_PATTERN:
      default:               return Names.scalar;
    }
  }

  /** The generic descriptor for the specialized methods */
  private static final Atom specializedMethodDescriptor = Atom.findOrCreateAsciiAtom("(Ljava/lang/Object;Lorg/mmtk/plan/TransitiveClosure;)V");

  /** The atoms for the names of the specialized methods */
  private static final class Names {
    static final Atom fallback       = Atom.findOrCreateAsciiAtom("fallback");
    static final Atom referenceArray = Atom.findOrCreateAsciiAtom("referenceArray");
    static final Atom scalar         = Atom.findOrCreateAsciiAtom("scalar");
    static final Atom noReferences   = Atom.findOrCreateAsciiAtom("noReferences");
    static final Atom scalarRNNNNN = Atom.findOrCreateAsciiAtom("scalarRNNNNN");
    static final Atom scalarNRNNNN = Atom.findOrCreateAsciiAtom("scalarNRNNNN");
    static final Atom scalarRRNNNN = Atom.findOrCreateAsciiAtom("scalarRRNNNN");
    static final Atom scalarNNRNNN = Atom.findOrCreateAsciiAtom("scalarNNRNNN");
    static final Atom scalarRNRNNN = Atom.findOrCreateAsciiAtom("scalarRNRNNN");
    static final Atom scalarNRRNNN = Atom.findOrCreateAsciiAtom("scalarNRRNNN");
    static final Atom scalarRRRNNN = Atom.findOrCreateAsciiAtom("scalarRRRNNN");
    static final Atom scalarNNNRNN = Atom.findOrCreateAsciiAtom("scalarNNNRNN");
    static final Atom scalarRNNRNN = Atom.findOrCreateAsciiAtom("scalarRNNRNN");
    static final Atom scalarNRNRNN = Atom.findOrCreateAsciiAtom("scalarNRNRNN");
    static final Atom scalarRRNRNN = Atom.findOrCreateAsciiAtom("scalarRRNRNN");
    static final Atom scalarNNRRNN = Atom.findOrCreateAsciiAtom("scalarNNRRNN");
    static final Atom scalarRNRRNN = Atom.findOrCreateAsciiAtom("scalarRNRRNN");
    static final Atom scalarNRRRNN = Atom.findOrCreateAsciiAtom("scalarNRRRNN");
    static final Atom scalarRRRRNN = Atom.findOrCreateAsciiAtom("scalarRRRRNN");
    static final Atom scalarNNNNRN = Atom.findOrCreateAsciiAtom("scalarNNNNRN");
    static final Atom scalarRNNNRN = Atom.findOrCreateAsciiAtom("scalarRNNNRN");
    static final Atom scalarNRNNRN = Atom.findOrCreateAsciiAtom("scalarNRNNRN");
    static final Atom scalarRRNNRN = Atom.findOrCreateAsciiAtom("scalarRRNNRN");
    static final Atom scalarNNRNRN = Atom.findOrCreateAsciiAtom("scalarNNRNRN");
    static final Atom scalarRNRNRN = Atom.findOrCreateAsciiAtom("scalarRNRNRN");
    static final Atom scalarNRRNRN = Atom.findOrCreateAsciiAtom("scalarNRRNRN");
    static final Atom scalarRRRNRN = Atom.findOrCreateAsciiAtom("scalarRRRNRN");
    static final Atom scalarNNNRRN = Atom.findOrCreateAsciiAtom("scalarNNNRRN");
    static final Atom scalarRNNRRN = Atom.findOrCreateAsciiAtom("scalarRNNRRN");
    static final Atom scalarNRNRRN = Atom.findOrCreateAsciiAtom("scalarNRNRRN");
    static final Atom scalarRRNRRN = Atom.findOrCreateAsciiAtom("scalarRRNRRN");
    static final Atom scalarNNRRRN = Atom.findOrCreateAsciiAtom("scalarNNRRRN");
    static final Atom scalarRNRRRN = Atom.findOrCreateAsciiAtom("scalarRNRRRN");
    static final Atom scalarNRRRRN = Atom.findOrCreateAsciiAtom("scalarNRRRRN");
    static final Atom scalarRRRRRN = Atom.findOrCreateAsciiAtom("scalarRRRRRN");
    static final Atom scalarNNNNNR = Atom.findOrCreateAsciiAtom("scalarNNNNNR");
    static final Atom scalarRNNNNR = Atom.findOrCreateAsciiAtom("scalarRNNNNR");
    static final Atom scalarNRNNNR = Atom.findOrCreateAsciiAtom("scalarNRNNNR");
    static final Atom scalarRRNNNR = Atom.findOrCreateAsciiAtom("scalarRRNNNR");
    static final Atom scalarNNRNNR = Atom.findOrCreateAsciiAtom("scalarNNRNNR");
    static final Atom scalarRNRNNR = Atom.findOrCreateAsciiAtom("scalarRNRNNR");
    static final Atom scalarNRRNNR = Atom.findOrCreateAsciiAtom("scalarNRRNNR");
    static final Atom scalarRRRNNR = Atom.findOrCreateAsciiAtom("scalarRRRNNR");
    static final Atom scalarNNNRNR = Atom.findOrCreateAsciiAtom("scalarNNNRNR");
    static final Atom scalarRNNRNR = Atom.findOrCreateAsciiAtom("scalarRNNRNR");
    static final Atom scalarNRNRNR = Atom.findOrCreateAsciiAtom("scalarNRNRNR");
    static final Atom scalarRRNRNR = Atom.findOrCreateAsciiAtom("scalarRRNRNR");
    static final Atom scalarNNRRNR = Atom.findOrCreateAsciiAtom("scalarNNRRNR");
    static final Atom scalarRNRRNR = Atom.findOrCreateAsciiAtom("scalarRNRRNR");
    static final Atom scalarNRRRNR = Atom.findOrCreateAsciiAtom("scalarNRRRNR");
    static final Atom scalarRRRRNR = Atom.findOrCreateAsciiAtom("scalarRRRRNR");
    static final Atom scalarNNNNRR = Atom.findOrCreateAsciiAtom("scalarNNNNRR");
    static final Atom scalarRNNNRR = Atom.findOrCreateAsciiAtom("scalarRNNNRR");
    static final Atom scalarNRNNRR = Atom.findOrCreateAsciiAtom("scalarNRNNRR");
    static final Atom scalarRRNNRR = Atom.findOrCreateAsciiAtom("scalarRRNNRR");
    static final Atom scalarNNRNRR = Atom.findOrCreateAsciiAtom("scalarNNRNRR");
    static final Atom scalarRNRNRR = Atom.findOrCreateAsciiAtom("scalarRNRNRR");
    static final Atom scalarNRRNRR = Atom.findOrCreateAsciiAtom("scalarNRRNRR");
    static final Atom scalarRRRNRR = Atom.findOrCreateAsciiAtom("scalarRRRNRR");
    static final Atom scalarNNNRRR = Atom.findOrCreateAsciiAtom("scalarNNNRRR");
    static final Atom scalarRNNRRR = Atom.findOrCreateAsciiAtom("scalarRNNRRR");
    static final Atom scalarNRNRRR = Atom.findOrCreateAsciiAtom("scalarNRNRRR");
    static final Atom scalarRRNRRR = Atom.findOrCreateAsciiAtom("scalarRRNRRR");
    static final Atom scalarNNRRRR = Atom.findOrCreateAsciiAtom("scalarNNRRRR");
    static final Atom scalarRNRRRR = Atom.findOrCreateAsciiAtom("scalarRNRRRR");
    static final Atom scalarNRRRRR = Atom.findOrCreateAsciiAtom("scalarNRRRRR");
    static final Atom scalarRRRRRR = Atom.findOrCreateAsciiAtom("scalarRRRRRR");
  }

  // CHECKSTYLE:OFF

  public static void scalarRNNNNN(Object object, TransitiveClosure trace) { pattern( 1, object, trace); }
  public static void scalarNRNNNN(Object object, TransitiveClosure trace) { pattern( 2, object, trace); }
  public static void scalarRRNNNN(Object object, TransitiveClosure trace) { pattern( 3, object, trace); }
  public static void scalarNNRNNN(Object object, TransitiveClosure trace) { pattern( 4, object, trace); }
  public static void scalarRNRNNN(Object object, TransitiveClosure trace) { pattern( 5, object, trace); }
  public static void scalarNRRNNN(Object object, TransitiveClosure trace) { pattern( 6, object, trace); }
  public static void scalarRRRNNN(Object object, TransitiveClosure trace) { pattern( 7, object, trace); }
  public static void scalarNNNRNN(Object object, TransitiveClosure trace) { pattern( 8, object, trace); }
  public static void scalarRNNRNN(Object object, TransitiveClosure trace) { pattern( 9, object, trace); }
  public static void scalarNRNRNN(Object object, TransitiveClosure trace) { pattern(10, object, trace); }
  public static void scalarRRNRNN(Object object, TransitiveClosure trace) { pattern(11, object, trace); }
  public static void scalarNNRRNN(Object object, TransitiveClosure trace) { pattern(12, object, trace); }
  public static void scalarRNRRNN(Object object, TransitiveClosure trace) { pattern(13, object, trace); }
  public static void scalarNRRRNN(Object object, TransitiveClosure trace) { pattern(14, object, trace); }
  public static void scalarRRRRNN(Object object, TransitiveClosure trace) { pattern(15, object, trace); }
  public static void scalarNNNNRN(Object object, TransitiveClosure trace) { pattern(16, object, trace); }
  public static void scalarRNNNRN(Object object, TransitiveClosure trace) { pattern(17, object, trace); }
  public static void scalarNRNNRN(Object object, TransitiveClosure trace) { pattern(18, object, trace); }
  public static void scalarRRNNRN(Object object, TransitiveClosure trace) { pattern(19, object, trace); }
  public static void scalarNNRNRN(Object object, TransitiveClosure trace) { pattern(20, object, trace); }
  public static void scalarRNRNRN(Object object, TransitiveClosure trace) { pattern(21, object, trace); }
  public static void scalarNRRNRN(Object object, TransitiveClosure trace) { pattern(22, object, trace); }
  public static void scalarRRRNRN(Object object, TransitiveClosure trace) { pattern(23, object, trace); }
  public static void scalarNNNRRN(Object object, TransitiveClosure trace) { pattern(24, object, trace); }
  public static void scalarRNNRRN(Object object, TransitiveClosure trace) { pattern(25, object, trace); }
  public static void scalarNRNRRN(Object object, TransitiveClosure trace) { pattern(26, object, trace); }
  public static void scalarRRNRRN(Object object, TransitiveClosure trace) { pattern(27, object, trace); }
  public static void scalarNNRRRN(Object object, TransitiveClosure trace) { pattern(28, object, trace); }
  public static void scalarRNRRRN(Object object, TransitiveClosure trace) { pattern(29, object, trace); }
  public static void scalarNRRRRN(Object object, TransitiveClosure trace) { pattern(30, object, trace); }
  public static void scalarRRRRRN(Object object, TransitiveClosure trace) { pattern(31, object, trace); }
  public static void scalarNNNNNR(Object object, TransitiveClosure trace) { pattern(32, object, trace); }
  public static void scalarRNNNNR(Object object, TransitiveClosure trace) { pattern(33, object, trace); }
  public static void scalarNRNNNR(Object object, TransitiveClosure trace) { pattern(34, object, trace); }
  public static void scalarRRNNNR(Object object, TransitiveClosure trace) { pattern(35, object, trace); }
  public static void scalarNNRNNR(Object object, TransitiveClosure trace) { pattern(36, object, trace); }
  public static void scalarRNRNNR(Object object, TransitiveClosure trace) { pattern(37, object, trace); }
  public static void scalarNRRNNR(Object object, TransitiveClosure trace) { pattern(38, object, trace); }
  public static void scalarRRRNNR(Object object, TransitiveClosure trace) { pattern(39, object, trace); }
  public static void scalarNNNRNR(Object object, TransitiveClosure trace) { pattern(40, object, trace); }
  public static void scalarRNNRNR(Object object, TransitiveClosure trace) { pattern(41, object, trace); }
  public static void scalarNRNRNR(Object object, TransitiveClosure trace) { pattern(42, object, trace); }
  public static void scalarRRNRNR(Object object, TransitiveClosure trace) { pattern(43, object, trace); }
  public static void scalarNNRRNR(Object object, TransitiveClosure trace) { pattern(44, object, trace); }
  public static void scalarRNRRNR(Object object, TransitiveClosure trace) { pattern(45, object, trace); }
  public static void scalarNRRRNR(Object object, TransitiveClosure trace) { pattern(46, object, trace); }
  public static void scalarRRRRNR(Object object, TransitiveClosure trace) { pattern(47, object, trace); }
  public static void scalarNNNNRR(Object object, TransitiveClosure trace) { pattern(48, object, trace); }
  public static void scalarRNNNRR(Object object, TransitiveClosure trace) { pattern(49, object, trace); }
  public static void scalarNRNNRR(Object object, TransitiveClosure trace) { pattern(50, object, trace); }
  public static void scalarRRNNRR(Object object, TransitiveClosure trace) { pattern(51, object, trace); }
  public static void scalarNNRNRR(Object object, TransitiveClosure trace) { pattern(52, object, trace); }
  public static void scalarRNRNRR(Object object, TransitiveClosure trace) { pattern(53, object, trace); }
  public static void scalarNRRNRR(Object object, TransitiveClosure trace) { pattern(54, object, trace); }
  public static void scalarRRRNRR(Object object, TransitiveClosure trace) { pattern(55, object, trace); }
  public static void scalarNNNRRR(Object object, TransitiveClosure trace) { pattern(56, object, trace); }
  public static void scalarRNNRRR(Object object, TransitiveClosure trace) { pattern(57, object, trace); }
  public static void scalarNRNRRR(Object object, TransitiveClosure trace) { pattern(58, object, trace); }
  public static void scalarRRNRRR(Object object, TransitiveClosure trace) { pattern(59, object, trace); }
  public static void scalarNNRRRR(Object object, TransitiveClosure trace) { pattern(60, object, trace); }
  public static void scalarRNRRRR(Object object, TransitiveClosure trace) { pattern(61, object, trace); }
  public static void scalarNRRRRR(Object object, TransitiveClosure trace) { pattern(62, object, trace); }
  public static void scalarRRRRRR(Object object, TransitiveClosure trace) { pattern(63, object, trace); }

  // CHECKSTYLE:ON
}
