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
package org.jikesrvm.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.UnpreemptibleNoWarn;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

/**
 * Machine dependent portion of Reflective method invoker.
 */
public abstract class MachineReflection implements RegisterConstants {

  /**
   * Determine number/type of registers and parameters required to
   * call specified method.
   * Unlike the PowerPC code we count all the parameters, not just the
   * ones that spill.  This allow us to make enough space on the stack
   * following the calling convention.
   */
  public static int countParameters(RVMMethod method) {
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

    for (TypeReference t : method.getParameterTypes()) {
      if (t.isLongType()) {
        if (gp > 0) {
          GPRs++;
          gp--;
          if (VM.BuildFor32Addr && gp > 0) {
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
    return (parameters << (Constants.REFLECTION_FPRS_BITS + Constants.REFLECTION_GPRS_BITS)) |
           (FPRs << Constants.REFLECTION_GPRS_BITS) |
           GPRs;
  }

  /**
   * Collect parameters into arrays of registers/spills, as required to
   * call specified method.
   */
  @UnpreemptibleNoWarn("GC is disabled as Objects are turned into Words."+
    "avoid preemption but still allow calls to preemptible unboxing routines")
  public static void packageParameters(RVMMethod method, Object thisArg, Object[] otherArgs, WordArray GPRs,
                                       double[] FPRs, byte[] FPRmeta, WordArray Parameters) {
    int GPR = 0;
    int FPR = ArchConstants.SSE2_FULL ? 0 : FPRs.length;
    int parameter = 0;

    int gp = NUM_PARAMETER_GPRS; // 0, 1, 2
    int fp = NUM_PARAMETER_FPRS; // 0-8

    if (!method.isStatic()) {
      Word val = Magic.objectAsAddress(thisArg).toWord();
      if (gp > 0) {
        gp--;
        GPRs.set(GPR++, val);
      }
      Parameters.set(parameter++, val);
    }

    TypeReference[] types = method.getParameterTypes();
    for (int i = 0; i < types.length; i++) {
      TypeReference t = types[i];

      if (!t.isPrimitiveType()) {
        Word val = Magic.objectAsAddress(otherArgs[i]).toWord();
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);
      } else if (t.isLongType()) {
        long l = (Long)otherArgs[i];
        if (VM.BuildFor32Addr) {
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
        } else {
          Word val = Word.fromLong(l);
          if (gp > 0) {
            gp--;
            GPRs.set(GPR++, val);
          }
          Parameters.set(parameter++, val);
          Parameters.set(parameter++, val);
        }
      } else if (t.isFloatType()) {
        if (fp > 0) {
          fp--;
          if (ArchConstants.SSE2_FULL) {
            FPRs[FPR] = (Float)otherArgs[i];
            FPRmeta[FPR] = 0x0;
            FPR++;
          } else {
            FPRs[--FPR] = (Float)otherArgs[i];
          }
        }
        float f = (Float)otherArgs[i];
        Parameters.set(parameter++, Word.fromIntZeroExtend(Float.floatToIntBits(f)));
      } else if (t.isDoubleType()) {
        if (VM.BuildFor32Addr) {
          if (fp > 0) {
            fp--;
            if (ArchConstants.SSE2_FULL) {
              FPRs[FPR] = (Double)otherArgs[i];
              FPRmeta[FPR] = 0x1;
              FPR++;
            } else {
              FPRs[--FPR] = (Double)otherArgs[i];
            }
          }
          double d = (Double)otherArgs[i];
          long l = Double.doubleToLongBits(d);
          Parameters.set(parameter++, Word.fromIntZeroExtend((int) (l >>> 32)));
          Parameters.set(parameter++, Word.fromIntZeroExtend((int) l));
        } else {
          if (fp > 0) {
            fp--;
            if (ArchConstants.SSE2_FULL) {
              FPRs[FPR] = (Double)otherArgs[i];
              FPRmeta[FPR] = 0x1;
              FPR++;
            } else {
              FPRs[--FPR] = (Double)otherArgs[i];
            }
          }
          double d = (Double)otherArgs[i];
          long l = Double.doubleToLongBits(d);
          Word val = Word.fromLong(l);
          Parameters.set(parameter++, val);
          Parameters.set(parameter++, val);
        }
      } else if (t.isBooleanType()) {
        boolean b = (Boolean)otherArgs[i];
        Word val = Word.fromIntZeroExtend(b ? 1 : 0);
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);
      } else if (t.isCharType()) {
        char c = (Character)otherArgs[i];
        Word val = Word.fromIntZeroExtend(c);
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);
      } else {
        if (VM.VerifyAssertions) VM._assert(t.isByteType() || t.isShortType() || t.isIntType());
        int x = ((Number)otherArgs[i]).intValue();
        Word val = Word.fromIntZeroExtend(x);
        if (gp > 0) {
          gp--;
          GPRs.set(GPR++, val);
        }
        Parameters.set(parameter++, val);
      }
    }
  }
}
