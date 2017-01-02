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
package org.jikesrvm.osr;

import static org.jikesrvm.classloader.ClassLoaderConstants.ArrayTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.BooleanTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.ByteTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.CharTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.ClassTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.DoubleTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.FloatTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.IntTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.LongTypeCode;
import static org.jikesrvm.classloader.ClassLoaderConstants.ShortTypeCode;
import static org.jikesrvm.compilers.opt.runtimesupport.OptGCMap.FIRST_GCMAP_REG;
import static org.jikesrvm.osr.OSRConstants.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.inlining.CallSiteTree;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.mir2mc.MachineCodeOffsets;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Offset;

/**
 * EncodedOSRMap provides the similar function as GC map
 * in OptMachineCodeMap.
 * <p>
 * In OptCompiledMethod, an instance of this class will represent
 * all OSR map info for that method.
 */
public final class EncodedOSRMap {

  /** osr info entries */
  private final long[] mapEntries;

  /** the last entry index. */
  private final int lastEntry;

  /** the OSR map */
  private final int[] osrMaps;

  /** map used when there are no OSR instructions */
  private static final EncodedOSRMap emptyMap = new EncodedOSRMap();

  @Inline
  public static boolean registerIsSet(int map, int regnum) {
    int bitpos = getRegBitPosition(regnum);
    return (map & (NEXT_BIT >>> bitpos)) > 0;
  }

  /**
   * Marks a register as a reference type.
   *
   * @param map the map
   * @param regnum the register's number
   * @return the updated map
   */
  private static int setRegister(int map, int regnum) {
    int bitpos = getRegBitPosition(regnum);
    map |= (NEXT_BIT >>> bitpos);
    return map;
  }

  @Inline
  private static int getRegBitPosition(int regnum) {
    return regnum - FIRST_GCMAP_REG + 1;
  }

  /** Constructor to build empty map */
  private EncodedOSRMap() {
    this.mapEntries = null;
    this.osrMaps = null;
    this.lastEntry = -1;
  }

  /**
   * @param varMap the variable map to use for building
   *  the EncodedOSRMap
   * @param mcOffsets the machine code offsets for the
   *  instructions
   */
  private EncodedOSRMap(VariableMap varMap, MachineCodeOffsets mcOffsets) {
    int entries = varMap.getNumberOfElements();

    this.lastEntry = entries - 1;

    if (VM.VerifyAssertions) VM._assert(entries > 0);
    this.mapEntries = new long[entries];
    ArrayList<Integer> tempOsrMaps = new ArrayList<Integer>();
    translateMap(tempOsrMaps, varMap.list, mcOffsets);
    this.osrMaps = new int[tempOsrMaps.size()];
    for (int i = 0; i < tempOsrMaps.size(); i++) {
      this.osrMaps[i] = tempOsrMaps.get(i);
    }

    //if (VM.TraceOnStackReplacement) {
    //  printMap();
    //}
  }

  /**
   * Encodes the given variable map as OSRMap.
   *
   * @param varMap the variable map to encode
   * @param mcOffsets machine code offsets for the instructions
   * @return the canonical empty map if the map
   * is empty, an encoded osr map otherwise
   */
  public static EncodedOSRMap makeMap(VariableMap varMap, MachineCodeOffsets mcOffsets) {
    if (varMap.getNumberOfElements() > 0) {
      return new EncodedOSRMap(varMap, mcOffsets);
    } else {
      return emptyMap;
    }
  }

