/*
 * (C) Copyright IBM Corp. 2001
 */
// Iterator for stack frames inserted by hardware trap handler.
// Such frames are purely used as markers.
// They contain no object references or JSR return addresses.
// 02 Jun 1999 Derek Lieber
//
final class VM_HardwareTrapGCMapIterator extends VM_GCMapIterator
   {
   VM_HardwareTrapGCMapIterator(int[] registerLocations)
      {
      this.registerLocations = registerLocations;
      }

   void
   setupIterator(VM_CompiledMethod compiledMethod, int instructionOffset, int framePtr) //- implements VM_GCMapIterator
      {
      this.framePtr = framePtr;
      }
  
   int
   getNextReferenceAddress() //- implements VM_GCMapIterator
      {
      // update register locations, noting that the trap handler represented by this stackframe
      // saved all registers into the thread's "hardwareExceptionRegisters" object
      //
      int registerLocation = VM_Magic.objectAsAddress(thread.hardwareExceptionRegisters.gprs);
      for (int i = 0; i < VM_Constants.NUM_GPRS; ++i)
         {
         registerLocations[i] = registerLocation;
         registerLocation += 4;
         }
      return 0;
      }

   int
   getNextReturnAddressAddress() //- implements VM_GCMapIterator
      {
      return 0;
      }

   void
   reset() //- implements VM_GCMapIterator
     {
     }

   void
   cleanupPointers() //- implements VM_GCMapIterator
      {
      }

   int
   getType() //- implements VM_GCMapIterator
      {
      return VM_GCMapIterator.TRAP;
      }
   }
