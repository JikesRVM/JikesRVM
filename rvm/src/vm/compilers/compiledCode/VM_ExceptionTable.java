/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

/**
 * Encoding of try ranges in the final machinecode and the
 * corresponding exception type and catch block start.
 *
 * @author Dave Grove
 * @author Mauricio Serrano
 */
public abstract class VM_ExceptionTable {

  /**
   * An eTable array encodes the exception tables using 4 ints for each
   */
  protected static final int TRY_START = 0;
  protected static final int TRY_END = 1;
  protected static final int CATCH_START = 2;
  protected static final int EX_TYPE = 3;

  /**
   * Return the machine code offset for the catch block that will handle
   * the argument exceptionType,or -1 if no such catch block exists.
   *
   * @param eTable the encoded exception table to search
   * @param instructionOffset the offset of the instruction after the PEI.
   * @param exceptionType the type of exception that was raised
   * @return the machine code offset of the catch block.
   */
  public static final int findCatchBlockForInstruction(int[] eTable,
                                                       int instructionOffset, 
                                                       VM_Type exceptionType) {
    for (int i = 0, n = eTable.length; i < n; i += 4) {
      // note that instructionOffset points to the instruction after the PEI
      // so the range check here must be "offset >  beg && offset <= end"
      // and not                         "offset >= beg && offset <  end"
      //
      // offset starts are sorted by starting point
      if (instructionOffset > eTable[i + TRY_START] &&
          instructionOffset <= eTable[i + TRY_END]) {
        VM_Type lhs = VM_Type.getType(eTable[i + EX_TYPE]);
        if (lhs == exceptionType) {
          return eTable[i + CATCH_START];
        } else if (lhs.isInitialized()) {
          Object[] rhsTIB = exceptionType.getTypeInformationBlock();
          if (VM_DynamicTypeCheck.instanceOfClass(lhs.asClass(), rhsTIB)) {
            return eTable[i + CATCH_START];
          }
        }
      }
    }
    return -1;
  }

  /**
   * Print an encoded exception table.
   * @param eTable the encoded exception table to print.
   */
  public static final void printExceptionTable (int[] eTable) {
    int length = eTable.length;
    VM.sysWriteln("Exception Table:");
    VM.sysWriteln("    trystart   tryend    catch    type");
    for (int i = 0; i<length; i+=4) {
      VM.sysWriteln("    " + 
                    VM_Services.getHexString(eTable[i + TRY_START], true) + " "+
                    VM_Services.getHexString(eTable[i + TRY_END], true) + " " + 
                    VM_Services.getHexString(eTable[i + CATCH_START], true) + "    " +
                    VM_Type.getType(eTable[i + EX_TYPE]));
    }
  }
}



