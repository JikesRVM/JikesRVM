/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.runtime.VM_Reflection;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

/**
 * Machine dependent portion of Reflective method invoker.
 */
public abstract class VM_MachineReflection implements VM_RegisterConstants {

  /**
   * Determine number/type of registers and parameters required to
   * call specified method.
   * Unlike the PowerPC code we count all the parameters, not just the
   * ones that spill.  This allow us to make enough space on the stack
   * following the calling convention.
   */
  public static int countParameters(VM_Method method) {
    int GPRs = 0;
    int FPRs = 0;
    int parameters = 0; // parameters size in 32-bits quant.

    int gp = NUM_PARAMETER_GPRS; // 0, 1, 2
    int fp = NUM_PARAMETER_FPRS; // 0-8

    if (!method.isStatic()) {
      if (gp > 0) {
        GPRs++;
        gp--;
      }
      parameters++;
    }

    for (VM_TypeReference t : method.getParameterTypes()) {
      if (t.isLongType()) {
        if (gp > 0) {
          GPRs++;
          gp--;
          if (gp > 0) {
            GPRs++;
            gp--;
          }
        }
        parameters += 2;
      } else if (t.isFloatType()) {
        if (fp > 0) {
          FPRs++;
          fp--;
        }
        parameters++;
      } else if (t.isDoubleType()) {
        if (fp > 0) {
          FPRs++;
          fp--;
        }
        parameters += 2;
      } else { // t is object, int, short, char, byte, or boolean
        if (gp > 0) {
          GPRs++;
          gp--;
        }
        parameters++;
      }
    }

    // hack to return triple
    return (parameters << (VM_Constants.REFLECTION_FPRS_BITS + VM_Constants.REFLECTION_GPRS_BITS)) |
           (FPRs << VM_Constants.REFLECTION_GPRS_BITS) | GPRs;
  }

  /**
   * Collect parameters into arrays of registers/spills, as required to
   * call specified method.
   */
  public static void packageParameters(VM_Method method, Object thisArg, Object[] otherArgs,
                                       WordArray GPRs, double[] FPRs, WordArray Parameters) {
    int GPR = 0;
    int FPR = FPRs.length;
    int parameter = 0;

    int gp = NUM_PARAMETER_GPRS; // 0, 1, 2
    int fp = NUM_PARAMETER_FPRS; // 0-8

    if (!method.isStatic()) {
      Word val = VM_Reflection.unwrapObject(thisArg).toWord();
      if (gp > 0) {
        gp--;
        GPRs.set(GPR++, val);
      }
      Parameters.set(parameter++, val);
    }

    VM_TypeReference[] types = method.getParameterTypes();
    for (int i = 0; i < types.length; i++) {
      VM_TypeReference t = types[i];

      if (t.isLongType()) {
        long l = VM_Reflection.unwrapLong(otherArgs[i]);
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, Word.fromIntZeroExtend((int) (l >>> 32)));
          if (gp > 0) {
            gp--;
            GPRs.set(GPR++, Word.fromIntZeroExtend((int) (l)));
          }
        }
        Parameters.set(parameter++, Word.fromIntZeroExtend((int) (l >>> 32)));
        Parameters.set(parameter++, Word.fromIntZeroExtend((int) l));

      } else if (t.isFloatType()) {
        if (fp > 0) {
          fp--;
          FPRs[--FPR] = VM_Reflection.unwrapFloat(otherArgs[i]);
        }
        float f = VM_Reflection.unwrapFloat(otherArgs[i]);
        Parameters.set(parameter++, Word.fromIntZeroExtend(Float.floatToIntBits(f)));

      } else if (t.isDoubleType()) {
        if (fp > 0) {
          fp--;
          FPRs[--FPR] = VM_Reflection.unwrapDouble(otherArgs[i]);
        }
        double d = VM_Reflection.unwrapDouble(otherArgs[i]);
        long l = Double.doubleToLongBits(d);
        Parameters.set(parameter++, Word.fromIntZeroExtend((int) (l >>> 32)));
        Parameters.set(parameter++, Word.fromIntZeroExtend((int) l));

      } else if (t.isBooleanType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapBooleanAsInt(otherArgs[i]));
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);

      } else if (t.isByteType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapByte(otherArgs[i]));
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);

      } else if (t.isCharType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapChar(otherArgs[i]));
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);

      } else if (t.isShortType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapShort(otherArgs[i]));
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);

      } else if (t.isIntType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapInt(otherArgs[i]));
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);

      } else if (!t.isPrimitiveType()) {
        Word val = VM_Reflection.unwrapObject(otherArgs[i]).toWord();
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);

      } else {
        if (VM.VerifyAssertions) VM._assert(VM_Constants.NOT_REACHED);
      }
    }
  }

}
