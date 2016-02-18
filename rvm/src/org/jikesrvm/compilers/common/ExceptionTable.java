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
package org.jikesrvm.compilers.common;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.DynamicTypeCheck;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.util.Services;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Offset;

/**
 * Encoding of try ranges in the final machinecode and the
 * corresponding exception type and catch block start.
 */
public abstract class ExceptionTable {

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
  @Unpreemptible
  public static int findCatchBlockForInstruction(int[] eTable, Offset instructionOffset, RVMType exceptionType) {
    for (int i = 0, n = eTable.length; i < n; i += 4) {
      // note that instructionOffset points to the instruction after the PEI
      // so the range check here must be "offset >  beg && offset <= end"
      // and not                         "offset >= beg && offset <  end"
      //
      // offset starts are sorted by starting point
      if (instructionOffset.sGT(Offset.fromIntSignExtend(eTable[i + TRY_START])) &&
          instructionOffset.sLE(Offset.fromIntSignExtend(eTable[i + TRY_END]))) {
        RVMType lhs = RVMType.getType(eTable[i + EX_TYPE]);
        if (lhs == exceptionType) {
          return eTable[i + CATCH_START];
        } else if (lhs.isInitialized()) {
          TIB rhsTIB = exceptionType.getTypeInformationBlock();
          if (DynamicTypeCheck.instanceOfClass(lhs.asClass(), rhsTIB)) {
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
  public static void printExceptionTable(int[] eTable) {
    writeExceptionTableHeader();
    int length = eTable.length;
    for (int i = 0; i < length; i += 4) {
      printNicelyFormattedAndInterruptible(eTable, i);
    }
  }

  @Uninterruptible
  private static void writeExceptionTableHeader() {
    VM.sysWriteln("Exception Table:");
    VM.sysWriteln("    trystart   tryend    catch    type");
  }

  private static void printNicelyFormattedAndInterruptible(int[] eTable, int i) {
    VM.sysWriteln("    " +
                  Services.getHexString(eTable[i + TRY_START], true) +
                  " " +
                  Services.getHexString(eTable[i + TRY_END], true) +
                  " " +
                  Services.getHexString(eTable[i + CATCH_START], true) +
                  "    " +
                  RVMType.getType(eTable[i + EX_TYPE]));
  }


  /**
   * Prints an exception table.
   * <p>
   * This method does the same thing as {@link #printExceptionTable(int[])} but
   * with less nicely formatted output because of the constraints imposed by
   * the requirements for uninterruptible code.
   *
   * @param eTable the exception table to print
   */
  @Uninterruptible
  public static void printExceptionTableUninterruptible(int[] eTable) {
    writeExceptionTableHeader();
    int length = eTable.length;
    for (int i = 0; i < length; i += 4) {
      printLessNicelyFormattedAndUninterruptible(eTable, i);
    }
  }

  @Uninterruptible
  private static void printLessNicelyFormattedAndUninterruptible(int[] eTable,
      int i) {
    VM.sysWrite("    ");
    VM.sysWriteHex(eTable[i + TRY_START]);
    VM.sysWrite(" ");
    VM.sysWriteHex(eTable[i + TRY_END]);
    VM.sysWrite(" ");
    VM.sysWriteHex(eTable[i + CATCH_START]);
    VM.sysWrite("    ");
    VM.sysWrite(RVMType.getType(eTable[i + EX_TYPE]).getDescriptor());
    VM.sysWriteln();
  }
}



