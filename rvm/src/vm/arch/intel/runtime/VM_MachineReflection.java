/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Machine dependent portion of Reflective method invoker.
 *
 * @author Maria Butrico
 */
public class VM_MachineReflection implements VM_Constants {

  /** 
   * Determine number/type of registers and parameters required to
   * call specified method.
   * Unlike the PowerPC code we count all the parameters, not just the
   * ones that spill.  This allow us to make enough space on the stack
   * following the calling convention.
   */
  static int countParameters(VM_Method method) {
    int GPRs   = 0;
    int FPRs   = 0;
    int parameters = 0;	// parameters size in 32-bits quant.

    int gp = NUM_PARAMETER_GPRS; // 0, 1, 2
    int fp = NUM_PARAMETER_FPRS; // 0-8

    if (!method.isStatic()) {
      if (gp > 0) {GPRs++; gp--;}
      parameters++;
    }

    VM_TypeReference [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      VM_TypeReference t = types[i];
      if (t.isLongType()) {
	if (gp > 0) {
	  GPRs++; gp--;
	  if (gp > 0) {GPRs++; gp--;}
	}
	parameters+=2; 
      } else if (t.isFloatType()) {
	if (fp > 0) {FPRs++; fp--;}
	parameters++;
      } else if (t.isDoubleType()) {
	if (fp > 0) {FPRs++; fp--;}
	parameters+=2;
      } else { // t is object, int, short, char, byte, or boolean
	if (gp > 0) {GPRs++; gp--;}
	parameters++;
      }
    }

    // hack to return triple
    return (parameters<<(REFLECTION_FPRS_BITS+REFLECTION_GPRS_BITS)) |
      (FPRs<<REFLECTION_GPRS_BITS) | GPRs;
  }
  

  /**
   * Collect parameters into arrays of registers/spills, as required to
   * call specified method.
   */
  static void packageParameters(VM_Method method, Object thisArg, Object[] otherArgs,
                                VM_WordArray GPRs, double[] FPRs, VM_WordArray Parameters) {
    int GPR	   	= 0;
    int FPR		= FPRs.length;
    int parameter	= 0;

    int gp = NUM_PARAMETER_GPRS; // 0, 1, 2
    int fp = NUM_PARAMETER_FPRS; // 0-8

    if (!method.isStatic()) {
      VM_Word val = VM_Reflection.unwrapObject(thisArg).toWord();
      if (gp > 0) {
	gp--;
	GPRs.set(GPR++, val);
      }
      Parameters.set(parameter++, val);
    }

    VM_TypeReference [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      VM_TypeReference t = types[i];

      if (t.isLongType()) {
	long l = VM_Reflection.unwrapLong(otherArgs[i]);
	if (gp > 0) {
	  gp--;
	  GPRs.set(GPR++, VM_Word.fromIntZeroExtend((int)(l>>>32)));
	  if (gp > 0) {
	    gp--;
	    GPRs.set(GPR++, VM_Word.fromIntZeroExtend((int)(l)));
	  }
	}
        Parameters.set(parameter++, VM_Word.fromIntZeroExtend((int)(l>>>32)));
	Parameters.set(parameter++, VM_Word.fromIntZeroExtend((int)l));
        
      } else if (t.isFloatType()) {
	if (fp > 0) {
	  fp--;
	  FPRs[--FPR] = VM_Reflection.unwrapFloat(otherArgs[i]);
	}
	float f = VM_Reflection.unwrapFloat(otherArgs[i]);
	Parameters.set(parameter++, VM_Word.fromIntZeroExtend(Float.floatToIntBits(f)));

      } else if (t.isDoubleType()) {
	if (fp > 0) {
	  fp--;
	  FPRs[--FPR] = VM_Reflection.unwrapDouble(otherArgs[i]);
	}
	double d = VM_Reflection.unwrapDouble(otherArgs[i]);
	long l = Double.doubleToLongBits(d);
	Parameters.set(parameter++, VM_Word.fromIntZeroExtend((int)(l>>>32)));
	Parameters.set(parameter++, VM_Word.fromIntZeroExtend((int)l));

      } else if (t.isBooleanType()) {
        VM_Word val = VM_Word.fromIntZeroExtend(VM_Reflection.unwrapBooleanAsInt(otherArgs[i]));
	if (gp > 0) {
	  gp--;
	  GPRs.set(GPR++, val);
	}
	Parameters.set(parameter++, val);

      } else if (t.isByteType()) {
        VM_Word val = VM_Word.fromIntZeroExtend(VM_Reflection.unwrapByte(otherArgs[i]));
	if (gp > 0) {
	  gp--;
	  GPRs.set(GPR++, val);
	}
	Parameters.set(parameter++, val);

      } else if (t.isCharType()) {
        VM_Word val = VM_Word.fromIntZeroExtend(VM_Reflection.unwrapChar(otherArgs[i]));
	if (gp > 0) {
	  gp--;
	  GPRs.set(GPR++, val);
	}
        Parameters.set(parameter++, val);
        
      } else if (t.isShortType()) {
        VM_Word val = VM_Word.fromIntZeroExtend(VM_Reflection.unwrapShort(otherArgs[i]));
	if (gp > 0) {
	  gp--;
	  GPRs.set(GPR++, val);
	}
	Parameters.set(parameter++, val);

      } else if (t.isIntType()) {
        VM_Word val = VM_Word.fromIntZeroExtend(VM_Reflection.unwrapInt(otherArgs[i]));
	if (gp > 0) {
	  gp--;
	  GPRs.set(GPR++, val);
	}
	Parameters.set(parameter++, val);

      } else if (!t.isPrimitiveType()) {
        VM_Word val = VM_Reflection.unwrapObject(otherArgs[i]).toWord();
	if (gp > 0) {
	  gp--;
	  GPRs.set(GPR++, val);
	}
	Parameters.set(parameter++, val);

      } else  {
	if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      }
    }
  }

}
