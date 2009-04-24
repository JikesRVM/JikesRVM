/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.runtimesupport;

import java.util.List;
import org.jikesrvm.ArchitectureSpecificOpt.OptGCMapIteratorConstants;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.GCIRMapElement;
import org.jikesrvm.compilers.opt.ir.RegSpillListElement;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A class that encapsulates the GCMap portion of the machine code maps.
 * An instance of this class is created to encode and instance of a
 * GCIRMap into an int[].  The int[] is stored persistently,
 * but the instance of the OptGCMap is NOT.
 *
 * <ul>
 * <li> each map will be a sequence of 1 or more ints
 * <li> the first int in each map is a bit map of registers that
 *   contain references (the MSB is used for chaining,
 *   we assume it will never contain a reference)
 * <li> the remaining ints will be spill locations
 * <li> the sequence will continue as long as the most significant bit
 *   is set to 1
 * </ul>
 *
 *  Note: This file contains two types of methods
 *         1) methods called during compilation to create the GC maps
 *            these methods are virtual
 *         2) methods called at GC time (no allocation allowed!)
 *            these methods are static
 */
@Uninterruptible
public final class OptGCMap implements OptGCMapIteratorConstants {
  public static final int NO_MAP_ENTRY = -1;
  public static final int ERROR = -2;

  /**
   *  The initial allocation size for a map
   */
  public static final int INITIAL_MAP_SIZE = 16;

  /**
   * bit pattern for the "next" bit in the GC maps array
   */
  private static final int NEXT_BIT = 0x80000000;

  /**
   * the index of the last map entry in use
   */
  private int lastGCMapEntry;

  /**
   *  The gc map array, a sequence of gc maps.  Each sequence starts
   *  with a register bit mask and is followed by a list of spills.
   *  The most significant bit of the spill location is used to chain
   *  the list.
   */
  private int[] gcMapInformation;

  public static final boolean DEBUG = false;

  /**
   * Constructor, called during compilation
   */
  OptGCMap() {
    lastGCMapEntry = -1;
    gcMapInformation = new int[INITIAL_MAP_SIZE];   // initial map size
  }

  /**
   * Called to complete the encoding and return the final int[]
   */
  @Interruptible
  public int[] finish() {
    if ((gcMapInformation != null) && (lastGCMapEntry < gcMapInformation.length - 1)) {
      resizeMapInformation(lastGCMapEntry + 1);
    }
    return gcMapInformation;
  }

  /**
   * Construct the GCMap for the argument GCIRMapElement
   * @param irMapElem  The IR Map element to create a GCMap for
   * @return the GCMap index.
   */
  @Interruptible
  public int generateGCMapEntry(GCIRMapElement irMapElem) {
    // the index into the GC maps we will use for this instruction.
    int mapIndex = NO_MAP_ENTRY;

    // Before requesting the (first) map entry, lets make sure we
    // will need it.  If the reg/spill list is empty, we don't
    // need a map slot, i.e., no references are live at this instruction
    List<RegSpillListElement> regSpillList = irMapElem.regSpillList();
    if (!regSpillList.isEmpty()) {

      // For efficiency we create our own bit map and then set the
      // appropriate array value
      int bitMap = 0;
      // count the spills so we know how big of an array we'll need
      int numSpills = 0;
      int numRegs = 0;

      // Because the output data structure (the map) stores register
      // information before spills, we need to traverse the list twice
      // the first time we get the register mask, the 2nd time we get
      // the spills
      // process the register
      for (RegSpillListElement elem : regSpillList) {
        if (elem.isSpill()) {
          numSpills++;
        } else {
          numRegs++;
          int realRegNumber = elem.getRealRegNumber();

          if (VM.VerifyAssertions && realRegNumber > LAST_GCMAP_REG) {
            System.out.println(elem);
            System.out.println(LAST_GCMAP_REG);
            VM._assert(false, "reg > last GC Map Reg!!");
          }

          // get the bit position for this register number
          int bitPosition = getRegBitPosition(realRegNumber);

          // Set the appropriate bit
          bitMap = bitMap | (NEXT_BIT >>> bitPosition);
        }
      }

      // get the next free Map entry
      int index = setRegisterBitMap(bitMap);

      int[] spillArray = new int[numSpills];
      int spillIndex = 0;
      // Now we need to walk the list again to process the spills.
      // first, get a fresh enumerator
      for (RegSpillListElement elem : regSpillList) {
        if (elem.isSpill()) {
          spillArray[spillIndex++] = elem.getSpill();
        }
      }

      // add the spills into the map
      addAllSpills(spillArray);

      // don't forget to report that there are no more spills
      mapIndex = endCurrentMap(index);

    }
    return mapIndex;
  }

