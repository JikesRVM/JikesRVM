/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Julian Dolby
 */

class OPT_SpecializationCodePatching implements VM_BaselineConstants {

    private static final int HIDDEN_STORE_OFFSET = -1;
    private static boolean DO_CACHE_SYNCS = true;

    static private void insertCallSiteNumber(INSTRUCTION[] codeArray,
					     int callInstructionIndex,
					     int callSiteNumber) {
	int hiddenStoreIndex = callInstructionIndex + HIDDEN_STORE_OFFSET;
	codeArray[hiddenStoreIndex] = VM_Assembler.LIL(0, callSiteNumber);
	if (DO_CACHE_SYNCS && VM.runningVM) {
	    int byteOffset = hiddenStoreIndex << LG_INSTRUCTION_WIDTH;
	    int address = VM_Magic.objectAsAddress(codeArray) + byteOffset;
	    VM_Memory.sync(address, LG_INSTRUCTION_WIDTH);
	}
    }
    
    static public void insertCallSiteNumber(INSTRUCTION[] codeArray,
					    VM_CompiledMethod codeInfo,
					    GNO_InstructionLocation call,
					    int callSiteNumber)
    {
	if ( codeInfo.getCompilerType() == VM_CompiledMethod.BASELINE ) {
	    VM_BaselineCompiledMethod info = (VM_BaselineCompiledMethod)codeInfo;
	    int bcIndex = call.getByteCodeOffset();
	    int mcOffset = info.findInstructionForBytecodeIndex( bcIndex );
	    insertCallSiteNumber( codeArray, mcOffset, callSiteNumber );

	} else if ( codeInfo.getCompilerType() == VM_CompiledMethod.OPT ) {
	    VM_OptCompiledMethod info = (VM_OptCompiledMethod) codeInfo;
	    VM_OptMachineCodeMap map = info.getMCMap();
	    int[] callSiteEncoding = map.inlineEncoding;
	    int bcIndex = call.getByteCodeOffset();
	    int mcOffset = map.getMCoffsetForBCindex( bcIndex );
	    while ( mcOffset != -1 ) {
		GNO_InstructionLocation pos =
		    new GNO_InstructionLocation(info, mcOffset);
		if (pos.equals(call)) {
		    insertCallSiteNumber(codeArray, mcOffset, callSiteNumber);
		    return;
		} else 
		    mcOffset = 
			map.getNextMCoffsetForBCindex( bcIndex, mcOffset );
	    }
	} 
    }
    
    static public void insertCallSiteNumber(VM_CompiledMethod code,
					    GNO_InstructionLocation call,
					    int callSiteNumber)
    {
	INSTRUCTION[] codeArray = code.getInstructions();

	insertCallSiteNumber(codeArray, code, call, callSiteNumber);
    }

}
