/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/*
 * Iterator for stack frame  built by the Baseline compiler
 * An Instance of this class will iterate through a particular 
 * reference map of a method returning the offsets of any refereces
 * that are part of the input parameters, local variables, and 
 * java stack for the stack frame.
 *
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Derek Lieber
 */
final class VM_BaselineGCMapIterator extends VM_GCMapIterator implements VM_BaselineConstants
   {
   //-------------//
   // Constructor //
   //-------------//

   //
   // Remember the location array for registers. This array needs to be updated
   // with the location of any saved registers.
   // This information is not used by this iterator but must be updated for the
   // other types of iterators (ones for the quick and opt compiler built frames)
   // The locations are kept as addresses within the stack.
   //

   VM_BaselineGCMapIterator(int registerLocations[])
      {
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
   void
   setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, int fp)
      {
      currentMethod = compiledMethod.getMethod();

   // VM.sysWrite("setupIterator: method="); VM.sysWrite(currentMethod); VM.sysWrite("\n");
      
      // setup superclass
      //
      framePtr = fp;
      
      // setup stackframe mapping
      //
      maps      = ((VM_BaselineCompilerInfo)compiledMethod.getCompilerInfo()).referenceMaps;
      mapId     = maps.locateGCPoint(instructionOffset, currentMethod);
      mapOffset = 0;
      if (mapId < 0)
         {
         // lock the jsr lock to serialize jsr processing
         VM_ReferenceMaps.jsrLock.lock();
         maps.setupJSRSubroutineMap( framePtr, mapId, compiledMethod);
         }
      if (VM.TraceStkMaps)
         {
         VM.sysWrite("VM_BaselineGCMapIterator setupIterator mapId = ");
         VM.sysWrite(mapId);
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
      bridgeRegisterLocation         = 0;

      if (currentMethod.getDeclaringClass().isDynamicBridge())
         {
                           fp                       = VM_Magic.getCallerFramePointer(fp);
         int               ip                       = VM_Magic.getNextInstructionAddress(fp);
         int               callingCompiledMethodId  = VM_Magic.getCompiledMethodID(fp);
         VM_CompiledMethod callingCompiledMethod    = VM_ClassLoader.getCompiledMethod(callingCompiledMethodId);
         VM_CompilerInfo   callingCompilerInfo      = callingCompiledMethod.getCompilerInfo();
         int               callingInstructionOffset = ip - VM_Magic.objectAsAddress(callingCompiledMethod.getInstructions());

         callingCompilerInfo.getDynamicLink(dynamicLink, callingInstructionOffset);
         bridgeTarget                = dynamicLink.methodRef();
         bridgeParameterInitialIndex = dynamicLink.isInvokedWithImplicitThisParameter() ? -1 : 0;
         bridgeParameterTypes        = bridgeTarget.getParameterTypes();

         }
        
      reset();
      }
  
   // Reset iteration to initial state.
   // This allows a map to be scanned multiple times
   //
   void
   reset()
      {
   // VM.sysWrite("reset\n");
      mapOffset = 0;

      if (bridgeTarget != null)
         {
         // point to first saved gpr
         bridgeParameterMappingRequired = true;
         bridgeParameterIndex   = bridgeParameterInitialIndex;
         bridgeRegisterIndex    = FIRST_VOLATILE_GPR;
         bridgeRegisterLocation = VM_Magic.getMemoryWord(framePtr)           // top of frame
                      - (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * 8  // fprs
                      - (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1) * 4; // gprs
         }
      }

   // Get location of next reference.
   // A zero return indicates that no more references exist.
   //
   int
   getNextReferenceAddress()
      {
      if (mapId < 0)
         mapOffset = maps.getNextJSRRef(mapOffset);
      else
         mapOffset = maps.getNextRef(mapOffset, mapId);
      if (VM.TraceStkMaps)
         {
         VM.sysWrite("VM_BaselineGCMapIterator getNextReferenceOffset = ");
         VM.sysWrite(mapOffset);
         VM.sysWrite(".\n");
         if (mapId < 0) 
            VM.sysWrite("Offset is a JSR return address ie internal pointer.\n");
         }

      if (mapOffset != 0)
        return (framePtr + mapOffset);

      else if (bridgeParameterMappingRequired)
         {
      // VM.sysWrite("getNextReferenceAddress: bridgeTarget="); VM.sysWrite(bridgeTarget); VM.sysWrite("\n");
         
	 if (!bridgeRegistersLocationUpdated)
            {
            // point registerLocations[] to our callers stackframe
            //
            int location = framePtr + VM_Compiler.getFrameSize(currentMethod);
            location -= (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * 8; 
						// skip non-volatile and volatile fprs
            for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
                registerLocations[i] = location -= 4;

            bridgeRegistersLocationUpdated = true;
            }

         // handle implicit "this" parameter, if any
         //
         if (bridgeParameterIndex == -1)
            {
            bridgeParameterIndex   += 1;
            bridgeRegisterIndex    += 1;
            bridgeRegisterLocation += 4;
            return bridgeRegisterLocation - 4;
            }
         
         // now the remaining parameters
         //
         for (;;)
            {
            if (bridgeParameterIndex == bridgeParameterTypes.length || bridgeRegisterIndex > LAST_VOLATILE_GPR)
               {
               bridgeParameterMappingRequired = false;
               break;
               }
            VM_Type bridgeParameterType = bridgeParameterTypes[bridgeParameterIndex++];
            if (bridgeParameterType.isReferenceType())
               {
               bridgeRegisterIndex    += 1;
               bridgeRegisterLocation += 4;
               return bridgeRegisterLocation - 4;
               }
            else if (bridgeParameterType.isLongType())
               {
               bridgeRegisterIndex    += 2;
               bridgeRegisterLocation += 8;
               }
            else if (bridgeParameterType.isDoubleType() || bridgeParameterType.isFloatType())
               { // no gpr's used
               }
            else 
               { // boolean, byte, char, short, int
               bridgeRegisterIndex    += 1;
               bridgeRegisterLocation += 4;
               }
            }
         }
      
      return 0;
      }

   //
   // Gets the location of the next return address
   // after the current position.
   //  a zero return indicates that no more references exist
   //
   int
   getNextReturnAddressAddress()
      {
   // VM.sysWrite("getNextReturnAddressAddress\n");
      if (mapId >= 0)
         {
         if (VM.TraceStkMaps) 
            {
            VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset mapId = ");
            VM.sysWrite(mapId);
            VM.sysWrite(".\n");
            }
         return 0;
         }
      mapOffset = maps.getNextJSRReturnAddr(mapOffset);
      if (VM.TraceStkMaps) 
         {
         VM.sysWrite("VM_BaselineGCMapIterator getNextReturnAddressOffset = ");
         VM.sysWrite(mapOffset);
         VM.sysWrite(".\n");
         }
      return (mapOffset == 0) ? 0 : (framePtr + mapOffset);
      }

   // cleanup pointers - used with method maps to release data structures
   //    early ... they may be in temporary storage ie storage only used
   //    during garbage collection
   //
   void
   cleanupPointers()
      {
   // VM.sysWrite("cleanupPointers\n");
      maps.cleanupPointers();
      maps = null;
      if (mapId < 0)   
         VM_ReferenceMaps.jsrLock.unlock();
      bridgeTarget         = null;
      bridgeParameterTypes = null;
      }

   int
   getType() 
      {
      return VM_GCMapIterator.BASELINE;
      }

   // For debugging (used with checkRefMap)
   //
   int
   getStackDepth()
      {
      return maps.getStackDepth(mapId);
      }

   // Iterator state for mapping any stackframe.
   //
   private   int              mapOffset; // current offset in current map
   private   int              mapId;     // id of current map out of all maps
   private   VM_ReferenceMaps maps;      // set of maps for this method

   // Additional iterator state for mapping dynamic bridge stackframes.
   //
   private VM_DynamicLink dynamicLink;                    // place to keep info returned by VM_CompilerInfo.getDynamicLink
   private VM_Method      bridgeTarget;                   // method to be invoked via dynamic bridge (null: current frame is not a dynamic bridge)
   private VM_Method      currentMethod;                  // method for the frame
   private VM_Type[]      bridgeParameterTypes;           // parameter types passed by that method
   private boolean        bridgeParameterMappingRequired; // have all bridge parameters been mapped yet?
   private boolean        bridgeRegistersLocationUpdated; // have the register location been updated
   private int            bridgeParameterInitialIndex;    // first parameter to be mapped (-1 == "this")
   private int            bridgeParameterIndex;           // current parameter being mapped (-1 == "this")
   private int            bridgeRegisterIndex;            // gpr register it lives in
   private int            bridgeRegisterLocation;         // memory address at which that register was saved
   }