  ////////////////////////////////////////////
  // Methods called at GC time
  ////////////////////////////////////////////
  /**
   * Returns the GC map information for the GC map information entry passed
   * @param  entry     map entry
   * @param  gcMap     the gc map
   */
  public static int gcMapInformation(int entry, int[] gcMap) {
    // before returning remember to clear the MSB.
    return gcMap[entry] & ~NEXT_BIT;
  }

  /**
   * Determines if the register map information for the entry passed is true
   * @param  entry            map entry
   * @param  registerNumber   the register number
   * @param  gcMap            the encoded GCMap
   */
  public static boolean registerIsSet(int entry, int registerNumber, int[] gcMap) {
    if (VM.VerifyAssertions) {
      VM._assert(registerNumber >= FIRST_GCMAP_REG && registerNumber <= LAST_GCMAP_REG, "Bad registerNumber");
    }

    // Get the bit position for the register number
    int bitPosition = getRegBitPosition(registerNumber);

    // Using the register number passed construct the appropriate bit string,
    // "and" it with the value, and see if we get a positive value
    return (gcMap[entry] & (NEXT_BIT >>> bitPosition)) > 0;
  }

  /**
   * @param  gcMap            the encoded GCMap
   * @return the next (relative) location or -1 for no more locations
   */
  public static int nextLocation(int currentIndex, int[] gcMap) {
    // Does the next entry contain anything useful?
    if (nextBitSet(currentIndex, gcMap)) {
      // if so, return the next index
      return currentIndex + 1;
    } else {
      return -1;
    }
  }

  /**
   *  This method maps a register number to its bit position
   *  @param registerNumber the register number of interest
   */
  private static int getRegBitPosition(int registerNumber) {
    //  Because we can't use bit position 0 (that is the next bit), we
    // adjust depending on the value of FIRST_GCMAP_REG
    //
    // For example,
    //  FIRST_GCMAP_REG = 1 => registerNumber = 1    (PPC)
    //  FIRST_GCMAP_REG = 0 => registerNumber = 1    (IA32)
    //
    return registerNumber - FIRST_GCMAP_REG + 1;
  }

  /**
   * Determines if the next bit is set for the entry passed in the gc map passed
   * @param entry the entry (index) to check
   * @param gcMap the gcmap
   * @return whether the next bit is set
   */
  private static boolean nextBitSet(int entry, int[] gcMap) {
    return (gcMap[entry] & NEXT_BIT) == NEXT_BIT;
  }

  /**
   * Dumps the GCmap that starts at entry.
   * @param entry  the entry where the map begins
   * @param gcMap the encoded GCmaps
   */
  @Interruptible
  public static void dumpMap(int entry, int[] gcMap) {
    VM.sysWrite("Regs [");
    // Inspect the register bit map for the entry passed and print
    // those bit map entries that are true
    for (int registerNumber = FIRST_GCMAP_REG; registerNumber <= LAST_GCMAP_REG; registerNumber++) {
      if (registerIsSet(entry, registerNumber, gcMap)) {
        VM.sysWrite(registerNumber, " ");
      }
    }
    VM.sysWrite("]");
    VM.sysWrite(" Spills [");
    while (nextBitSet(entry, gcMap)) {
      entry++;
      VM.sysWrite(gcMapInformation(entry, gcMap));
      VM.sysWrite(" ");
    }
    VM.sysWrite("]");
  }

  ////////////////////////////////////////////
  // Helper methods for GCMap creation
  ////////////////////////////////////////////
  /**
   * Returns the next GC map entry for use
   * @return the entry in the map table that can be used
   */
  @Interruptible
  private int getNextMapEntry() {
    // make sure we have enough room
    int oldLength = gcMapInformation.length - 1;
    if (lastGCMapEntry >= oldLength) {
      // expand the mapInformation array to be twice as big
      resizeMapInformation(oldLength << 1);
    }
    return ++lastGCMapEntry;
  }

