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

  private static final boolean DEBUG = false;
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
    int currStartOff, currEndOff;
    int tableSize = countExceptionTableSize(ir);
    eTable = new int[tableSize*4];
      

    // For each basic block
    //   See if it has code associated with it and if it has
    //   any reachable exception handlers.
    //   When such a block is found, check the blocks that follow
    //   it in code order to see if this block has the same
    //   Bag of exceptionHandlers as any of its immediate successors.
    //   If so the try region can be expanded to include those
    //   successors. Stop checking successors as soon as a non-match
    //   is found, or a block that doesn't have handlers is found.
    //   Successors that don't have any code associated with them can
    //   be ignored. 
    //   If blocks were joined together then when adding the
    //   entries to the eTable it is important to not restrict the
    //   entries to reachable handlers; as the first block may only
    //   throw a subset of the exception types represented by the Bag
    for (OPT_BasicBlock bblock = ir.firstBasicBlockInCodeOrder(); 
	 bblock != null; ) {
      // Iteration is explicit in loop

      int startOff = bblock.firstInstruction().getmcOffset();
      int endOff = bblock.lastInstruction().getmcOffset();
      if (endOff > startOff) {
	if (!bblock.hasExceptionHandlers()) {
	  bblock = bblock.nextBasicBlockInCodeOrder();
	  continue;
	}

	OPT_BasicBlock followonBB;
	OPT_BasicBlockEnumeration reachBBe, e;
	boolean joinedBlocks;

	// First make sure at least one of the exception handlers
	// is reachable from this block
	reachBBe = bblock.getReachableExceptionHandlers(); 
	if (!reachBBe.hasMoreElements()) {
	  bblock = bblock.nextBasicBlockInCodeOrder();
	  continue;
	}
	  
	currStartOff = startOff;
	currEndOff = endOff;
	joinedBlocks = false;

	for (followonBB = bblock.nextBasicBlockInCodeOrder();
	     followonBB != null;
	     followonBB = followonBB.nextBasicBlockInCodeOrder()) {
	  int fStartOff = followonBB.firstInstruction().getmcOffset();
	  int fEndOff = followonBB.lastInstruction().getmcOffset();
	  // See if followon Block has any code
	  if (fEndOff > fStartOff) {
	    // See if followon Block has matching handler block bag
	    if (followonBB.hasExceptionHandlers() &&
		bblock.isExceptionHandlerEquivalent(followonBB)) {
	      currEndOff = fEndOff;
	      joinedBlocks = true;
	    } else {
	      // Can't join any more blocks together
	      break;
	    }
	  }
	}
	// found all the matching followon blocks 
	// Now fill in the eTable with the handlers
	if (joinedBlocks)
	  e = bblock.getExceptionHandlers();
	else
	  e = reachBBe;
	
	for ( ; e.hasMoreElements();) {
	  OPT_ExceptionHandlerBasicBlock eBlock = 
	    (OPT_ExceptionHandlerBasicBlock)e.nextElement();
	  for (java.util.Enumeration ets = eBlock.getExceptionTypes(); 
	       ets.hasMoreElements();) {
	    OPT_TypeOperand type = (OPT_TypeOperand)ets.nextElement();
	    int catchOffset = eBlock.firstInstruction().getmcOffset();
	    int eId = type.type.getDictionaryId();
	    eTable[index + TRY_START] = currStartOff;
	    eTable[index + TRY_END] = currEndOff;
	    eTable[index + CATCH_START] = catchOffset;
	    eTable[index + EX_TYPE] = eId;
	    index += 4;
	  }
	}
	
	bblock = followonBB;
      
      } else // No code in bblock
	bblock = bblock.nextBasicBlockInCodeOrder();
    }

    if (index != eTable.length) {              // resize array
      int[] newETable = new int[index];
      for (int i = 0; i <index; i++)
        newETable[i] = eTable[i];
      eTable = newETable;
    }
    if (DEBUG) {
      VM.sysWrite("eTable length: " + eTable.length + "\n");
      printExceptionTable();
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
		       VM_Services.getHexString(eTable[i + TRY_START], true) + " "+
		       VM_Services.getHexString(eTable[i + TRY_END], true) + " " + 
		       VM_Services.getHexString(eTable[i + CATCH_START], true) + " " +
		       VM_TypeDictionary.getValue(eTable[i + EX_TYPE]));
      System.out.println();
    }
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



