/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * @author Julian Dolby
 * @date May 20, 2002
 */

class OPT_BytecodeInfoFactory {

    public static OPT_BytecodeInfo create(VM_Method method) {
	return new OPT_BytecodeInfo( method );
    }

}
