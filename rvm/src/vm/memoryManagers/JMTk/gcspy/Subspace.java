/**
 ** Subspace
 **
 ** An abstraction of a subspace of a Space.
 **
 ** (C) Copyright Richard Jones, 2002
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package org.mmtk.vm.gcspy;

import org.mmtk.utility.Log;

import org.vmmagic.unboxed.*;



/**
 * This class is an abstraction of a subspace of a Space.
 * For example, a semispace collector might choose to model the heap as a single
 * Space, but within that Space it can model each semispace by a Subspace.
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class Subspace 
  implements  Uninterruptible {
  public final static String Id = "$Id$";

  private Address start_;	// The Subspace spans [start_, end_)
  private Address end_;
  private int firstIndex_;	// The index of the tile in which start_ lies
  private int blockSize_;	// The tile size
  private int blockNum_;	// The number of tiles in this space

  private static final boolean DEBUG_ = false;

  /**
   * Create a new subspace
   *
   * @param start The address of the start of the subspace
   * @param end The address of the end of the subspace
   * @param firstIndex The index of the first tile of the subspace
   * @param blockSize The size of tiles in this subspace
   * @param blockNum The number of tiles in this subspace
   */
  public Subspace (Address start, 
                Address end, 
                int firstIndex, 
		int blockSize, 
		int blockNum) {
     reset(start, end, firstIndex, blockSize, blockNum);
  }

  /**
   * Reset a new subspace
   *
   * @param start The address of the start of the subspace
   * @param end The address of the end of the subspace
   * @param firstIndex The index of the first tile of the subspace
   * @param blockSize The size of tiles in this subspace
   * @param blockNum The number of tiles in this subspace
   */
  private void reset (Address start, 
                      Address end, 
                      int firstIndex, 
		      int blockSize, 
		      int blockNum) {
    reset(start, end, firstIndex, blockNum);
    blockSize_ = blockSize;
    if (DEBUG_)
      dump();
  }

  /**
   * Reset a new subspace
   *
   * @param start The address of the start of the subspace
   * @param end The address of the end of the subspace
   * @param firstIndex The index of the first tile of the subspace
   * @param blockNum The number of tiles in this subspace
   */
  public void reset (Address start, 
		     Address end, 
		     int firstIndex, 
		     int blockNum) {
    start_ = start;
    end_ = end;
    firstIndex_ = firstIndex;
    blockNum_ = blockNum;
  }

  /**
   * Reset a new subspace
   *
   * @param start The address of the start of the subspace
   * @param end The address of the end of the subspace
   * @param blockNum The number of tiles in this subspace
   */
  public void reset (Address start, Address end, int blockNum) {
    start_ = start;
    end_ = end;
    blockNum_ = blockNum;
  }
  
  /**
   * Reset a new subspace
   *
   * @param firstIndex The index of the first tile of the subspace
   * @param blockNum The number of tiles in this subspace
   */
  public void reset (int firstIndex, int blockNum) {
    firstIndex_ = firstIndex;
    blockNum_ = blockNum;
  }
  

  /**
   * Is index in range?
   * 
   * @param index The index of the tile
   * @return true if this tile lies in this subspace
   */
  public boolean indexInRange (int index) {
    return index >= firstIndex_ && 
           index < firstIndex_ + blockNum_;
  }

  /**
   * Is address in range?
   * 
   * @param addr An address
   * @return true if this address is in a tile in this subspace
   */
  public boolean addressInRange(Address addr) {
    return addr.GE(start_) && addr.LT(end_);
  }


  /** 
   * Get tile index from address
   * 
   * @param addr The address
   * @return The index of the tile holding this address
   */
  public int getIndex(Address addr) {
    return firstIndex_ + addr.diff(start_).toInt() / blockSize_;
  }

  /**
   * Get address of start of tile from its index
   *
   * @param index The index of the tile
   * @return The address of the start of the tile
   */
  public Address getAddress(int index) {
    return start_.add(index - firstIndex_ * blockSize_);
  }

  /**
   * Start of subspace
   *
   * @return The start of this subspace
   */
  public Address getStart() { return start_; }

  /**
   * End of subspace
   *
   * @return The address of the end of this subspace
   */
  public Address getEnd() { return end_; }

  /**
   * First index of subspace
   *
   * The firstIndex of this subspace
   */
  public int getFirstIndex() { return firstIndex_; }

  /**
   * Blocksize of subspace
   *
   * @return The size of a tile
   */
  public int getBlockSize() { return blockSize_; }

  /**
   * Number of tiles in this subspace
   *
   * @return The number of tiles in this subspace
   */
  public int getBlockNum() { return blockNum_; }
  
  /**
   * Space remaining in a block after this address
   *
   * @param addr the Address
   * @return the remainder
   */
  public int spaceRemaining(Address addr) {
    int nextIndex = getIndex(addr) + 1;
    Address nextTile = start_.add(blockSize_ * (nextIndex - firstIndex_));
    return nextTile.diff(addr).toInt();      
  }
  
  /**
   * Dump a representation of the subspace
   */
  private void dump() {
    Log.write("GCspy Subspace: ");
    Util.dumpRange(start_, end_);
    Log.writeln("\n  -- firstIndex=", firstIndex_);
    Log.writeln("  -- blockNum=", blockNum_);
  }
}


