/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Class that supports scanning Objects or Arrays for references
 * during Collection and processing those references
 *
 * @author Stephen Smith
 */  
public class VM_ScanObject
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible {

  /**
   * Scans an object or array for internal object references and
   * processes those references (calls processPtrField)
   *
   * @param objRef  reference for object to be scanned (as int)
   */
  static void
  scanObjectOrArray ( int objRef ) {
    VM_Type    type;
    
    // First process the TIB to relocate it.
    // Necessary only if the allocator/collector moves objects
    // and the object model is actually storing the TIB as a pointer.
    // 
    if (VM_Allocator.movesObjects) {
      VM_ObjectModel.gcProcessTIB(objRef);
    }

    type = VM_Magic.getObjectType(VM_Magic.addressAsObject(objRef));
    if ( type.isClassType() ) {
      int[] referenceOffsets = type.asClass().getReferenceOffsets();
      for(int i = 0, n=referenceOffsets.length; i < n; i++) {
	VM_Allocator.processPtrField( objRef + referenceOffsets[i] );
      }
    }
    else {
      if (VM.VerifyAssertions) VM.assert(type.isArrayType());
      VM_Type elementType = type.asArray().getElementType();
      if (elementType.isReferenceType()) {
	int num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(objRef));
	int location = objRef;    // for arrays = address of [0] entry
	int end      = objRef + num_elements * WORDSIZE;
	while ( location < end ) {
	  VM_Allocator.processPtrField( location );
	  location = location + WORDSIZE;  // is this size_of_pointer ?
	}
      }
    }
  } 

  static void
  scanObjectOrArray ( Object objRef ) {
    scanObjectOrArray( VM_Magic.objectAsAddress(objRef) );
  }

  public static boolean
  validateRefs ( int ref, int depth ) {
    VM_Type    type;

    if ( ref == 0 ) return true;   // null is always valid

    // First check passed ref, before looking into it for refs
    if ( !VM_GCUtil.validRef(ref) ) {
      VM.sysWrite("ScanObject.validateRefs: Bad Ref = ");
      VM_GCUtil.dumpRef( ref );
      VM_GCUtil.dumpMemoryWords( ref - 16*WORDSIZE, 32 );  // dump 16 words on either side of bad ref
      return false;
    }

    if (depth==0) return true;  //this ref valid, stop depth first scan

    type = VM_Magic.getObjectType(VM_Magic.addressAsObject(ref));
    if ( type.isClassType() ) {
      int[] referenceOffsets = type.asClass().getReferenceOffsets();
      for (int i = 0, n=referenceOffsets.length; i < n; i++) {
	int iref = VM_Magic.getMemoryWord(ref + referenceOffsets[i]);
	if ( ! validateRefs( iref, depth-1 ) ) {
	  VM.sysWrite("Referenced from Object: Ref = ");
	  VM_GCUtil.dumpRef( ref );
	  VM.sysWrite("                  At Offset = ");
	  VM.sysWrite(referenceOffsets[i],false);
	  VM.sysWrite("\n");
	  return false;
	}
      }
    }
    else {
      VM_Type elementType = type.asArray().getElementType();
      if (elementType.isReferenceType()) {
	int num_elements = VM_Magic.getArrayLength(VM_Magic.addressAsObject(ref));
	int location = 0;    // for arrays = offset of [0] entry
	int end      = num_elements * 4;
	for ( location = 0; location < end; location += 4 ) {
	  int iref = VM_Magic.getMemoryWord(ref + location);
	  if ( ! validateRefs( iref, depth-1 ) ) {
	    VM.sysWrite("Referenced from Array: Ref = ");
	    VM_GCUtil.dumpRef( ref );
	    VM.sysWrite("                  At Index = ");
	    VM.sysWrite((location>>2),false);
	    VM.sysWrite("              Array Length = ");
	    VM.sysWrite(num_elements,false);
	    VM.sysWrite("\n");
	    return false;
	  }
	}
      }
    } 
    return true;
  } // validateRefs

}   // VM_ScanObject
