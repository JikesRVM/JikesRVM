/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Machine dependent portion of Reflective method invoker.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 * @date 15 Jul 1998 
 */
public class VM_MachineReflection 
   implements VM_BaselineConstants
   {
   //-----------//
   // interface //
   //-----------//
   

   //----------------//
   // implementation //
   //----------------//
   
   // Determine number/type of registers/spills required to call specified method.
   // See also: VM_Compiler.loadParameters()
   //
   static int 
   countParameters(VM_Method method) {
     int GPRs   = 0;
     int FPRs   = 0;
     int Spills = 0;
     int gp = FIRST_VOLATILE_GPR;
     int fp = FIRST_VOLATILE_FPR;
     if (!method.isStatic()) {
       if (gp > LAST_VOLATILE_GPR) Spills++;
       else {GPRs++; gp++;}
     }
     VM_Type [] types = method.getParameterTypes();
     for (int i=0; i<types.length; i++) {
       VM_Type t = types[i];
       if (t.isLongType()) {
         if (gp > LAST_VOLATILE_GPR) Spills+=2;
         else {
           {GPRs++; gp++;}
           if (gp > LAST_VOLATILE_GPR) Spills++;
           else {GPRs++; gp++;}
         }
       } else if (t.isFloatType()) {
         if (fp > LAST_VOLATILE_FPR) Spills++;
         else {FPRs++; fp++;}
       } else if (t.isDoubleType()) {
         if (fp > LAST_VOLATILE_FPR) Spills+=2;
         else {FPRs++; fp++;}
       } else { // t is object, int, short, char, byte, or boolean
         if (gp > LAST_VOLATILE_GPR) Spills++;
         else {GPRs++; gp++;}
       }
     }

     // spills[] carries burden of aligning stack frame
     int frameSize  = (Spills << 2)           // spill area
                    + STACKFRAME_HEADER_SIZE; // header
     frameSize = (frameSize + STACKFRAME_ALIGNMENT_MASK) & ~STACKFRAME_ALIGNMENT_MASK;
     Spills = (frameSize-STACKFRAME_HEADER_SIZE) >> 2;        

     // hack to return triple
     return (Spills<<(REFLECTION_FPRS_BITS+REFLECTION_GPRS_BITS)) |
            (FPRs<<REFLECTION_GPRS_BITS) | GPRs;
   }

   // Collect parameters into arrays of registers/spills, as required to call specified method.
   //
   static void 
   packageParameters(VM_Method method, Object thisArg, Object[] otherArgs,
                  int[] GPRs, double[] FPRs, int[] Spills) {
     int GPR   = GPRs.length;
     int FPR   = FPRs.length;
     int Spill = Spills.length;
     int gp = FIRST_VOLATILE_GPR;
     int fp = FIRST_VOLATILE_FPR;
     if (!method.isStatic()) {
       if (gp > LAST_VOLATILE_GPR)
         Spills[--Spill] = VM_Reflection.unwrapObject(thisArg);
       else {
         gp++;
         GPRs[--GPR] = VM_Reflection.unwrapObject(thisArg);
       }
     }
     VM_Type [] types = method.getParameterTypes();
     for (int i=0; i<types.length; i++) {
       VM_Type t = types[i];
       if (t.isLongType()) {
         long l = VM_Reflection.unwrapLong(otherArgs[i]);
         if (gp > LAST_VOLATILE_GPR) {
           Spills[--Spill] = (int)(l>>>32);
           Spills[--Spill] = (int)l;
         } else {
           gp++;
           GPRs[--GPR] = (int)(l>>>32);
           if (gp > LAST_VOLATILE_GPR) Spills[--Spill] = (int)(l);
           else {
             gp++;
             GPRs[--GPR] = (int)(l);
           }
         }
       } else if (t.isFloatType()) {
         if (fp > LAST_VOLATILE_FPR) {
           float f = VM_Reflection.unwrapFloat(otherArgs[i]);
           Spills[--Spill] = Float.floatToIntBits(f);
         } else {
           fp++;
           FPRs[--FPR] = VM_Reflection.unwrapFloat(otherArgs[i]);
         }
       } else if (t.isDoubleType()) {
         if (fp > LAST_VOLATILE_FPR) {
           double d = VM_Reflection.unwrapDouble(otherArgs[i]);
           long l = Double.doubleToLongBits(d);
           Spills[--Spill] = (int)(l>>>32);
           Spills[--Spill] = (int)l;
         } else {
           fp++;
           FPRs[--FPR] = VM_Reflection.unwrapDouble(otherArgs[i]);
         }
       } else if (t.isBooleanType()) {
         if (gp > LAST_VOLATILE_GPR)
           Spills[--Spill] = VM_Reflection.unwrapBooleanAsInt(otherArgs[i]);
         else {
           gp++;
           GPRs[--GPR] = VM_Reflection.unwrapBooleanAsInt(otherArgs[i]);
         }
       } else if (t.isByteType()) {
         if (gp > LAST_VOLATILE_GPR)
           Spills[--Spill] = (int) VM_Reflection.unwrapByte(otherArgs[i]);
         else {
           gp++;
           GPRs[--GPR] = (int) VM_Reflection.unwrapByte(otherArgs[i]);
         }
       } else if (t.isCharType()) {
         if (gp > LAST_VOLATILE_GPR)
           Spills[--Spill] = (int) VM_Reflection.unwrapChar(otherArgs[i]);
         else {
           gp++;
           GPRs[--GPR] = (int) VM_Reflection.unwrapChar(otherArgs[i]);
         }
       } else if (t.isShortType()) {
         if (gp > LAST_VOLATILE_GPR)
           Spills[--Spill] = (int) VM_Reflection.unwrapShort(otherArgs[i]);
         else {
           gp++;
           GPRs[--GPR] = (int) VM_Reflection.unwrapShort(otherArgs[i]);
         }
       } else if (t.isIntType()) {
         if (gp > LAST_VOLATILE_GPR)
           Spills[--Spill] = VM_Reflection.unwrapInt(otherArgs[i]);
         else {
           gp++;
           GPRs[--GPR] = VM_Reflection.unwrapInt(otherArgs[i]);
         }
       } else if (!t.isPrimitiveType()) {
         if (gp > LAST_VOLATILE_GPR)
           Spills[--Spill] = VM_Reflection.unwrapObject(otherArgs[i]);
         else {
           gp++;
           GPRs[--GPR] = VM_Reflection.unwrapObject(otherArgs[i]);
         }
       } else  {
         if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
       }
     }
   }

   }
