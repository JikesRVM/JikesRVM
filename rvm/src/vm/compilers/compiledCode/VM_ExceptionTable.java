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
abstract class VM_ExceptionTable {

  /**
   * The eTable array encodes the exception tables using 4 ints for each
   */
  protected int[] eTable;
  protected static final int TRY_START = 0;
  protected static final int TRY_END = 1;
  protected static final int CATCH_START = 2;
  protected static final int EX_TYPE = 3;

  /**
   * Return the machine code offset for the catch block that will handle
   * the argument exceptionType,or -1 if no such catch block exists.
   * 
   * @param instructionOffset the offset of the instruction after the PEI.
   * @param exceptionType the type of exception that was raised
   * @return the machine code offset of the catch block.
   */
  public final int findCatchBlockForInstruction(int instructionOffset, 
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
   * Print the exception table.
   */
  public final void printExceptionTable () {
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

  private static final VM_Class TYPE = VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom("LVM_ExceptionTable;"), VM_SystemClassLoader.getVMClassLoader()).asClass();
  public final int size() {
    return TYPE.getInstanceSize() + VM_Array.arrayOfIntType.getInstanceSize(eTable.length);
  }
}



