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
package org.jikesrvm.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.Reflection;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

/**
 * Machine dependent portion of Reflective method invoker.
 */
public abstract class MachineReflection implements ArchConstants {

  /**
   * Determine number/type of registers/spills required to call specified method.
   * See also: Compiler.loadParameters()
   */
  public static int countParameters(RVMMethod method) {
    int GPRs = 0;
    int FPRs = 0;
    int Spills = 0;
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    if (!method.isStatic()) {
      if (gp > LAST_VOLATILE_GPR) {
        Spills++;
      } else {
        GPRs++;
        gp++;
      }
    }
    for (TypeReference t : method.getParameterTypes()) {
      if (t.isLongType()) {
        if (VM.BuildFor64Addr) {
          if (gp > LAST_VOLATILE_GPR) {
            Spills++;
          } else {
            GPRs++;
            gp++;
          }
        } else {
          if (gp > LAST_VOLATILE_GPR) {
            Spills += 2;
          } else {
            GPRs++;
            gp++;
            if (gp > LAST_VOLATILE_GPR) {
              Spills++;
            } else {
              GPRs++;
              gp++;
            }
          }
        }
      } else if (t.isFloatType()) {
        if (fp > LAST_VOLATILE_FPR) {
          Spills++;
        } else {
          FPRs++;
          fp++;
        }
      } else if (t.isDoubleType()) {
        if (fp > LAST_VOLATILE_FPR) {
          Spills += VM.BuildFor64Addr ? 1 : 2;
        } else {
          FPRs++;
          fp++;
        }
      } else { // t is object, int, short, char, byte, or boolean
        if (gp > LAST_VOLATILE_GPR) {
          Spills++;
        } else {
          GPRs++;
          gp++;
        }
      }
    }

    // spills[] carries burden of doubleword aligning stack frame
    if (VM.BuildFor32Addr) {
      int frameSize = (Spills << LOG_BYTES_IN_STACKSLOT) + STACKFRAME_HEADER_SIZE;
      frameSize = Memory.alignUp(frameSize, STACKFRAME_ALIGNMENT);
      Spills = (frameSize - STACKFRAME_HEADER_SIZE) >> LOG_BYTES_IN_STACKSLOT;
    }

    // hack to return triple
    return (Spills << (Constants.REFLECTION_FPRS_BITS + Constants.REFLECTION_GPRS_BITS)) |
           (FPRs << Constants.REFLECTION_GPRS_BITS) |
           GPRs;
  }

  /**
   * Collect parameters into arrays of registers/spills, as required to call specified method.
   */
  public static void packageParameters(RVMMethod method, Object thisArg, Object[] otherArgs, WordArray GPRs,
                                       double[] FPRs, byte[] FPRmeta, WordArray Spills) {
    int GPR = GPRs.length();
    int FPR = FPRs.length;
    int Spill = Spills.length();
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    if (!method.isStatic()) {
      if (gp > LAST_VOLATILE_GPR) {
        Spills.set(--Spill, Reflection.unwrapObject(thisArg).toWord());
      } else {
        gp++;
        GPRs.set(--GPR, Reflection.unwrapObject(thisArg).toWord());
      }
    }
    TypeReference[] types = method.getParameterTypes();
    for (int i = 0; i < types.length; i++) {
      TypeReference t = types[i];
      if (t.isLongType()) {
        long l = Reflection.unwrapLong(otherArgs[i]);
        if (VM.BuildFor64Addr) {
          if (gp > LAST_VOLATILE_GPR) {
            Spills.set(--Spill, Word.fromLong(l));
          } else {
            gp++;
            GPRs.set(--GPR, Word.fromLong(l));
          }
        } else {
          Word hi = Word.fromIntZeroExtend((int) (l >>> 32));
          Word lo = Word.fromIntZeroExtend((int) l);
          if (gp > LAST_VOLATILE_GPR) {
            Spills.set(--Spill, hi);
            Spills.set(--Spill, lo);
          } else {
            gp++;
            GPRs.set(--GPR, hi);
            if (gp > LAST_VOLATILE_GPR) {
              Spills.set(--Spill, lo);
            } else {
              gp++;
              GPRs.set(--GPR, lo);
            }
          }
        }
      } else if (t.isFloatType()) {
        float f = Reflection.unwrapFloat(otherArgs[i]);
        if (fp > LAST_VOLATILE_FPR) {
          Spills.set(--Spill, Word.fromIntZeroExtend(Float.floatToIntBits(f)));
        } else {
          fp++;
          FPRs[--FPR] = f;
        }
      } else if (t.isDoubleType()) {
        if (fp > LAST_VOLATILE_FPR) {
          double d = Reflection.unwrapDouble(otherArgs[i]);
          long l = Double.doubleToLongBits(d);
          if (VM.BuildFor64Addr) {
            Spills.set(--Spill, Word.fromLong(l));
          } else {
            Spills.set(--Spill, Word.fromIntZeroExtend((int) (l >>> 32)));
            Spills.set(--Spill, Word.fromIntZeroExtend((int) l));
          }
        } else {
          fp++;
          FPRs[--FPR] = Reflection.unwrapDouble(otherArgs[i]);
        }
      } else if (t.isBooleanType()) {
        Word val = Word.fromIntZeroExtend(Reflection.unwrapBooleanAsInt(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (t.isByteType()) {
        Word val = Word.fromIntZeroExtend(Reflection.unwrapByte(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (t.isCharType()) {
        Word val = Word.fromIntZeroExtend(Reflection.unwrapChar(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (t.isShortType()) {
        Word val = Word.fromIntZeroExtend(Reflection.unwrapShort(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (t.isIntType()) {
        Word val = Word.fromIntZeroExtend(Reflection.unwrapInt(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (!t.isPrimitiveType()) {
        Word val = Reflection.unwrapObject(otherArgs[i]).toWord();
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else {
        if (VM.VerifyAssertions) VM._assert(Constants.NOT_REACHED);
      }
    }
  }
}
