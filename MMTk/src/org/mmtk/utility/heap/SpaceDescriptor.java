/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.heap;

import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class manages the e ncoding and decoding of space descriptors.<p>
 * 
 * Space descriptors are integers that encode a space's mapping into
 * virtual memory.  For discontigious spaces, they indicate
 * discontiguity and mapping must be done by consulting the space map.
 * For contigious spaces, the space's address range is encoded into
 * the integer (using a fixed point notation).<p>
 *
 * The purpose of this class is to allow <code>static final int</code>
 * space descriptors to exist for each space, which can then be used
 * in tests to determine whether an object is in a space.  A good
 * compiler can perform this decoding at compile time and produce
 * optimal code for the test.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class SpaceDescriptor implements Uninterruptible, Constants {
  private static final int VM_TYPE_BITS = 2; 
  private static final int VM_TYPE_SHARED = 0;
  private static final int VM_TYPE_CONTIGUOUS = 1;
  private static final int VM_TYPE_CONTIGUOUS_HI = 3;
  private static final int VM_TYPE_MASK = (1<<VM_TYPE_BITS)-1;
  private static final int VM_SIZE_SHIFT = VM_TYPE_BITS;
  private static final int VM_SIZE_BITS = 10;
  private static final int VM_SIZE_MASK = ((1<<VM_SIZE_BITS)-1)<<VM_SIZE_SHIFT;
  private static final int VM_EXPONENT_SHIFT = VM_SIZE_SHIFT + VM_SIZE_BITS;
  private static final int VM_EXPONENT_BITS = 5;
  private static final int VM_EXPONENT_MASK = ((1<<VM_EXPONENT_BITS)-1)<<VM_EXPONENT_SHIFT;
  private static final int VM_MANTISSA_SHIFT = VM_EXPONENT_SHIFT + VM_EXPONENT_BITS;
  private static final int VM_MANTISSA_BITS = 14;
  private static final int VM_BASE_EXPONENT = BITS_IN_INT - VM_MANTISSA_BITS;

  public static int getDescriptor(Address start, Address end) {
    int chunks = end.diff(start).toWord().rshl(Space.LOG_BYTES_IN_CHUNK).toInt();
    if (Assert.VERIFY_ASSERTIONS) 
      Assert._assert(!start.isZero() && chunks > 0 
                     && chunks < (1<<VM_SIZE_BITS));
    boolean top = end.EQ(Space.HEAP_END);
    Word tmp = start.toWord();
    tmp = tmp.rshl(VM_BASE_EXPONENT);
    int exponent = 0;
    while (!tmp.isZero() && tmp.and(Word.one()).isZero()) {
      tmp = tmp.rshl(1);
      exponent++;
    }
    int mantissa = tmp.toInt();
    if (Assert.VERIFY_ASSERTIONS) 
      Assert._assert(mantissa<<(VM_BASE_EXPONENT + exponent) == start.toInt());
    return (mantissa<<VM_MANTISSA_SHIFT)
      | (exponent<<VM_EXPONENT_SHIFT) 
      | (chunks<<VM_SIZE_SHIFT)
      | ((top) ? VM_TYPE_CONTIGUOUS_HI : VM_TYPE_CONTIGUOUS);
  }

  public static int getDescriptor() {
    return VM_TYPE_SHARED;
  }

  public static boolean isContiguous(int descriptor) throws InlinePragma {
    return ((descriptor & VM_TYPE_CONTIGUOUS) == VM_TYPE_CONTIGUOUS);
  }

  public static boolean isContiguousHi(int descriptor) throws InlinePragma {
    return ((descriptor & VM_TYPE_MASK) == VM_TYPE_CONTIGUOUS_HI);
  }

  public static Address getStart(int descriptor) throws InlinePragma {
    Word mantissa = Word.fromInt(descriptor>>>VM_MANTISSA_SHIFT);
    int exponent = (descriptor & VM_EXPONENT_MASK)>>>VM_EXPONENT_SHIFT;
    return mantissa.lsh(VM_BASE_EXPONENT+exponent).toAddress();
  }
   
  public static int getChunks(int descriptor) throws InlinePragma {
    return (descriptor & VM_SIZE_MASK)>>>VM_SIZE_SHIFT;
  }
}