  /**
   * Resize the map array
   * @param newSize the new size for the map array
   */
  @Interruptible
  private void resizeMapInformation(int newSize) {
    int[] newMapInformation = new int[newSize];
    for (int i = 0; i <= lastGCMapEntry; i++) {
      newMapInformation[i] = gcMapInformation[i];
    }
    gcMapInformation = newMapInformation;
  }

  //////////
  // Setters for GC Maps
  //////////

  /**
   * Sets the register map information at the next available entry
   * @param  bitMap    map entry
   * @return The index of that entry.
   */
  @Interruptible
  private int setRegisterBitMap(int bitMap) {
    // Set the appropriate bit, but make sure we preserve the NEXT bit!
    int entry = getNextMapEntry();
    gcMapInformation[entry] = bitMap | NEXT_BIT;
    return entry;
  }

  /**
   * If we will be looking for missed references we need to sort the list
   *  of spills and then add them to the map, otherwise, nothing to do
   * @param spillArray an array of spills
   */
  @Interruptible
  private void addAllSpills(int[] spillArray) {
    // 1) sort the spills to increase the odds of reusing the GC map
    java.util.Arrays.sort(spillArray);

    // 2) add them to the map using addSpillLocation
    for (int spill : spillArray) {
      addSpillLocation(spill);
    }
  }

  /**
   * Adds the passed spill value to the current map
   * @param spill the spill location
   */
  @Interruptible
  private void addSpillLocation(int spill) {
    // make sure the value doesn't overflow the maximum spill location
    if (VM.VerifyAssertions && ((spill < 0) || (spill > 32767))) {
      VM._assert(false, "Unexpected spill passed:" + spill);
    }
    // get the next entry (with the NEXT bit set) ...
    int entry = getNextMapEntry();
    gcMapInformation[entry] = spill | NEXT_BIT;
  }

  /**
   * Ends the current map
   * @param firstIndex the index of the beginning of the map
   * @return the index of the beginning of the map (may be different)
   */
  @Interruptible
  private int endCurrentMap(int firstIndex) {
    int lastEntry = lastGCMapEntry;

    // adjust the last entry so that the NEXT bit is not set.
    gcMapInformation[lastEntry] = gcMapInformation[lastEntry] & ~NEXT_BIT;

    if (DEBUG) {
      System.out.println("\nendCurrentMap called with firstIndex: " +
                         firstIndex +
                         ", lastGCMapEntry: " +
                         lastGCMapEntry);
      System.out.println("gc map array before reuse checking");
      for (int i = 0; i <= lastGCMapEntry; i++) {
        System.out.println(i + ": " + gcMapInformation[i]);
      }
    }

    // Now that we know the complete map information, let's determine if
    // we really need to store it, or instead can reuse a previous map.
    int candidateBeginningIndex = 0; //this will be the beginning
    int candidateIndex = candidateBeginningIndex;  // this will walk the map
    int curIndex = firstIndex;
    while (candidateIndex < firstIndex && curIndex <= lastEntry) {
      int old = gcMapInformation[candidateIndex++];
      int cur = gcMapInformation[curIndex++];
      if (old != cur) {
        if (DEBUG) {
          System.out.println("entries at " + (candidateIndex - 1) + " and " + (curIndex - 1) + " don't match");
        }
        // this entry won't work, advance to candidateIndex to GC map entry
        //  and reset curIndex
        while ((old & NEXT_BIT) != 0) {
          old = gcMapInformation[candidateIndex++];
        }

        // update the beginning index too
        candidateBeginningIndex = candidateIndex;
        curIndex = firstIndex;
      } else if ((old & NEXT_BIT) == 0) {
        // we've checked all of the candidate without stopping, so we found
        //  a winner to reuse

        if (DEBUG) {
          System.out.println("found a matching map: [" +
                             candidateBeginningIndex +
                             ", " +
                             (candidateIndex - 1) +
                             "] == [" +
                             firstIndex +
                             ", " +
                             lastGCMapEntry +
                             "]");
        }

        lastGCMapEntry = firstIndex - 1;
        return candidateBeginningIndex;
      }
    }

    return firstIndex;
  }
}