  /**
   * Translates a list of OSR_MapElement to encoding.
   * <p>
   * we can not trust the osrlist is in the increasing order of
   * machine code offset. Sort it first.
   *
   * @param tempOsrMaps an empty list that will hold temporary
   *  OSR map information
   * @param osrlist information about instructions and variables
   * @param mcOffsets machine code offsets for the
   *  instructions
   */
  private void translateMap(ArrayList<Integer> tempOsrMaps,
      LinkedList<VariableMapElement> osrlist, final MachineCodeOffsets mcOffsets) {

    /* sort the list, use the mc offset of the index instruction
     * as the key.
     */
    int n = osrlist.size();

    VariableMapElement[] osrarray = new VariableMapElement[n];
    for (int i = 0; i < n; i++) {
      osrarray[i] = osrlist.get(i);
    }

    /* ideally, the osrList should be in sorted order by MC offset,
     * but I got once it is not in the order. To work correctly,
     * sort it first.
     *
     * TODO: figure out why LiveAnalysis does not give correct order?
     */
    if (n > 1) {
      Arrays.sort(osrarray,
        new Comparator<VariableMapElement>() {
          @Override
          public int compare(VariableMapElement a, VariableMapElement b) {
            return mcOffsets.getMachineCodeOffset(a.osr) -
                mcOffsets.getMachineCodeOffset(b.osr);
          }
        });
    }
    CallSiteTree inliningTree = new CallSiteTree();
    for (int i = 0; i < n; i++) {
      Instruction instr = osrarray[i].osr;
      // add lining element, move sanity later
      if (instr.position() != null) {
        inliningTree.addLocation(instr.position());
      }
    }

    for (int i = 0; i < n; i++) {

      VariableMapElement elm = osrarray[i];
      Instruction instr = elm.osr;

      int iei = inliningTree.find(instr.position()).encodedOffset;
      setIEIndex(i, iei);

      // get osr map
      LinkedList<MethodVariables> mVarList = elm.mvars;
      int osrMapIndex = generateOsrMaps(tempOsrMaps, mVarList);

      // use this offset, and adjust on extractState
      int mcOffset = mcOffsets.getMachineCodeOffset(instr);
      setMCOffset(i, mcOffset);
      setOSRMapIndex(i, osrMapIndex);
      setBCIndex(i, instr.getBytecodeIndex());
    }
  }

  /**
   * Generate value in the Osr map,
   * return the index of the first integer in the map.
   * <p>
   * An OSR Map has following structure:
   * <pre>
   * | regmap || mid, mpc, (n1, n2) ... ||
   *          || mid, mpc, (n1, n2) ... ||
   * </pre>
   * Regmap indicates the value of which register is a reference,
   * the execution state extractor can convert the value to an
   * object to avoid confusing GC.
   * The MSB of regmap indicates next mid is valid.
   * <p>
   * The MSB of mid indicates if the next mid item will be
   * available.
   * <p>
   * The MSB of mpc indicates if the next is a valid pair
   *
   * @param tempOsrMaps temporary OSR map information. This method will
   *  fill this data structure.
   * @param mVarList information about variables
   * @return the index of the first integer in the map
   */
  private int generateOsrMaps(ArrayList<Integer> tempOsrMaps, LinkedList<MethodVariables> mVarList) {

    int regmap = (!mVarList.isEmpty()) ? NEXT_BIT : 0;
    tempOsrMaps.add(regmap);
    int mapIndex = tempOsrMaps.size() - 1;

    // from inner to outer
    for (int i = 0, m = mVarList.size(); i < m; i++) {
      MethodVariables mVar = mVarList.get(i);
      _generateMapForOneMethodVariable(tempOsrMaps, mapIndex, mVar, (i == (m - 1)));
    }

    return mapIndex;
  }

  /**
   * Generate value in the Osr map
   * @param tempOsrMaps the maps under construction
   * @param regMapIndex used to patch the register map
   * @param mVar the method variables
   * @param lastMid whether this is the last method in the inlined chain
   */
  private void _generateMapForOneMethodVariable(ArrayList<Integer> tempOsrMaps, int regMapIndex, MethodVariables mVar, boolean lastMid) {
    // Is this the last method in the inlined chain?
    int mid = lastMid ? mVar.methId : (mVar.methId | NEXT_BIT);
    tempOsrMaps.add(mid);

    LinkedList<LocalRegPair> tupleList = mVar.tupleList;
    int m = tupleList.size();

    // Is this method has variables?
    int bci = (m == 0) ? mVar.bcIndex : (mVar.bcIndex | NEXT_BIT);
    tempOsrMaps.add(bci);

    // append each element
    for (int j = 0; j < m; j++) {
      LocalRegPair tuple = tupleList.get(j);

      boolean isLast = (j == m - 1);

      processTuple(tempOsrMaps, tuple, isLast);
      // mark the reg ref map
      if (((tuple.typeCode == ClassTypeCode) || (tuple.typeCode == ArrayTypeCode)) && (tuple.valueType == PHYREG)) {
        tempOsrMaps.set(regMapIndex, setRegister(tempOsrMaps.get(regMapIndex), tuple.value.toInt()));
      }
    }
  }

