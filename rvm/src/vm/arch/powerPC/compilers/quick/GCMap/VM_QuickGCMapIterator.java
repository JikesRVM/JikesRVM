/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.quick;

import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_GCMapIterator;
import com.ibm.JikesRVM.classloader.*;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Iterator for stack frame  built by the Quick compiler
 * An Instance of this class will iterate through a particular 
 * reference map of a method returning the offsets of any refereces
 * that are part of the input parameters, local variables, and 
 * java stack for the stack frame.
 *
 * Based on VM_GCMapIterator
 *
 * @author Chris Hoffmann
 */
public final class VM_QuickGCMapIterator extends VM_GCMapIterator 
  implements VM_BaselineConstants, VM_QuickConstants,
             Uninterruptible  {

  // Iterator state for mapping any stackframe.
  //
  private   int              mapIndex; // current index in current map
  private   int              mapId;     // id of current map out of all maps
  private   VM_QuickReferenceMaps maps;      // set of maps for this method

  private boolean checkSavedThisPtr;

  private static final int CHECK_REFERENCE_STATE        = 0;
  private static final int CHECK_THIS_PTR_STATE         = 1;
  private static final int CHECK_BRIDGE_REFERENCE_STATE = 2;
  private static final int CHECK_COMPLETE_STATE         = 3;
  private int     state;
  
  // Additional iterator state for mapping dynamic bridge stackframes.
  //
  private VM_DynamicLink dynamicLink;                    // place to keep info returned by VM_CompiledMethod.getDynamicLink
  private VM_Method      bridgeTarget;                   // method to be invoked via dynamic bridge (null: current frame is not a dynamic bridge)
  private VM_NormalMethod currentMethod;                  // method for the frame
  private VM_TypeReference[]      bridgeParameterTypes;           // parameter types passed by that method
  private boolean        bridgeParameterMappingRequired; // have all bridge parameters been mapped yet?
  private boolean        bridgeRegistersLocationUpdated; // have the register location been updated
  private int            bridgeParameterInitialIndex;    // first parameter to be mapped (-1 == "this")
  private int            bridgeParameterIndex;           // current parameter being mapped (-1 == "this")
  private int            bridgeRegisterIndex;            // gpr register it lives in
  private Address     bridgeRegisterLocation;         // memory address at which that register was saved


  //
  // Remember the location array for registers. This array needs to be updated
  // with the location of any saved registers.
  // This information is not used by this iterator but must be updated for the
  // other types of iterators (ones for the quick and opt compiler built frames)
  // The locations are kept as addresses within the stack.
  //
  public VM_QuickGCMapIterator(WordArray registerLocations) {
    this.registerLocations = registerLocations; // (in superclass)
    //dynamicLink  = new VM_DynamicLink();
  }

  //
  // Set the iterator to scan the map at the machine instruction offset provided.
  // The iterator is positioned to the beginning of the map
  //
  //   method - identifies the method and class
  //   instruction offset - identifies the map to be scanned.
  //   fp  - identifies a specific occurrance of this method and
  //         allows for processing instance specific information
  //         i.e JSR return address values
  //
  //  NOTE: An iterator may be reused to scan a different method and map.
  //
  public void setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, Address fp) {
    currentMethod = (VM_NormalMethod)compiledMethod.getMethod();


    // setup superclass
    //
    framePtr = fp;
      
    // setup stackframe mapping
    //
    maps      = ((VM_QuickCompiledMethod)compiledMethod).referenceMaps;
    mapId     = maps.locateGCPoint(instructionOffset, currentMethod);
    mapIndex = VM_QuickReferenceMaps.STARTINDEX;
    if (mapId < 0) {
      // lock the jsr lock to serialize jsr processing
      VM_QuickReferenceMaps.jsrLock.lock();
      maps.setupJSRSubroutineMap( framePtr, mapId, registerLocations, compiledMethod);
    }

    // setup dynamic bridge mapping
    //
    bridgeTarget                   = null;
    bridgeParameterTypes           = null;
    bridgeParameterMappingRequired = false;
    bridgeRegistersLocationUpdated = false;
    bridgeParameterIndex           = 0;
    bridgeRegisterIndex            = 0;
    bridgeRegisterLocation         = Address.zero();

    if (currentMethod.getDeclaringClass().isDynamicBridge()) {
      if (VM.VerifyAssertions) VM._assert(NOT_REACHED);  
      
//       fp                       = VM_Magic.getCallerFramePointer(fp);
//       Address        ip                       = VM_Magic.getNextInstructionAddress(fp);
//       int               callingCompiledMethodId  = VM_Magic.getCompiledMethodID(fp);
//       VM_CompiledMethod callingCompiledMethod    = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
//       int               callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

//       callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);
//       bridgeTarget                = dynamicLink.methodRef().getResolvedMember();
//       bridgeParameterInitialIndex = dynamicLink.isInvokedWithImplicitThisParameter() ? -1 : 0;
//       bridgeParameterTypes        = bridgeTarget.getParameterTypes();
    }
        
    reset();
  }
  
  // Reset iteration to initial state.
  // This allows a map to be scanned multiple times
  //
  public void reset() {

    mapIndex = VM_QuickReferenceMaps.STARTINDEX;
    state = CHECK_REFERENCE_STATE;
    checkSavedThisPtr = true;

    if (bridgeTarget != null) {
      // point to first saved gpr
      bridgeParameterMappingRequired = true;
      bridgeParameterIndex   = bridgeParameterInitialIndex;
      bridgeRegisterIndex    = FIRST_VOLATILE_GPR;
      bridgeRegisterLocation = framePtr.loadAddress();
      bridgeRegisterLocation = bridgeRegisterLocation.sub(8 * (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) +
                                                          4 * (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1));
    }
  }

  // Get location of next reference.
  // A zero return indicates that no more references exist.
  //
  public Address getNextReferenceAddress() {

    int location = -1;
    
    if (state == CHECK_REFERENCE_STATE) {
      // Loop through all slots that the simple analysis of the
      // bytecodes has determined will contain object references. Ask
      // the reference map instance for the location of that slot in
      // register or memory. The special value EMPTY_SLOT indicates
      // the quick compiler decided it did not actually need to store
      // anything at that slot at this point in execution.
      do {
        if (mapId < 0)
          mapIndex = maps.getNextJSRRefIndex(mapIndex);
        else
          mapIndex = maps.getNextRefIndex(mapIndex, mapId);
        if (mapIndex == VM_QuickReferenceMaps.NOMORE) {
          state += 1;
          break;
        } else {
          location = maps.getLocation(mapId, mapIndex);
        }
      } while (location == EMPTY_SLOT);

      if (location == INVALID_SLOT) {
        VM.sysWrite("VM_QuickGCMapIterator getNextReferenceAddress: bad index = ");
        VM.sysWrite(mapIndex);
        VM.sysWrite(", mapId");
        VM.sysWrite(mapId);
        VM.sysWrite(".\n");
        return Address.max();
      }
      
      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_QuickGCMapIterator getNextReferenceAddress index = ");
        VM.sysWrite(mapIndex);
        VM.sysWrite(".\n");
        if (mapId < 0) 
          VM.sysWrite("Offset is a JSR return address ie internal pointer.\n");
      }
    }

    if (state == CHECK_REFERENCE_STATE) {
        if (VM.TraceStkMaps) {
          VM.sysWrite("VM_QuickGCMapIterator location = ");
          VM.sysWrite(location);
        }
      if (location > 0) {
        if (VM.TraceStkMaps) {
          VM.sysWrite(". Register = ");
          VM.sysWrite(location);
          VM.sysWrite(".\n");
        }
        return registerLocations.get(location).toAddress();
      }
      else {
        int offset = maps.locationToOffset(mapIndex);
        if (VM.TraceStkMaps) {
          VM.sysWrite(". Offset = ");
          VM.sysWriteHex(offset);
          VM.sysWrite(".\n");
        }
        return (framePtr.add(offset));
      }
    } else if (state == CHECK_THIS_PTR_STATE) {
      state += 1;
      if (checkSavedThisPtr && maps.getSavedThisPtrOffset() >= 0) {
        if (VM.TraceStkMaps) {
          VM.sysWrite("VM_QuickGCMapIterator getNextReferenceAddress: savedThisOffset="); VM.sysWrite( maps.getSavedThisPtrOffset()); VM.sysWrite("\n");
        }
        checkSavedThisPtr = false;
        return framePtr.add(maps.getSavedThisPtrOffset());
      }
    }
    else if (state == CHECK_BRIDGE_REFERENCE_STATE &&
             bridgeParameterMappingRequired) {

      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_QuickGCMapIterator.getNextReferenceAddress: bridgeTarget="); VM.sysWrite(bridgeTarget); VM.sysWrite("\n");
      }
      if (!bridgeRegistersLocationUpdated) {
        // point registerLocations[] to our callers stackframe
        //
        Address addr = framePtr.add(VM_Compiler.getFrameSize(currentMethod));
        addr = addr.sub((LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * BYTES_IN_DOUBLE); 
        // skip non-volatile and volatile fprs
        for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i) {
          addr = addr.sub(BYTES_IN_ADDRESS);
          registerLocations.set(i, addr);
        }

        bridgeRegistersLocationUpdated = true;
      }

      // handle implicit "this" parameter, if any
      //
      if (bridgeParameterIndex == -1) {
        bridgeParameterIndex   += 1;
        bridgeRegisterIndex    += 1;
        bridgeRegisterLocation = bridgeRegisterLocation.add(4);
        return bridgeRegisterLocation.sub(4);
      }
         
      // now the remaining parameters
      //
      while (true) {
        if (bridgeParameterIndex == bridgeParameterTypes.length || bridgeRegisterIndex > LAST_VOLATILE_GPR) {
          bridgeParameterMappingRequired = false;
    state += 1;
          break;
        }
        VM_TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];
        if (bridgeParameterType.isReferenceType()) {
          bridgeRegisterIndex    += 1;
          bridgeRegisterLocation = bridgeRegisterLocation.add(4);
          return bridgeRegisterLocation.sub(4);
        } else if (bridgeParameterType.isLongType()) {
          bridgeRegisterIndex    += 2;
          bridgeRegisterLocation = bridgeRegisterLocation.add(8);
        } else if (bridgeParameterType.isDoubleType() || bridgeParameterType.isFloatType()) {
          // no gpr's used
        } else {
          // boolean, byte, char, short, int
          bridgeRegisterIndex    += 1;
          bridgeRegisterLocation = bridgeRegisterLocation.add(4);
        }
      }
    }
      
    return Address.zero();
  }

  //
  // Gets the location of the next return address
  // after the current position.
  //  a zero return indicates that no more references exist
  //
  public Address getNextReturnAddressAddress() {
    if (mapId >= 0)
      {
        if (VM.TraceStkMaps) 
          {
            VM.sysWrite("VM_QuickGCMapIterator getNextReturnAddressOffset mapId = ");
            VM.sysWrite(mapId);
            VM.sysWrite(".\n");
          }
        return Address.zero();
      }
    mapIndex = maps.getNextJSRReturnAddrIndex(mapIndex);
    if (VM.TraceStkMaps) 
      {
        VM.sysWrite("VM_QuickGCMapIterator getNextReturnAddressAddress index = ");
        VM.sysWrite(mapIndex);
        VM.sysWrite(".\n");
      }
    if (mapIndex == VM_QuickReferenceMaps.NOMORE) {
      return Address.zero();
    }
    else {
      int location = maps.getLocation(mapId, mapIndex);
      if (VM.TraceStkMaps) {
        VM.sysWrite("VM_QuickGCMapIterator getNextReturnAddressAddress location = ");
        VM.sysWrite(location);
        VM.sysWriteln();
    }
      if (VM_QuickCompiler.isRegister(location))
        return registerLocations.get(VM_QuickCompiler.locationToRegister(location)).toAddress();
      else
        return framePtr.add(VM_QuickCompiler.locationToOffset(location));
    }
  }

  // cleanup pointers - used with method maps to release data structures
  //    early ... they may be in temporary storage ie storage only used
  //    during garbage collection
  //
  public void cleanupPointers() {
    // Make sure that the registerLocation array is updated with the
    // locations where this method cached its callers registers.
    // [[Yuck. I hate having to do this here, but it seems to be the
    // only safe way to do this...]]
    updateCallerRegisterLocations();
    
    maps.cleanupPointers();
    maps = null;
    if (mapId < 0) 
      VM_QuickReferenceMaps.jsrLock.unlock();
    bridgeTarget         = null;
    bridgeParameterTypes = null;
  }
       
  public int getType() {
    return VM_CompiledMethod.QUICK;
  }


  private void updateCallerRegisterLocations() {
    Address addr = framePtr.add(VM_QuickCompiler.getCallerSaveOffset(currentMethod));

    for (int i = VM_QuickCompiler.min(maps.firstFixedLocalRegister, maps.firstFixedStackRegister);
         i <= VM_QuickCompiler.max(maps.lastFixedLocalRegister, maps.lastFixedStackRegister);
         i++) {
      registerLocations.set(i,addr);
      addr = addr.sub(4);
    }
    // and restore information for our scratch registers
    registerLocations.set(S1, addr);
    addr = addr.sub(4);

    registerLocations.set(S0, addr);
  }

  // For debugging (used with checkRefMap)
  //
  public int getStackDepth() {
    return maps.getStackDepth(mapId);
  }
}
