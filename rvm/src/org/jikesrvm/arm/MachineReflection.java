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
package org.jikesrvm.arm;

import static org.jikesrvm.runtime.Reflection.REFLECTION_FPRS_BITS;
import static org.jikesrvm.runtime.Reflection.REFLECTION_GPRS_BITS;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_FPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_DPR;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.Reflection;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

/**
 * Machine dependent portion of Reflective method invoker.
 */
public abstract class MachineReflection {

  /**
   * Determine number/type of registers/spills required to call specified method.
   * See also: Compiler.loadParameters()
   */
  public static int countParameters(RVMMethod method) {
    int gp = FIRST_VOLATILE_GPR.value();
    int fp = FIRST_VOLATILE_FPR.value();
    int spills = 0;

    if (!method.isStatic()) {
      gp++;
    }

    for (TypeReference t : method.getParameterTypes()) {
      if (t.isLongType() || t.isDoubleType()) {
        if ((fp & 1) == 1) fp++; // 64-bit align
        if (fp <= LAST_VOLATILE_FPR.value() - 1)
          fp += 2;
        else
          spills += 2;
      } else if (t.isFloatType()) {
        if (fp <= LAST_VOLATILE_FPR.value())
          fp++;
        else
          spills++;
      } else { // int-like or object
        if (gp <= LAST_VOLATILE_GPR.value())
          gp++;
        else
          spills++;
      }
    }

    final int FPRs = (fp - FIRST_VOLATILE_FPR.value() + 1) >> 1; // Caller expects a count of "double" registers, not single ones, so divide by 2
    final int GPRs = gp - FIRST_VOLATILE_GPR.value();

    // hack to return triple
    return (spills << (REFLECTION_FPRS_BITS + REFLECTION_GPRS_BITS)) |
           (FPRs << REFLECTION_GPRS_BITS) |
           GPRs;
  }

  /**
   * Collect parameters into arrays of registers/spills, as required to call specified method.
   */
  public static void packageParameters(RVMMethod method, Object thisArg, Object[] otherArgs, WordArray GPRs,
                                       double[] FPRs, byte[] FPRmeta, WordArray Spills) {

    TypeReference[] types = method.getParameterTypes();

    int gp = FIRST_VOLATILE_GPR.value();
    int fp = FIRST_VOLATILE_FPR.value();
    int sp = 0;

    if (!method.isStatic())
      GPRs.set(gp++, Reflection.unwrapObject(thisArg).toWord());

    for (int i = 0; i < types.length; i++) {
      TypeReference t = types[i];

      if (t.isFloatType()) {
        int f = Float.floatToIntBits(Reflection.unwrapFloat(otherArgs[i]));
        if (fp <= LAST_VOLATILE_FPR.value()) {
          if ((fp & 1) == 1) { // odd numbered
            FPRs[fp >> 1] = Double.longBitsToDouble(
                              Double.doubleToLongBits(FPRs[fp >> 1]) |
                              (((long) f) << 32));        // Sign-extension shifted off
            fp++;
          } else {
            FPRs[fp++ >> 1] = Double.longBitsToDouble((((long) f) & 0xFFFFFFFFL)); // Mask to avoid sign-extension
          }
        } else {
          Spills.set(sp++, Word.fromIntZeroExtend(Float.floatToIntBits(f)));
        }
      } else if (t.isDoubleType()) {
        if ((fp & 1) == 1) fp++; // 64-bit align
        if (fp <= LAST_VOLATILE_DPR.value()) {
          FPRs[fp >> 1] = Reflection.unwrapDouble(otherArgs[i]);
          fp += 2;
        } else {
          long l = Double.doubleToLongBits(Reflection.unwrapDouble(otherArgs[i]));
          Spills.set(sp++, Word.fromIntZeroExtend((int) l));          // Low word
          Spills.set(sp++, Word.fromIntZeroExtend((int) (l >>> 32))); // High word
        }
      } else if (t.isLongType()) {
        if ((fp & 1) == 1) fp++; // 64-bit align
        if (fp <= LAST_VOLATILE_DPR.value()) {
          FPRs[fp >> 1] = Double.longBitsToDouble(Reflection.unwrapLong(otherArgs[i]));
          fp += 2;
        } else {
          long l = Reflection.unwrapLong(otherArgs[i]);
          Spills.set(sp++, Word.fromIntZeroExtend((int) l));          // Low word
          Spills.set(sp++, Word.fromIntZeroExtend((int) (l >>> 32))); // High word
        }
      } else {
        // Something in GPRs
        if (gp <= LAST_VOLATILE_GPR.value()) {
          if (t.isBooleanType())
            GPRs.set(gp++, Word.fromIntZeroExtend(Reflection.unwrapBooleanAsInt(otherArgs[i])));
          else if (t.isByteType())
            GPRs.set(gp++, Word.fromIntSignExtend(Reflection.unwrapByte(otherArgs[i])));
          else if (t.isCharType())
            GPRs.set(gp++, Word.fromIntZeroExtend(Reflection.unwrapChar(otherArgs[i])));
          else if (t.isShortType())
            GPRs.set(gp++, Word.fromIntSignExtend(Reflection.unwrapShort(otherArgs[i])));
          else if (t.isIntType())
            GPRs.set(gp++, Word.fromIntSignExtend(Reflection.unwrapInt(otherArgs[i])));
          else if (!t.isPrimitiveType())
            GPRs.set(gp++, Reflection.unwrapObject(otherArgs[i]).toWord());
          else
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        } else {
          if (t.isBooleanType())
            Spills.set(sp++, Word.fromIntZeroExtend(Reflection.unwrapBooleanAsInt(otherArgs[i])));
          else if (t.isByteType())
            Spills.set(sp++, Word.fromIntSignExtend(Reflection.unwrapByte(otherArgs[i])));
          else if (t.isCharType())
            Spills.set(sp++, Word.fromIntZeroExtend(Reflection.unwrapChar(otherArgs[i])));
          else if (t.isShortType())
            Spills.set(sp++, Word.fromIntSignExtend(Reflection.unwrapShort(otherArgs[i])));
          else if (t.isIntType())
            Spills.set(sp++, Word.fromIntSignExtend(Reflection.unwrapInt(otherArgs[i])));
          else if (!t.isPrimitiveType())
            Spills.set(sp++, Reflection.unwrapObject(otherArgs[i]).toWord());
          else
            if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert((gp - FIRST_VOLATILE_GPR.value()) == GPRs.length());
    if (VM.VerifyAssertions) VM._assert((fp - FIRST_VOLATILE_FPR.value() + 1) >> 1 == FPRs.length);
    if (VM.VerifyAssertions) VM._assert(sp == Spills.length());

    if (VM.VerifyAssertions) VM._assert(gp <= LAST_VOLATILE_GPR.value() + 1);
    if (VM.VerifyAssertions) VM._assert(fp <= LAST_VOLATILE_FPR.value() + 1);
  }
}
