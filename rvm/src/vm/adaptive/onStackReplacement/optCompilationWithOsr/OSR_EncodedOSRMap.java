/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/** 
 * OSR_EncodedOSRMap provides the samilar function as GC map
 * in VM_OptMachineCodeMap.
 * 
 * In VM_OptCompiledMethod, an instance of this class will represent
 * all OSR map info for that method.
 *
 * @author  Feng Qian
 */

public class OSR_EncodedOSRMap 
  implements VM_OptGCMapIteratorConstants,
             OSR_Constants {
  
  /* osr info entries */
  private long[] mapEntries;

  /* the last entry index. */
  private int lastEntry;
  
  /* the OSR map */
  private int[] osrMaps;
  private int mapSize   = 16;
  private int lastIndex = 0;

  /* the inlining encoding */
  private int[] ieMaps;

  public static final boolean registerIsSet(int map, int regnum) 
    throws InlinePragma {

    int bitpos = getRegBitPosition(regnum);
    return (map & (NEXT_BIT >>> bitpos)) > 0;
  }

  /*
   * mark a register as reference type
   */
  private static int setRegister(int map, int regnum) {
    int bitpos = getRegBitPosition(regnum);
    map |= (NEXT_BIT >>> bitpos);
    return map;
  }

  /*
   * get register bit position
   */
  private static final int getRegBitPosition(int regnum) 
    throws InlinePragma {

    return regnum - FIRST_GCMAP_REG + 1;
  }

  /* 
   *
   */
  public OSR_EncodedOSRMap(OSR_VariableMap varMap) {
    int entries = varMap.getNumberOfElements();
    
    this.lastEntry = entries-1;

    if (entries > 0) {
      this.mapEntries = new long[entries];
      this.osrMaps    = new int[mapSize];
      translateMap(varMap.list);
      resizeOsrMaps();
    }

        /*
    if (VM.TraceOnStackReplacement) {
      printMap();
    }
        */
  }

  /*
   * translates a list of OSR_MapElement to encoding,
   * we can not trust the osrlist is in the increasing order of 
   * machine code offset. Sort it first.
   */
  private void translateMap(LinkedList osrlist) {

    /* sort the list, use the mc offset of the index instruction 
     * as the key.
     */
    int n = osrlist.size();

    OSR_VariableMapElement[] osrarray = new OSR_VariableMapElement[n];
    for (int i=0; i<n; i++) {
      osrarray[i] = (OSR_VariableMapElement)osrlist.get(i);
    }

    /* ideally, the osrList should be in sorted order by MC offset,
     * but I got once it is not in the order. To work correctly,
     * sort it first.
     * 
     * TODO: figure out why LiveAnalysis does not give correct order?
     */
    quickSort(osrarray, 0, n-1);

    // make inline encoding, OSR maps,
    OPT_CallSiteTree inliningTree = new OPT_CallSiteTree();
    for (int i=0; i<n; i++) {
      OPT_Instruction instr = osrarray[i].osr;      
      // add lining element, move sanity later
      if (instr.position != null) { 
        inliningTree.addLocation(instr.position);
      }
    }

    //get inlining encoding
    ieMaps = VM_OptEncodedCallSiteTree.getEncoding(inliningTree);

    for (int i=0; i<n; i++) {

      OSR_VariableMapElement elm = osrarray[i];      
      OPT_Instruction instr = elm.osr;

      int iei = inliningTree.find(instr.position).encodedOffset;
      setIEIndex(i, iei);

      // get osr map 
      LinkedList mVarList = elm.mvars;
      int osrMapIndex = generateOsrMaps(mVarList);
      
      // use this offset, and adjust on extractState
      int mcOffset = instr.getmcOffset();
      setMCOffset(i, mcOffset); 
      setOSRMapIndex(i, osrMapIndex);
      setBCIndex(i, instr.getBytecodeIndex());
    }
  }

  // use the mc offset as key, correctly we should use the next
  // instruction's mc offset as the key, but since there are
  // in the order, we will use the current instruction's mc offset
  // as the key.
  private static final void quickSort(OSR_VariableMapElement[] array,
                                      int start,
                                      int end) {
    if ( start < end ) {
      int pivot = partition(array, start, end );
      quickSort( array, start, pivot );
      quickSort( array, pivot+1, end );
    }
  }

  private static final int partition(OSR_VariableMapElement[] array,
                                     int start,
                                     int end) {
    int left = start;
    int right = end;
    int pivot = start;
   
    OSR_VariableMapElement pivot_elm = array[pivot];
    int pivot_offset = pivot_elm.osr.getmcOffset();
    while ( true ) {
      /* Move right while item > pivot */
      while (array[right].osr.getmcOffset() > pivot_offset) right--;

      /* Move left while item < pivot */ 
      while( array[left].osr.getmcOffset() < pivot_offset ) left++;

      if ( left < right ) {
        /* swap left and right */
        OSR_VariableMapElement temp = array[left];
        array[left] = array[right];
        array[right] = temp;
      } else {
        return right;
      }
    }
  }

  /* generate value in the Osr map,
   * return the index of the first integer in the map.
   * 
   * An OSR Map has following structure:
   * | regmap || mid, mpc, (n1, n2) ... ||
   *          || mid, mpc, (n1, n2) ... ||
   * Regmap indicates the value of which register is a reference,
   * the execution state extractor can convert the value to an
   * object to avoid confusing GC.
   * The MSB of regmap indicates next mid is valid. 
   *
   * The MSB of mid indicates if the next mid item will be 
   * available. 
   *
   * The MSB of mpc indicates if the next is a valid pair
   */
  private int generateOsrMaps(LinkedList mVarList) {

    int regmap = (mVarList.size() > 0)? NEXT_BIT:0;
    int mapIndex = addIntToOsrMap(regmap);

    // from inner to outer
    for (int i=0, m=mVarList.size(); i<m; i++) {
      OSR_MethodVariables mVar = 
        (OSR_MethodVariables)mVarList.get(i);
      _generateMapForOneMethodVariable(mapIndex, mVar, (i==(m-1)));
    }

    return mapIndex;
  }

  /* @param regMapIndex, used to patch the register map
   * @param mVar, the method variables
   */
  private void _generateMapForOneMethodVariable(int regMapIndex,
                                                OSR_MethodVariables mVar,
                                                boolean lastMid) {
    // Is this the last method in the inlined chain?
    int mid = lastMid ? mVar.methId : (mVar.methId | NEXT_BIT);
    addIntToOsrMap(mid);

    LinkedList tupleList = mVar.tupleList;
    int m = tupleList.size();

    // Is this method has variables?
    int bci = (m == 0) ? mVar.bcIndex : (mVar.bcIndex | NEXT_BIT);
    addIntToOsrMap(bci);

    // append each element 
    for (int j=0; j<m; j++) {
      OSR_LocalRegPair tuple = 
        (OSR_LocalRegPair)tupleList.get(j);

      boolean isLast = (j == m-1);
        
      processTuple(tuple, isLast);
      // mark the reg ref map
      if ( ((tuple.typeCode == ClassTypeCode) 
            ||(tuple.typeCode == ArrayTypeCode))
           &&(tuple.valueType == PHYREG)) {
        osrMaps[regMapIndex] = 
          setRegister(osrMaps[regMapIndex], 
                      tuple.value.toInt());
      }
    }
  }

  /*
   * process on 32-bit tuple.
   * 
   * tuple, maps the local to register, spill
   * isLast, indicates to set NEXT_BIT
   */
  private void processTuple(OSR_LocalRegPair tuple, 
                            boolean isLast) {

    int first = (tuple.num << NUM_SHIFT) & NUM_MASK;

    if (!isLast) {
      first |= NEXT_BIT;
    }

    first |= (tuple.kind << KIND_SHIFT);

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
        //split in two integer parts for OSR map
        /* process the first half part, 
         * it is not the last. */
        first |= NEXT_BIT;
        first |= (HIGH_64BIT << TCODE_SHIFT);
       
        // add first word
        addIntToOsrMap(first);
        // add the second word
   
        if (VM.BuildFor64Addr) {
          addIntToOsrMap(tuple.value.rshl(32).toInt()); 
        } else {
          addIntToOsrMap(tuple.value.toInt()); 
          tuple = tuple._otherHalf;
        } 
        /* process the second half part,
         * it may be the last, and it is not the first half.*/
        first = (tuple.num << NUM_SHIFT) & NUM_MASK;
    
        if (!isLast) first |= NEXT_BIT;
       
        first |= (tuple.kind << KIND_SHIFT);
        first |= (tuple.valueType << VTYPE_SHIFT);
      }
      first |= (LONG << TCODE_SHIFT);
      break;
    case ReturnAddressTypeCode:

      //-#if RVM_WITH_DEBUG
      VM.sysWrite("returnaddress type for ");
      if (tuple.kind == LOCAL) {
        VM.sysWrite("L"+tuple.num);
      } else {
        VM.sysWrite("S"+tuple.num);
      } 
      VM.sysWrite("\n");
      //-#endif
      
      first |= (RET_ADDR << TCODE_SHIFT);
      break;
    case WordTypeCode:
      if (VM.BuildFor64Addr && (tuple.valueType == ICONST)) {//KV:TODO
        //split in two integer parts for OSR map
        /* process the first half part, 
         * it is not the last. */
        first |= NEXT_BIT;
        first |= (HIGH_64BIT << TCODE_SHIFT);
       
        // add first word
        addIntToOsrMap(first);
        // add the second word
        addIntToOsrMap(tuple.value.rshl(32).toInt());
      
        /* process the second half part,
         * it may be the last, and it is not the first half.*/
        first = (tuple.num << NUM_SHIFT) & NUM_MASK;
        if (!isLast) first |= NEXT_BIT;
        first |= (tuple.kind << KIND_SHIFT);
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
    addIntToOsrMap(first);
    // add the second word
    addIntToOsrMap(tuple.value.toInt()); 
  }

  /* add an int to osrMaps, expand the array if necessary.
   * return the index of the word.
   */
  private int addIntToOsrMap(int value) {
    if (lastIndex >= mapSize) {
      // double the size
      int oldSize = mapSize;
      mapSize <<= 1;
      int[] oldMaps = osrMaps;
      osrMaps = new int[mapSize];
      
      System.arraycopy(oldMaps, 0, osrMaps, 0, oldSize);
    }
    
    osrMaps[lastIndex++] = value;
    
    return lastIndex-1;
  }

  private void resizeOsrMaps() {
    if (VM.VerifyAssertions) VM._assert(mapSize == osrMaps.length);

    if (lastIndex < mapSize-1) {
      int[] newMaps = new int[lastIndex];
      System.arraycopy(osrMaps,
                       0,
                       newMaps,
                       0,
                       lastIndex);
      osrMaps = newMaps;
      mapSize = lastIndex;
    }
  }

  ////////////////////////////////////
  // INTERFACE 
  ///////////////////////////////////
  /* 
   * does the OSR map exist for a machine instruction offset
   */
  public final boolean hasOSRMap(Offset mcOffset) {
    int entry = findOSREntry(mcOffset);
    return (entry != NO_OSR_ENTRY);
  }

  /* WARNING:
   * It is the caller's reposibility to make sure there are OSR
   * entry exist for a machine instruction offset.
   */
  /* 
   * get bytecode index for a given instruction offset in bytes.
   */
  public final int getBytecodeIndexForMCOffset(Offset mcOffset) {
    int entry = findOSREntry(mcOffset);
    return getBCIndex(entry);
  }

  /* TODO!
   * get inline encoding index for the machine instruction offset
   */
  public final int getInlineEncodingForMCOffset(Offset mcOffset) {
      return -1;
  }

  /*
   * get register's reference map for the machine instruction offset
   */
  public final int getRegisterMapForMCOffset(Offset mcOffset) {
    int entry    = findOSREntry(mcOffset);
    int mapIndex = getOSRMapIndex(entry);
    return osrMaps[mapIndex];
  }

  /**
   * given a MC offset, return an iterator over the
   * elements of this map.
   * NOTE: the map index is gotten from 'findOSRMapIndex'.
   * This has to be changed....
   */
  public final OSR_MapIterator getOsrMapIteratorForMCOffset(Offset mcOffset) {
    int entry    = findOSREntry(mcOffset);
    int mapIndex = getOSRMapIndex(entry);
    return new OSR_MapIterator(osrMaps, mapIndex);
  }

  /////////////////////////////////
  // private functions
  ////////////////////////////////
  /* 
   * Do a binary search, find the entry for the machine code offset. 
   * Return -1 if no entry was found.
   */
  private final int findOSREntry(Offset mcOffset) {
    
    int l = 0;
    int r = lastEntry;
    
    while (l <= r) {
      int m = (l+r) >> 1;
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
      VM.sysWrite("cannot find map entry for ", mcOffset, "\n");
      this.printMap();
    }

    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);

    return NO_OSR_ENTRY;
  }

  private final int getMCOffset(int entry) {
    return (int)((mapEntries[entry] & OFFSET_MASK) >>> OFFSET_SHIFT);
  }

  private final int getOSRMapIndex(int entry) {
    return (int)((mapEntries[entry] & OSRI_MASK) >>> OSRI_SHIFT);
  }

  private final int getBCIndex(int entry) {
    return (int)((mapEntries[entry] & BCI_MASK) >>> BCI_SHIFT);
  }

  private final int getIEIndex(int entry) {
    return (int)((mapEntries[entry] & IEI_MASK) >>> IEI_SHIFT);
  }

  private final void setMCOffset(int entry, int offset) {
    mapEntries[entry] = (mapEntries[entry] & ~OFFSET_MASK) 
      | (((long)offset) << OFFSET_SHIFT);
  }
 
  private final void setOSRMapIndex(int entry, int index) {
    mapEntries[entry] = (mapEntries[entry] & ~OSRI_MASK)
      | (((long)index) << OSRI_SHIFT);
  }

  private final void setBCIndex(int entry, int index) {
    mapEntries[entry] = (mapEntries[entry] & ~BCI_MASK)
      | (((long)index) << BCI_SHIFT);
  }

  private final void setIEIndex(int entry, int index) {
    mapEntries[entry] = (mapEntries[entry] & ~IEI_MASK)
      | (((long)index) << IEI_SHIFT);
  }

  /*
   * print the encoded map for debugging. 
   */
  public void printMap() {
    if (lastEntry > 0) {
      VM.sysWrite("On-stack-replacement maps:\n");
    }
    for (int i=0; i<=lastEntry; i++) {
      VM.sysWrite("Entry "+i+" : ");
      int mapIndex = getOSRMapIndex(i);
      VM.sysWrite("  mapIndex "+mapIndex+", ");
      int mcOffset = getMCOffset(i);
      VM.sysWrite("  mc "+mcOffset+", ");
      int bcIndex  = getBCIndex(i);
      VM.sysWriteln("bc "+bcIndex);

      /*
      for (int j=0; j<osrMaps.length; j++) {
        VM.sysWriteHex(osrMaps[j]);VM.sysWrite(" ");
      }
      VM.sysWrite("\n");
      */

      // register map
      int regmap = osrMaps[mapIndex] & ~NEXT_BIT;
      VM.sysWrite("regmap: "+Integer.toBinaryString(regmap));

      OSR_MapIterator iterator =
        new OSR_MapIterator(osrMaps, mapIndex);

      while (iterator.hasMore()) {
        VM.sysWrite("("+iterator.getValueType()+","+iterator.getValue()+")");
        iterator.moveToNext();
      }
      VM.sysWrite("\n");
    }
  }

  public int[] getMCIndexes() {
    int[] indexes = new int[mapEntries.length];
    for (int i=0, n=mapEntries.length; i<n; i++) {
      indexes[i] = getMCOffset(i);
    }
        
    return indexes;
  }
}