  /**
   * Process a 32-bit tuple.

   * @param tempOsrMaps the temporary osr maps
   * @param tuple mapping of the local to register
   * @param isLast whether to set {@link OSRConstants#NEXT_BIT}
   */
  private void processTuple(ArrayList<Integer> tempOsrMaps, LocalRegPair tuple, boolean isLast) {

    int first = (tuple.num << NUM_SHIFT) & NUM_MASK;

    if (!isLast) {
      first |= NEXT_BIT;
    }

    first |= (tuple.kind ? 1 : 0) << KIND_SHIFT;

    first |= (tuple.valueType << VTYPE_SHIFT);

    switch (tuple.typeCode) {
      case BooleanTypeCode:
      case ByteTypeCode:
      case CharTypeCode:
      case ShortTypeCode:
      case IntTypeCode:
        first |= (INT << TCODE_SHIFT);
        break;
      case FloatTypeCode:
        first |= (FLOAT << TCODE_SHIFT);
        break;
      case DoubleTypeCode:
        first |= (DOUBLE << TCODE_SHIFT);
        break;
      case LongTypeCode:
        if (VM.BuildFor32Addr || (tuple.valueType == LCONST)) {
          // split in two integer parts for OSR map
          // process the first half part,
          // it is not the last.
          first |= NEXT_BIT;
          first |= (HIGH_64BIT << TCODE_SHIFT);

          // add first word
          tempOsrMaps.add(first);
          // add the second word

          if (VM.BuildFor64Addr) {
            tempOsrMaps.add(tuple.value.rshl(32).toInt());
          } else {
            tempOsrMaps.add(tuple.value.toInt());
            tuple = tuple._otherHalf;
          }
          // process the second half part,
          // it may be the last, and it is not the first half.
          first = (tuple.num << NUM_SHIFT) & NUM_MASK;

          if (!isLast) first |= NEXT_BIT;

          first |= (tuple.kind ? 1 : 0) << KIND_SHIFT;
          first |= (tuple.valueType << VTYPE_SHIFT);
        }
        first |= (LONG << TCODE_SHIFT);
        break;
      case ReturnAddressTypeCode:

        if (false) {
          VM.sysWrite("returnaddress type for ");
          if (tuple.kind == LOCAL) {
            VM.sysWrite("L" + tuple.num);
          } else {
            VM.sysWrite("S" + tuple.num);
          }
          VM.sysWriteln();
        }

        first |= (RET_ADDR << TCODE_SHIFT);
        break;
      case WordTypeCode:
        if (VM.BuildFor64Addr && (tuple.valueType == ICONST)) { //KV:TODO
          //split in two integer parts for OSR map
          // process the first half part,
          // it is not the last. */
          first |= NEXT_BIT;
          first |= (HIGH_64BIT << TCODE_SHIFT);

          // add first word
          tempOsrMaps.add(first);
          // add the second word
          tempOsrMaps.add(tuple.value.rshl(32).toInt());

          // process the second half part,
          // it may be the last, and it is not the first half.
          first = (tuple.num << NUM_SHIFT) & NUM_MASK;
          if (!isLast) first |= NEXT_BIT;
          first |= (tuple.kind ? 1 : 0) << KIND_SHIFT;
          first |= (tuple.valueType << VTYPE_SHIFT);
        }
        first |= (WORD << TCODE_SHIFT);
        break;
      case ClassTypeCode:
      case ArrayTypeCode:
        first |= (REF << TCODE_SHIFT);
        break;
    }

    // add first word
    tempOsrMaps.add(first);
    // add the second word
    tempOsrMaps.add(tuple.value.toInt());
  }

  ////////////////////////////////////
  // INTERFACE
  ///////////////////////////////////

  /**
   * @param mcOffset the machine instruction offset
   * @return whether there's an OSR map exist for
   *  the machine instruction offset
   */
  public boolean hasOSRMap(Offset mcOffset) {
    int entry = findOSREntry(mcOffset);
    return (entry != NO_OSR_ENTRY);
  }

  /**
   * Get bytecode index for a given instruction offset in bytes.
   * <p>
   * NOTE: It is the caller's reponsibility to make sure there are OSR
   * entry exist for a machine instruction offset.
   *
   * @param mcOffset the instruction offset in bytes
   * @return the bytecode index
   */
  public int getBytecodeIndexForMCOffset(Offset mcOffset) {
    int entry = findOSREntry(mcOffset);
    return getBCIndex(entry);
  }

  /* TODO!
   * get inline encoding index for the machine instruction offset
   */
  public int getInlineEncodingForMCOffset(Offset mcOffset) {
    return -1;
  }

  /**
   * Gets register's reference map for the machine instruction offset
   *
   * @param mcOffset the instruction offset in bytes
   * @return the desired OSR map
   */
  public int getRegisterMapForMCOffset(Offset mcOffset) {
    int entry = findOSREntry(mcOffset);
    int mapIndex = getOSRMapIndex(entry);
    return osrMaps[mapIndex];
  }

