/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.ppc;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_Constants;
import com.ibm.jikesrvm.VM_Memory;
import com.ibm.jikesrvm.VM_Reflection;
import com.ibm.jikesrvm.classloader.*;

import org.vmmagic.unboxed.*;

/**
 * Machine dependent portion of Reflective method invoker.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 * @date 15 Jul 1998 
 */
public abstract class VM_MachineReflection implements VM_Constants {

  /** 
   * Determine number/type of registers/spills required to call specified method.
   * See also: VM_Compiler.loadParameters()
   */
  public static int countParameters(VM_Method method) {
    int GPRs   = 0;
    int FPRs   = 0;
    int Spills = 0;
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    if (!method.isStatic()) {
      if (gp > LAST_VOLATILE_GPR) Spills++;
      else {GPRs++; gp++;}
    }
    for (VM_TypeReference t : method.getParameterTypes()) {
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
        if (fp > LAST_VOLATILE_FPR) Spills++;
        else {
          FPRs++;
          fp++;
        }
      } else if (t.isDoubleType()) {
        if (fp > LAST_VOLATILE_FPR) Spills += VM.BuildFor64Addr ? 1 : 2;
        else {
          FPRs++;
          fp++;
        }
      } else { // t is object, int, short, char, byte, or boolean
        if (gp > LAST_VOLATILE_GPR) Spills++;
        else {
          GPRs++;
          gp++;
        }
      }
    }

    // spills[] carries burden of doubleword aligning stack frame
    if (VM.BuildFor32Addr) {
      int frameSize  = (Spills << LOG_BYTES_IN_STACKSLOT) + STACKFRAME_HEADER_SIZE;
      frameSize = VM_Memory.alignUp(frameSize, STACKFRAME_ALIGNMENT);
      Spills = (frameSize-STACKFRAME_HEADER_SIZE) >> LOG_BYTES_IN_STACKSLOT;        
    }

    // hack to return triple
    return (Spills<<(REFLECTION_FPRS_BITS+REFLECTION_GPRS_BITS)) |
      (FPRs<<REFLECTION_GPRS_BITS) | GPRs;
  }
 

  /**
   * Collect parameters into arrays of registers/spills, as required to call specified method.
   */
  public static void packageParameters(VM_Method method, Object thisArg, 
                                Object[] otherArgs, WordArray GPRs, 
                                double[] FPRs, WordArray Spills) {
    int GPR   = GPRs.length();
    int FPR   = FPRs.length;
    int Spill = Spills.length();
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    if (!method.isStatic()) {
      if (gp > LAST_VOLATILE_GPR)
        Spills.set(--Spill, VM_Reflection.unwrapObject(thisArg).toWord());
      else {
        gp++;
        GPRs.set(--GPR, VM_Reflection.unwrapObject(thisArg).toWord());
      }
    }
    VM_TypeReference [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      VM_TypeReference t = types[i];
      if (t.isLongType()) {
        long l = VM_Reflection.unwrapLong(otherArgs[i]);
        if (VM.BuildFor64Addr) {
          if (gp > LAST_VOLATILE_GPR) {
            Spills.set(--Spill, Word.fromLong(l));
          } else {
            gp++;
            GPRs.set(--GPR, Word.fromLong(l));
          }
        } else {
          Word hi = Word.fromIntZeroExtend((int)(l>>>32));
          Word lo = Word.fromIntZeroExtend((int)l);
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
        float f = VM_Reflection.unwrapFloat(otherArgs[i]);
        if (fp > LAST_VOLATILE_FPR) {
          Spills.set(--Spill, Word.fromIntZeroExtend(Float.floatToIntBits(f)));
        } else {
          fp++;
          FPRs[--FPR] = f;
        }
      } else if (t.isDoubleType()) {
        if (fp > LAST_VOLATILE_FPR) {
          double d = VM_Reflection.unwrapDouble(otherArgs[i]);
          long l = Double.doubleToLongBits(d);
          if (VM.BuildFor64Addr) {
            Spills.set(--Spill, Word.fromLong(l));
          } else {
            Spills.set(--Spill, Word.fromIntZeroExtend((int)(l>>>32)));
            Spills.set(--Spill, Word.fromIntZeroExtend((int)l));
          }
        } else {
          fp++;
          FPRs[--FPR] = VM_Reflection.unwrapDouble(otherArgs[i]);
        }
      } else if (t.isBooleanType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapBooleanAsInt(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (t.isByteType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapByte(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (t.isCharType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapChar(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (t.isShortType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapShort(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (t.isIntType()) {
        Word val = Word.fromIntZeroExtend(VM_Reflection.unwrapInt(otherArgs[i]));
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else if (!t.isPrimitiveType()) {
        Word val = VM_Reflection.unwrapObject(otherArgs[i]).toWord();
        if (gp > LAST_VOLATILE_GPR) {
          Spills.set(--Spill, val);
        } else {
          gp++;
          GPRs.set(--GPR, val);
        }
      } else  {
        if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      }
    }
  }
}
