/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Machine dependent portion of Reflective method invoker.
 *
 * @author Maria Butrico
 */
public class VM_MachineReflection implements VM_Constants {
  //-----------//
  // interface //
  //-----------//
   

   //----------------//
   // implementation //
   //----------------//
   
   // Determine number/type of registers and parameters required to
   // call specified method.
   //  Unlike the PowerPC code we count all the parameters, not just the
   // ones that spill.  This allow us to make enough space on the stack
   // following the calling convention.
   //
  static int 
    countParameters(VM_Method method) {
    int GPRs   = 0;
    int FPRs   = 0;
    int parameters = 0;	// parameters size in 32-bits quant.

    int gp = NUM_PARAMETER_GPRS; // 0, 1, 2
    int fp = NUM_PARAMETER_FPRS; // 0-8

    if (!method.isStatic()) {
      if (gp > 0) {GPRs++; gp--;}
      parameters++;
    }

    VM_Type [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      VM_Type t = types[i];
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


  // Collect parameters into arrays of registers/spills, as required to
  // call specified method.
  static void 
    packageParameters(VM_Method method, Object thisArg, Object[] otherArgs,
		      int[] GPRs, double[] FPRs, int[] Parameters) {
    int GPR	   	= 0;
    int FPR		= FPRs.length;
    int parameter	= 0;


    int gp = NUM_PARAMETER_GPRS; // 0, 1, 2
    int fp = NUM_PARAMETER_FPRS; // 0-8

    if (!method.isStatic()) {
      if (gp > 0) {
	gp--;
	GPRs[GPR++] = VM_Reflection.unwrapObject(thisArg);
      }
      Parameters[parameter++] = VM_Reflection.unwrapObject(thisArg);
    }

    VM_Type [] types = method.getParameterTypes();

    for (int i=0; i<types.length; i++) {
      VM_Type t = types[i];

      if (t.isLongType()) {
	long l = VM_Reflection.unwrapLong(otherArgs[i]);
	if (gp > 0) {
	  gp--;
	  GPRs[GPR++] = (int)(l>>>32);
	  if (gp > 0) {
	    gp--;
	    GPRs[GPR++] = (int)(l);
	  }
	}
	Parameters[parameter++] = (int)(l>>>32);
	Parameters[parameter++] = (int)l;

      } else if (t.isFloatType()) {
	if (fp > 0) {
	  fp--;
	  FPRs[--FPR] = VM_Reflection.unwrapFloat(otherArgs[i]);
	}
	float f = VM_Reflection.unwrapFloat(otherArgs[i]);
	Parameters[parameter++] = Float.floatToIntBits(f);

      } else if (t.isDoubleType()) {
	if (fp > 0) {
	  fp--;
	  FPRs[--FPR] = VM_Reflection.unwrapDouble(otherArgs[i]);
	}
	double d = VM_Reflection.unwrapDouble(otherArgs[i]);
	long l = Double.doubleToLongBits(d);
	Parameters[parameter++] = (int)(l>>>32);
	Parameters[parameter++] = (int)l;

      } else if (t.isBooleanType()) {
	if (gp > 0) {
	  gp--;
	  GPRs[GPR++] = VM_Reflection.unwrapBooleanAsInt(otherArgs[i]);
	}
	Parameters[parameter++] = VM_Reflection.unwrapBooleanAsInt(otherArgs[i]);

      } else if (t.isByteType()) {
	if (gp > 0) {
	  gp--;
	  GPRs[GPR++] = (int) VM_Reflection.unwrapByte(otherArgs[i]);
	}
	Parameters[parameter++] = (int) VM_Reflection.unwrapByte(otherArgs[i]);

      } else if (t.isCharType()) {
	if (gp > 0) {
	  gp--;
	  GPRs[GPR++] = (int) VM_Reflection.unwrapChar(otherArgs[i]);
	}
	Parameters[parameter++] = (int) VM_Reflection.unwrapChar(otherArgs[i]);

      } else if (t.isShortType()) {
	if (gp > 0) {
	  gp--;
	  GPRs[GPR++] = (int) VM_Reflection.unwrapShort(otherArgs[i]);
	}
	Parameters[parameter++] = (int) VM_Reflection.unwrapShort(otherArgs[i]);

      } else if (t.isIntType()) {
	if (gp > 0) {
	  gp--;
	  GPRs[GPR++] = VM_Reflection.unwrapInt(otherArgs[i]);
	}
	Parameters[parameter++] = VM_Reflection.unwrapInt(otherArgs[i]);

      } else if (!t.isPrimitiveType()) {
	if (gp > 0) {
	  gp--;
	  GPRs[GPR++] = VM_Reflection.unwrapObject(otherArgs[i]);
	}
	Parameters[parameter++] = VM_Reflection.unwrapObject(otherArgs[i]);

      } else  {
	if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
      }
    }
  }

}