  /**
   * given a MC offset, return an iterator over the
   * elements of this map.
   * <p>
   * NOTE: the map index is gotten from 'findOSRMapIndex'.
   * This has to be changed....
   *
   * @param mcOffset the instruction offset in bytes
   * @return an iterator
   */
  public OSRMapIterator getOsrMapIteratorForMCOffset(Offset mcOffset) {
    int entry = findOSREntry(mcOffset);
    int mapIndex = getOSRMapIndex(entry);
    return new OSRMapIterator(osrMaps, mapIndex);
  }

  /////////////////////////////////
  // private functions
  ////////////////////////////////
  /**
   * Do a binary search, find the entry for the machine code offset.
   *
   * @param mcOffset the instruction offset in bytes
   * @return {@link OSRConstants#NO_OSR_ENTRY} if no entry was found, the
   *  entry otherwise
   */
  private int findOSREntry(Offset mcOffset) {

    int l = 0;
    int r = lastEntry;

    while (l <= r) {
      int m = (l + r) >> 1;
      Offset offset = Offset.fromIntSignExtend(getMCOffset(m));
      if (offset.EQ(mcOffset)) {
        return m;
      } else if (offset.sLT(mcOffset)) {
        l = m + 1;
      } else {
        r = m - 1;
      }
    }

    /* this is the place should not be reached, dump OSR content */
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("cannot find map entry for ", mcOffset);
      this.printMap();
    }

    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);

    return NO_OSR_ENTRY;
  }

  private int getMCOffset(int entry) {
    return (int) ((mapEntries[entry] & OFFSET_MASK) >>> OFFSET_SHIFT);
  }

  private int getOSRMapIndex(int entry) {
    return (int) ((mapEntries[entry] & OSRI_MASK) >>> OSRI_SHIFT);
  }

  private int getBCIndex(int entry) {
    return (int) ((mapEntries[entry] & BCI_MASK) >>> BCI_SHIFT);
  }

  @SuppressWarnings("unused")
  // Here for completeness (RJG ??)
  private int getIEIndex(int entry) {
    return (int) ((mapEntries[entry] & IEI_MASK) >>> IEI_SHIFT);
  }

  private void setMCOffset(int entry, int offset) {
    mapEntries[entry] = (mapEntries[entry] & ~OFFSET_MASK) | (((long) offset) << OFFSET_SHIFT);
  }

  private void setOSRMapIndex(int entry, int index) {
    mapEntries[entry] = (mapEntries[entry] & ~OSRI_MASK) | (((long) index) << OSRI_SHIFT);
  }

  private void setBCIndex(int entry, int index) {
    mapEntries[entry] = (mapEntries[entry] & ~BCI_MASK) | (((long) index) << BCI_SHIFT);
  }

  private void setIEIndex(int entry, int index) {
    mapEntries[entry] = (mapEntries[entry] & ~IEI_MASK) | (((long) index) << IEI_SHIFT);
  }

  /**
   * print the encoded map for debugging.
   */
  public void printMap() {
    if (lastEntry > 0) {
      VM.sysWriteln("On-stack-replacement maps:");
    }
    for (int i = 0; i <= lastEntry; i++) {
      VM.sysWrite("Entry " + i + " : ");
      int mapIndex = getOSRMapIndex(i);
      VM.sysWrite("  mapIndex " + mapIndex + ", ");
      int mcOffset = getMCOffset(i);
      VM.sysWrite("  mc " + mcOffset + ", ");
      int bcIndex = getBCIndex(i);
      VM.sysWriteln("bc " + bcIndex);

      /*
      for (int j=0; j<osrMaps.length; j++) {
        VM.sysWriteHex(osrMaps[j]);VM.sysWrite(" ");
      }
      VM.sysWriteln();
      */

      // register map
      int regmap = osrMaps[mapIndex] & ~NEXT_BIT;
      VM.sysWrite("regmap: " + Integer.toBinaryString(regmap));

      OSRMapIterator iterator = new OSRMapIterator(osrMaps, mapIndex);

      while (iterator.hasMore()) {
        VM.sysWrite("(" + iterator.getValueType() + "," + iterator.getValue() + ")");
        iterator.moveToNext();
      }
      VM.sysWriteln();
    }
  }

  public int[] getMCIndexes() {
    int[] indexes = new int[mapEntries.length];
    for (int i = 0, n = mapEntries.length; i < n; i++) {
      indexes[i] = getMCOffset(i);
    }

    return indexes;
  }
}
