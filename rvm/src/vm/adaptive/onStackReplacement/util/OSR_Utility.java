/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

/**
 * A utility class for on stack replacement
 *
 * @author Feng Qian
 */
public class OSR_Utility {

  /**
   * Performs quick sort of integers in place, result from 
   * min to max
   */
  public static void quickSort(int[] data) {
    int start = 0, end = data.length-1;
    _quickSort(data, start, end);
  } 


  private static void _quickSort(int[] array, int start, int end) {
    if ( start < end ) {
      int pivot = _partition(array, start, end );
      _quickSort( array, start, pivot );
      _quickSort( array, pivot+1, end );
    }
  }

  private static final int _partition(int[] array,
                                     int start,
                                     int end) {
    int left = start;
    int right = end;
    int pivot = start;
 
    int pivot_key = array[pivot];
    while ( true ) {
      /* Move right while item > pivot */
      while (array[right] > pivot_key) right--;
 
      /* Move left while item < pivot */
      while( array[left] < pivot_key ) left++;
 
      if ( left < right ) {
        /* swap left and right */
        int temp = array[left];
        array[left] = array[right];
        array[right] = temp;
      } else {
        return right;
      }
    }
  }

  /**
   * Performs quick search of a value in a sorted array, 
   * returns the index if value is in the array
   *         -1, otherwise
   */
  public static int quickSearch(int[] array, int value) {
    int low = 0;
    int high = array.length-1;
    while (low <= high) {
      int mid = (low + high) >> 1;
      int bci = array[mid];
      if (bci == value) {
	return mid;
      }
      if (bci > value) {
	high = mid-1;
      } else {
	low = mid+1;
      }   
    }
    return -1;
  }

  /**
   * Disassemble instruction array
   */
  public static final void disassemble(VM_CodeArray instructions) {
    //-#if RVM_FOR_POWERPC
    for (int i=0; i<instructions.length; i++) {
      VM.sysWrite(i + " : " + PPC_Disassembler.disasm(instructions,get(i),0)+"\n");
    }
    //-#endif

    //-#if RVM_FOR_IA32
    IntelDisassembler.disasm(instructions, 0, 0);
    //-#endif
  }
}
