/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.quick;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import org.vmmagic.pragma.*;

/**
 * Unusual maps are maps to track references that don't take the usual format.
 * Currently unusual maps include:
 *    maps of locations within JSR subroutines (includes return address map)
 * In the future the return address maps may be expanded to include other
 * internal pointers or internal/external pointers may be handled separately.
 *
 * Based on VM_UnusualMaps
 *
 * @author Chris Hoffmann
 */
class VM_QuickUnusualMaps implements Uninterruptible {

  // set the offset in the stack frame of the return address for this map
  //
  void setReturnAddressLocation(int location)
  {
    returnAddressLocation = location;
    return;
  }

  // provide the offset in the stack frame of the return address for this map
  //
  int getReturnAddressLocation()
  {
    return returnAddressLocation;
  }

  // set the  offset of the reference map in the stackmap list of maps
  //
  void setReferenceMapIndex(int index)
  {
    referenceMapIndex = index;
    return;
  }

  // provide the index in the stackmaps for the refernce map
  //
  int getReferenceMapIndex()
  {
    return referenceMapIndex;
  }

  // set the  offset of the non-reference map in the stackmap list of maps
  //
  void setNonReferenceMapIndex(int index)
  {
    nonReferenceMapIndex = index;
    return;
  }

  // provide the index in the stackmaps for the non-refernce map
  //
  int getNonReferenceMapIndex()
  {
    return nonReferenceMapIndex;
  }


  // set the  offset of the returnAddress map in the stackmap list of maps
  //
  void setReturnAddressMapIndex(int index)
  {
    returnAddressMapIndex = index;
    return;
  }

  // provide the index in the stackmaps for the return Address  map
  //
  int getReturnAddressMapIndex()
  {
    return returnAddressMapIndex;
  }

  // provide the normal map index ie the backpointer
  //
  int getNormalMapIndex()
  {
    return normalMapIndex;
  }

  // set the normal map index ie the backpointer
  //
  void setNormalMapIndex(int index)
  {
    normalMapIndex = index;
    return;
  }

  // deep copy of a UnusualMap
  //
  void copy(VM_QuickUnusualMaps that)
  {
    that.returnAddressLocation = this.returnAddressLocation;

  }



  // For maps of JSR subroutine locations
  int returnAddressLocation;   // index into the normal reference map of where the
                             // return address can be located
  int referenceMapIndex;     // index into the map table of the references set map
  int nonReferenceMapIndex;  // index into the map table of the non-reference set map
  int returnAddressMapIndex; // index into the map table of the return address map
  int normalMapIndex;        // index into the array of normal maps ie the backpointer

  public void showInfo()
  {
      VM.sysWrite("  UnusualMap showInfo- ");

      VM.sysWrite("    return address offsetbyte = ");
      VM.sysWrite(returnAddressLocation);
      VM.sysWrite("\n    referenceMapIndex = ");
      VM.sysWrite(referenceMapIndex);
      VM.sysWrite("\n    nonReferenceMapIndex = ");
      VM.sysWrite(nonReferenceMapIndex);
      VM.sysWrite("\n    returnAddressMapIndex = ");
      VM.sysWrite(returnAddressMapIndex);
      VM.sysWrite("\n");




  }

}



