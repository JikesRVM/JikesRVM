/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

/**
 * Encoding of try ranges in the final machinecode and the
 * corresponding exception type and catch block start.
 *
 * @author Dave Grove
 * @author Mauricio Serrano
 */
final class VM_OptExceptionTable implements VM_Constants {

  /**
   * The eTable array encodes the exception tables using 4 ints for each
   */
  private int[] eTable;
  private static final int TRY_START = 0;
  private static final int TRY_END = 1;
  private static final int CATCH_START = 2;
  private static final int EX_TYPE = 3;

  /**
   * Return the machine code offset for the catch block that will handle
   * the argument exceptionType,or -1 if no such catch block exists.
   * 
   * @param instructionOffset the offset of the instruction after the PEI.
   * @param exceptionType the type of exception that was raised
   * @return the machine code offset of the catch block.
   */
  public int findCatchBlockForInstruction(int instructionOffset, 
					  VM_Type exceptionType) {
    for (int i = 0, n = eTable.length; i < n; i += 4) {
      // note that instructionOffset points to the instruction after the PEI
      // so the range check here must be "offset >  beg && offset <= end"
      // and not                         "offset >= beg && offset <  end"
      //
      // offset starts are sorted by starting point
      if (instructionOffset > eTable[i + TRY_START] &&
	  instructionOffset <= eTable[i + TRY_END]) {
	VM_Type lhs = VM_TypeDictionary.getValue(eTable[i + EX_TYPE]);
	if (lhs == exceptionType) {
	  return eTable[i + CATCH_START];
	} else if (lhs.isInitialized()) {
	  if (VM.BuildForFastDynamicTypeCheck) {
	    Object[] rhsTIB = exceptionType.getTypeInformationBlock();
	    if (VM_DynamicTypeCheck.instanceOfClass(lhs.asClass(), rhsTIB)) {
	      return eTable[i + CATCH_START];
	    }
	  } else {
	    try {
	      if (VM_Runtime.isAssignableWith(lhs, exceptionType)) {
		return eTable[i + CATCH_START];
	      }
	    } catch (VM_ResolutionException e) {
	      // cannot be thrown since lhs and rhs are initialized 
	      // thus no classloading will be performed
	    }
	  }
	}
      }
    }
    return  -1;
  }


  /**
   * Construct the exception table for an IR.
   */
  VM_OptExceptionTable(OPT_IR ir) {
    int index = 0;
    int tableSize = countExceptionTableSize(ir);
    eTable = new int[tableSize*4];
    for (OPT_BasicBlock bblock = ir.firstBasicBlockInCodeOrder(); 
	 bblock != null; 
	 bblock = bblock.nextBasicBlockInCodeOrder()) {
      int startOff = bblock.firstInstruction().getmcOffset();
      int endOff = bblock.lastInstruction().getmcOffset();
      if (endOff > startOff) {
        if (bblock.hasExceptionHandlers()) {
          for (OPT_BasicBlockEnumeration e = bblock.getReachableExceptionHandlers(); 
	       e.hasMoreElements();) {
            OPT_ExceptionHandlerBasicBlock eBlock = 
	      (OPT_ExceptionHandlerBasicBlock)e.nextElement();
	  loop: for (java.util.Enumeration ets = eBlock.getExceptionTypes(); 
		     ets.hasMoreElements();) {
	      OPT_TypeOperand type = (OPT_TypeOperand)ets.nextElement();
	      int catchOffset = eBlock.firstInstruction().getmcOffset();
	      int eId = type.type.getDictionaryId();
	      loop2: for (int i = index - 4; i >= 0; i -= 4) {
		int prevStart = eTable[i + TRY_START];
		int prevEnd = eTable[i + TRY_END];
		int prevCatch = eTable[i + CATCH_START];
		int prevEId = eTable[i + EX_TYPE];
		if ((prevStart == startOff) && (prevEnd == endOff) && 
		    (prevEId == eId)) {
		  continue loop;    // skip this entry because it's useless
		}
		if ((prevEnd == startOff) && (prevCatch == catchOffset) && 
		    (prevEId == eId)) {
		  eTable[i + TRY_END] = endOff;
		  continue loop;   // skip this entry, merging with previous
		}
		if (prevStart <= endOff && prevEnd >= startOff) {
		  break loop2; // stop trying to combine; it's getting tricky
		}
	      }
	      eTable[index + TRY_START] = startOff;
	      eTable[index + TRY_END] = endOff;
              eTable[index + CATCH_START] = catchOffset;
	      eTable[index + EX_TYPE] = eId;
              index += 4;
            }
          }
        }
      }
    }
    if (index != eTable.length) {              // resize arrays
      int[] newETable = new int[index];
      for (int i = 0; i <index; i++)
        newETable[i] = eTable[i];
      eTable = newETable;
    }
  }


  /**
   * Print the exception table.
   */
  void printExceptionTable () {
    int length = eTable.length;
    System.out.println("Exception Table:");
    System.out.println("    trystart   tryend    catch    type");
    for (int i = 0; i<length; i+=4) {
      System.out.print("    " + 
		       getHexString(eTable[i + TRY_START], true) + " "+
		       getHexString(eTable[i + TRY_END], true) + " " + 
		       getHexString(eTable[i + CATCH_START], true) + " " +
		       VM_TypeDictionary.getValue(eTable[i + EX_TYPE]));
      System.out.println();
    }
  }


  /**
   * Utility printing function.
   * @param i
   * @param blank
   * @return 
   */
  static String getHexString(int i, boolean blank) {
    StringBuffer buf = new StringBuffer(8);
    for (int j = 0; j < 8; j++, i <<= 4) {
      int n = i >>> 28;
      if (blank && (n == 0) && (j != 7)) {
        buf.append(' ');
      } else {
        buf.append(Character.forDigit(n, 16));
        blank = false;
      }
    }
    return buf.toString();
  }
  

  /**
   * Return an upper bounds on the size of the exception table for an IR.
   */
  private int countExceptionTableSize(OPT_IR ir) {
    int tSize = 0;
    for (OPT_BasicBlock bblock = ir.firstBasicBlockInCodeOrder(); 
	 bblock != null; 
	 bblock = bblock.nextBasicBlockInCodeOrder()) {
      if (bblock.hasExceptionHandlers()) {
        for (OPT_BasicBlockEnumeration e = 
	       bblock.getReachableExceptionHandlers(); e.hasMoreElements();) {
	  OPT_ExceptionHandlerBasicBlock ebb = 
	    (OPT_ExceptionHandlerBasicBlock)e.next();
          tSize += ebb.getNumberOfExceptionTableEntries();
        }
      }
    }
    return tSize;
  }
}



