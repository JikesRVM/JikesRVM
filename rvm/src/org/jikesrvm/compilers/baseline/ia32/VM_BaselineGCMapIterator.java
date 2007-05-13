/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.baseline.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.VM_ReferenceMaps;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.ia32.VM_BaselineConstants;
import org.jikesrvm.memorymanagers.mminterface.VM_GCMapIterator;
import org.jikesrvm.runtime.VM_DynamicLink;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.WordArray;

/**
 * Iterator for stack frame  built by the Baseline compiler
 * An Instance of this class will iterate through a particular 
 * reference map of a method returning the offsets of any refereces
 * that are part of the input parameters, local variables, and 
 * java stack for the stack frame.
 *
 */
@Uninterruptible
public abstract class VM_BaselineGCMapIterator extends VM_GCMapIterator
  implements VM_BaselineConstants {
  private static final boolean TRACE_ALL = false;
  private static final boolean TRACE_DL  = false; // dynamic link frames

  //-------------//
  // Constructor //
  //-------------//

  // 
  // Remember the location array for registers. This array needs to be updated
  // with the location of any saved registers.
  // This information is not used by this iterator but must be updated for the
  // other types of iterators (ones for the opt compiler built frames)
  // The locations are kept as addresses within the stack.
  //
  
  public VM_BaselineGCMapIterator(WordArray registerLocations) {
    this.registerLocations = registerLocations; // (in superclass)
    dynamicLink  = new VM_DynamicLink();
  }

  //-----------//
  // Interface //
  //-----------//

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
  public void setupIterator(VM_CompiledMethod compiledMethod, Offset instructionOffset, Address fp) {
    currentCompiledMethod = (VM_BaselineCompiledMethod)compiledMethod;
    currentMethod = (VM_NormalMethod) currentCompiledMethod.getMethod();
    currentNumLocals = currentMethod.getLocalWords();
      
    // setup superclass
    //
    framePtr = fp;
      
    // setup stackframe mapping
    //
    maps      = ((VM_BaselineCompiledMethod)compiledMethod).referenceMaps;
    mapId     = maps.locateGCPoint(instructionOffset, currentMethod);
    mapIndex = 0;
    if (mapId < 0) {
      // lock the jsr lock to serialize jsr processing
      VM_ReferenceMaps.jsrLock.lock();
      int JSRindex  = maps.setupJSRSubroutineMap(mapId);
      while (JSRindex != 0) {
        Address nextCallerAddress = framePtr.plus(convertIndexToOffset(JSRindex)).loadAddress();
        Offset nextMachineCodeOffset = compiledMethod.getInstructionOffset(nextCallerAddress);
        if (VM.TraceStkMaps) {
          VM.sysWriteln("     setupJSRsubroutineMap- nested jsrs end of loop- = ");
          VM.sysWriteln("      next jsraddress offset = ", JSRindex);
          VM.sysWriteln("      next callers address = ", nextCallerAddress);
          VM.sysWriteln("      next machinecodeoffset = ", nextMachineCodeOffset);
          if (nextMachineCodeOffset.sLT(Offset.zero()))
            VM.sysWriteln("BAD MACHINE CODE OFFSET");
        }
        JSRindex = maps.getNextJSRAddressIndex(nextMachineCodeOffset, currentMethod);
      }
    }
    if (VM.TraceStkMaps || TRACE_ALL ) {
      VM.sysWrite("VM_BaselineGCMapIterator setupIterator mapId = ");
      VM.sysWrite(mapId);
      VM.sysWrite(" for ");
      VM.sysWrite(compiledMethod.getMethod());
      VM.sysWrite(".\n");
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
    bridgeSpilledParamLocation     = Address.zero();
    
    if (currentMethod.getDeclaringClass().hasDynamicBridgeAnnotation()) {
      Address        ip                       = VM_Magic.getReturnAddress(fp);
                        fp                       = VM_Magic.getCallerFramePointer(fp);
      int               callingCompiledMethodId  = VM_Magic.getCompiledMethodID(fp);
      VM_CompiledMethod callingCompiledMethod    = VM_CompiledMethods.getCompiledMethod(callingCompiledMethodId);
      Offset            callingInstructionOffset = callingCompiledMethod.getInstructionOffset(ip);

      callingCompiledMethod.getDynamicLink(dynamicLink, callingInstructionOffset);
      bridgeTarget                    = dynamicLink.methodRef().getResolvedMember();
      bridgeParameterTypes            = bridgeTarget.getParameterTypes();
      if (dynamicLink.isInvokedWithImplicitThisParameter()) {
        bridgeParameterInitialIndex     = -1;
        bridgeSpilledParamInitialOffset =  8; // this + return addr
      } else {  
        bridgeParameterInitialIndex     =  0;
        bridgeSpilledParamInitialOffset =  4; // return addr
      }
      bridgeSpilledParamInitialOffset  += (4 * bridgeTarget.getParameterWords());
      bridgeSpilledParameterMappingRequired = 
          callingCompiledMethod.getCompilerType() != VM_CompiledMethod.BASELINE;
    }
        
    reset();
  }
  
  // Reset iteration to initial state.
  // This allows a map to be scanned multiple times
  //
  public void reset() {
    mapIndex = 0;
    finishedWithRegularMap = false;

    // setup map to report EBX if this method is holding the base of counter array in it.
    counterArrayBase                 = currentCompiledMethod.hasCounterArray();

    if (bridgeTarget != null) {
      bridgeParameterMappingRequired = true;
      bridgeParameterIndex           = bridgeParameterInitialIndex;
      bridgeRegisterIndex            = 0;
      bridgeRegisterLocation         = framePtr.plus(STACKFRAME_FIRST_PARAMETER_OFFSET); // top of frame
      bridgeSpilledParamLocation     = framePtr.plus(bridgeSpilledParamInitialOffset);
    }
  }

  /**
   * given a index in the local area (biased : local0 has index 1)
   *   this routine determines the correspondig offset in the stack
   */
  public int convertIndexToLocation(int index)   {
    if (index == 0) return 0;
    if (index <= currentNumLocals) { //index is biased by 1;
      return currentCompiledMethod.getGeneralLocalLocation(index-1); 
    } else {
      return currentCompiledMethod.getGeneralStackLocation(index-1-currentNumLocals);
    }
  } 
  
  private int convertIndexToOffset(int index) { 
    //for ia32: always offset, never registers
    if (index == 0) return 0; //invalid
   
    // index is biased by 1, index 1 means local 0, this is at offset -BYTES_IN_ADDRESS from startLocalOffset 
    int offset = VM_Compiler.locationToOffset(convertIndexToLocation(index)) - BYTES_IN_ADDRESS; // no jsrbit here
    if (VM.TraceStkMaps) {
      VM.sysWriteln("convertIndexToOffset- input index = ", index, "  offset = ", offset);
    }
    return offset;
  }

  // Get location of next reference.
  // A zero return indicates that no more references exist.
  //
  public Address getNextReferenceAddress() {
    if (!finishedWithRegularMap) {
      if (counterArrayBase) {
        counterArrayBase = false;
        return registerLocations.get(EBX).toAddress();
      }
      if (mapId < 0) {
        mapIndex = maps.getNextJSRRefIndex(mapIndex);
      } else {
        mapIndex = maps.getNextRefIndex(mapIndex, mapId);
      }
      
      if (mapIndex != 0) {
        int mapOffset=convertIndexToOffset(mapIndex);
        if (VM.TraceStkMaps || TRACE_ALL) {
          VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = ");
          VM.sysWriteHex(mapOffset);
          VM.sysWrite(".\n");
          VM.sysWrite("Reference is ");
        }
        if (bridgeParameterMappingRequired) {
          if (VM.TraceStkMaps || TRACE_ALL) {
            VM.sysWriteHex(framePtr.plus(mapOffset - BRIDGE_FRAME_EXTRA_SIZE).loadInt());
            VM.sysWrite(".\n");
            if (mapId < 0) {
              VM.sysWrite("Offset is a JSR return address ie internal pointer.\n");
            }
          }
          
          // TODO  clean this
          return (framePtr.plus(mapOffset - BRIDGE_FRAME_EXTRA_SIZE ));
        } else {
          if (VM.TraceStkMaps || TRACE_ALL) {
            VM.sysWriteHex(framePtr.plus(mapOffset).loadInt());
            VM.sysWrite(".\n");
            if (mapId < 0) {
              VM.sysWrite("Offset is a JSR return address ie internal pointer.\n");
            }
          }
          return (framePtr.plus(mapOffset) );
        }
      } else {
        // remember that we are done with the map for future calls, and then
        //   drop down to the code below 
        finishedWithRegularMap = true;
      }
    }

    if (bridgeParameterMappingRequired) {
      if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
        VM.sysWrite("getNextReferenceAddress: bridgeTarget="); VM.sysWrite(bridgeTarget); VM.sysWrite("\n");
      }         

      if (!bridgeRegistersLocationUpdated) {
        // point registerLocations[] to our callers stackframe
        //
        registerLocations.set(JTOC, framePtr.plus(JTOC_SAVE_OFFSET).toWord());
        registerLocations.set(T0, framePtr.plus(T0_SAVE_OFFSET).toWord());
        registerLocations.set(T1, framePtr.plus(T1_SAVE_OFFSET).toWord());
        registerLocations.set(EBX, framePtr.plus(EBX_SAVE_OFFSET).toWord());
        
        bridgeRegistersLocationUpdated = true;
      }

      // handle implicit "this" parameter, if any
      //
      if (bridgeParameterIndex == -1) {
        bridgeParameterIndex       += 1;
        bridgeRegisterIndex        += 1;
        bridgeRegisterLocation     = bridgeRegisterLocation.minus(4);
        bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(4);
        
        if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
          VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR this ");
          VM.sysWrite(bridgeRegisterLocation.plus(4));
          VM.sysWrite(".\n");
        }

        return bridgeRegisterLocation.plus(4);
      }
         
      // now the remaining parameters
      //
      while(bridgeParameterIndex < bridgeParameterTypes.length) {
        VM_TypeReference bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];
        
        if (bridgeParameterType.isReferenceType()) {
          bridgeRegisterIndex        += 1;
          bridgeRegisterLocation     = bridgeRegisterLocation.minus(4);
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(4);
          
          if (bridgeRegisterIndex <= NUM_PARAMETER_GPRS) {
            if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
              VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = dynamic link GPR parameter ");
              VM.sysWrite(bridgeRegisterLocation.plus(4));
              VM.sysWrite(".\n");
            }
            return bridgeRegisterLocation.plus(4);
          } else {
            if (bridgeSpilledParameterMappingRequired) {
              if (VM.TraceStkMaps || TRACE_ALL || TRACE_DL) {
                VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = dynamic link spilled parameter ");
                VM.sysWrite(bridgeSpilledParamLocation.plus(4));
                VM.sysWrite(".\n");
              }
              return bridgeSpilledParamLocation.plus(4);
            } else {
              break;
            }
          }
        } else if (bridgeParameterType.isLongType()) {
          bridgeRegisterIndex        += 2;
          bridgeRegisterLocation     = bridgeRegisterLocation.minus(8);
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(8);
        } else if (bridgeParameterType.isDoubleType()) {
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(8);
        } else if (bridgeParameterType.isFloatType()) {
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(4);
        } else { 
          // boolean, byte, char, short, int
          bridgeRegisterIndex        += 1;
          bridgeRegisterLocation     = bridgeRegisterLocation.minus(4);
          bridgeSpilledParamLocation = bridgeSpilledParamLocation.minus(4);
        }
      }
    } else {
      // point registerLocations[] to our callers stackframe
      //
      registerLocations.set(JTOC, framePtr.plus(JTOC_SAVE_OFFSET).toWord());
      registerLocations.set(EBX, framePtr.plus(EBX_SAVE_OFFSET).toWord());
    }
    
    return Address.zero();
  }

  //
  // Gets the location of the next return address
  // after the current position.
  //  a zero return indicates that no more references exist
  //
  public Address getNextReturnAddressAddress() {
    if (mapId >= 0) {
      if (VM.TraceStkMaps || TRACE_ALL) {
        VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
        VM.sysWrite(mapId);
        VM.sysWrite(".\n");
      }
      return Address.zero();
    }
    mapIndex = maps.getNextJSRReturnAddrIndex(mapIndex);
    if (VM.TraceStkMaps || TRACE_ALL) {
      VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset = ");
      VM.sysWrite(convertIndexToOffset(mapIndex));
      VM.sysWrite(".\n");
    }
    return (mapIndex == 0) ? Address.zero() : framePtr.plus(convertIndexToOffset(mapIndex));
  }

  // cleanup pointers - used with method maps to release data structures
  //    early ... they may be in temporary storage ie storage only used
  //    during garbage collection
  //
  public void cleanupPointers() {
    maps.cleanupPointers();
    maps = null;
    if (mapId < 0)   
      VM_ReferenceMaps.jsrLock.unlock();
    bridgeTarget         = null;
    bridgeParameterTypes = null;
  }

  public int getType() {
    return VM_CompiledMethod.BASELINE;
  }

  // For debugging (used with checkRefMap)
  //
  public int getStackDepth() {
    return maps.getStackDepth(mapId);
  }

  // Iterator state for mapping any stackframe.
  //
  private VM_NormalMethod  currentMethod;      // compiled method for the frame
  private VM_BaselineCompiledMethod  currentCompiledMethod;      // compiled method for the frame
  private int currentNumLocals;
  private int              mapIndex; // current index in current map
  private int              mapId;     // id of current map out of all maps
  private VM_ReferenceMaps maps;      // set of maps for this method
  private boolean counterArrayBase;   // have we reported the base ptr of the edge counter array?

  // Additional iterator state for mapping dynamic bridge stackframes.
  //
  private VM_DynamicLink dynamicLink;                    // place to keep info returned by VM_CompiledMethod.getDynamicLink
  private VM_Method bridgeTarget;                        // method to be invoked via dynamic bridge (null: current frame is not a dynamic bridge)
  private VM_TypeReference[]      bridgeParameterTypes;           // parameter types passed by that method
  private boolean        bridgeParameterMappingRequired; // have all bridge parameters been mapped yet?
  private boolean        bridgeSpilledParameterMappingRequired; // do we need to map spilled params (baseline compiler = no, opt = yes)
  private boolean        bridgeRegistersLocationUpdated; // have the register location been updated
  private boolean        finishedWithRegularMap;         // have we processed all the values in the regular map yet?
  private int            bridgeParameterInitialIndex;    // first parameter to be mapped (-1 == "this")
  private int            bridgeParameterIndex;           // current parameter being mapped (-1 == "this")
  private int            bridgeRegisterIndex;            // gpr register it lives in
  private Address     bridgeRegisterLocation;         // memory address at which that register was saved
  private Address     bridgeSpilledParamLocation;     // current spilled param location
  private int            bridgeSpilledParamInitialOffset;// starting offset to stack location for param0
}
