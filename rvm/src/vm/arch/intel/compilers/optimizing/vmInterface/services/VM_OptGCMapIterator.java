/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

/**
 * An instance of this class provides iteration across the references 
 * represented by a frame built by the OPT compiler.
 *
 * The architecture-specific version of the GC Map iterator.  It inherits
 * its architecture-independent code from VM_OptGenericGCMapIterator.
 * This version is for IA32
 *
 * @author Michael Hind
 */
final class VM_OptGCMapIterator extends VM_OptGenericGCMapIterator {

  // Constructor 
  VM_OptGCMapIterator(int[] registerLocations) {
    super(registerLocations);
  }

  /** 
   * If any non-volatile gprs were saved by the method being processed
   * then update the registerLocations array with the locations where the
   * registers were saved.  Also, check for special methods that also
   * save the volatile gprs.
   */
  void updateLocateRegisters() {

    //           HIGH MEMORY
    //
    //       +---------------+                                           |
    //  FP-> |   saved FP    |  <-- this frame's caller's frame          |
    //       +---------------+                                           |
    //       |    cmid       |  <-- this frame's compiledmethod id       |
    //       +---------------+                                           |
    //       |               |                                           |
    //       |  Spill Area   |  <-- spills and other method-specific     |
    //       |     ...       |      compiler-managed storage             |
    //       +---------------+                                           |
    //       |   Saved FP    |     only SaveVolatile Frames              |   
    //       |    State      |                                           |
    //       +---------------+                                           |
    //       |  VolGPR[0]    |                                           
    //       |     ...       |     only SaveVolatile Frames              
    //       |  VolGPR[n]    |                                           
    //       +---------------+                                           
    //       |  NVolGPR[k]   |  <-- info.getUnsignedNonVolatileOffset()  
    //       |     ...       |   k == info.getFirstNonVolatileGPR()      
    //       |  NVolGPR[n]   |                                           
    //       +---------------+                                           
    //
    //           LOW MEMORY
    
    int frameOffset = compilerInfo.getUnsignedNonVolatileOffset();
    if (frameOffset >= 0) {
      // get to the non vol area
      int nonVolArea = framePtr - frameOffset;
    
      // update non-volatiles
      int first = compilerInfo.getFirstNonVolatileGPR();
      if (first >= 0) {
	// move to the beginning of the nonVol area
	int location = nonVolArea;
	
	for (int i = first; i < NUM_NONVOLATILE_GPRS; i++) {
	  // determine what register index corresponds to this location
	  int registerIndex = NONVOLATILE_GPRS[i];
	  registerLocations[registerIndex] = location;
	  location -= 4;
	}
      }
      
      // update volatiles if needed
      if (compilerInfo.isSaveVolatile()) {
	// move to the beginning of the nonVol area
	int location = nonVolArea + (4 * NUM_VOLATILE_GPRS);
	
	for (int i = 0; i < NUM_VOLATILE_GPRS; i++) {
	  // determine what register index corresponds to this location
	  int registerIndex = VOLATILE_GPRS[i];
	  registerLocations[registerIndex] = location;
	  location -= 4;
	}
	
	// the scratch register is also considered a volatile, 
	// so it is already updated
      }
    }
  }

  /** 
   *  Determine the spill location given the frame ptr and spill offset.
   *  (The location of spills varies among architectures.)
   *  @param framePtr the frame pointer
   *  @param offset  the offset for the spill 
   *  @return the resulting spill location
   */
  int getStackLocation(int framePtr, int offset) {
    return framePtr - offset;
  }

  /** 
   *  Get address of the first spill location for the given frame ptr
   *  @param the frame pointer
   *  @return the first spill location
   */
  int getFirstSpillLoc() {
    return framePtr - (-VM.STACKFRAME_BODY_OFFSET);
  }

  /** 
   *  Get address of the last spill location for the given frame ptr
   *  @param the frame pointer
   *  @return the last spill location
   */
  int getLastSpillLoc() {
    if (compilerInfo.isSaveVolatile()) {
      return framePtr - compilerInfo.getUnsignedNonVolatileOffset() + 4 + SAVE_VOL_SIZE;
    } else {
      return framePtr - compilerInfo.getUnsignedNonVolatileOffset() + 4;
    }
  }

  final static int VOL_SIZE = 4 * NUM_VOLATILE_GPRS;
  final static int SAVE_VOL_SIZE = VOL_SIZE + VM.FPU_STATE_SIZE;

}
