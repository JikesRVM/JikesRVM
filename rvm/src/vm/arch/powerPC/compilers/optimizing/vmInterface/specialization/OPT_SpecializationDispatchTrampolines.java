/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Julian Dolby
 */
class OPT_SpecializationDispatchTrampolines implements VM_BaselineConstants {
    
    static private INSTRUCTION[] generate(OPT_ConcreteMethodKey key) {
	int codeMapOffset =  OPT_SpecializationManager.getJTOCoffset(key);
	if (OPT_SpecializationManager.DEBUG)
	    VM.sysWrite("replacing " + key.getMethod() + " of " + key.getType() + " with table in JTOC entry " + codeMapOffset + "\n");

	VM_Assembler code = new VM_Assembler(5);
	code.emitLtoc(S0, codeMapOffset << 2);
	code.emitLX(SP, S0, 0);
	code.emitMTCTR(SP);       
	code.emitBCTR();
	code.mc.finish();
	return code.mc.getInstructions();
    }

    static void insert(OPT_ConcreteMethodKey key) {
	INSTRUCTION[] code = generate(key);
	VM_Method method = key.getMethod();
	// the code in VM_Method does this >>> 2.  Why?
	int offset = method.getOffset() >>> 2;
	if ( method.isStatic() || 
	     method.isObjectInitializer() || 
	     method.isClassInitializer()) 
	{
	    VM_Statics.setSlotContents( offset, code );
	} else if (key.getType() instanceof VM_Class)
	    ((VM_Class)key.getType()).resetTIBEntry(method, code);
	else {
	    // arrays
	    VM_Type receiver = key.getType();
	    Object[] tib = receiver.getTypeInformationBlock();
	    tib[ offset ] = code;
	}
    }
}
