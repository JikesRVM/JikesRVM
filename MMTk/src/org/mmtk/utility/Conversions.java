/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Memory;

/*
 * Conversions between different units.
 *
 * @author Perry Cheng
 */
public class Conversions implements Constants, VM_Uninterruptible {

  // Round up (if necessary)
  //
  public static int MBToBlocks(EXTENT megs) {
    if (VMResource.LOG_BLOCK_SIZE <= LOG_MBYTE_SIZE)
      return (megs << (LOG_MBYTE_SIZE - VMResource.LOG_BLOCK_SIZE));
    else
      return (megs + ((VMResource.BLOCK_SIZE >> LOG_MBYTE_SIZE) - 1)) >>> (VMResource.LOG_BLOCK_SIZE - LOG_MBYTE_SIZE);
  }

  // Round up
  //
  public static int bytesToBlocks(EXTENT bytes) {
    return (bytes + (VMResource.BLOCK_SIZE - 1)) >>> VMResource.LOG_BLOCK_SIZE;
  }

  // Round up
  //
  public static int bytesToMmapChunk(EXTENT bytes) {
    return (bytes + (LazyMmapper.MMAP_CHUNK_SIZE - 1)) >>> LazyMmapper.LOG_MMAP_CHUNK_SIZE;
  }

  // Round up
  //
  public static int blocksToMmapChunks(int blocks) {
    return bytesToMmapChunk(blocks << VMResource.LOG_BLOCK_SIZE);
  }

  // No rounding needed
  //
  public static int blocksToPages(int blocks) {
    return (blocks << VMResource.LOG_PAGES_PER_BLOCK);
  }

  // Round down
  //
  public static int addressToMmapChunks(VM_Address addr) {
    return (addr.toInt()) >> LazyMmapper.LOG_MMAP_CHUNK_SIZE;
  }

  // Round down
  //
  public static int addressToBlocks(VM_Address addr) {
    return (addr.toInt() >> VMResource.LOG_BLOCK_SIZE);
  }

  // No rounding needed
  //
  public static int blocksToBytes(int blocks) {
    return blocks << VMResource.LOG_BLOCK_SIZE;
  }

  // No rounding needed
  //
  public static int pagesToBytes(int pages) {
    return pages << LOG_PAGE_SIZE;
  }

  // Round up
  //
  public static int bytesToPages(int bytes) {
    return (bytes + PAGE_SIZE - 1) >> LOG_PAGE_SIZE;
  }

  // Round up
  //
  public static int pagesToBlocks(int pages) {
    int pagesInBlock = 1 << (VMResource.LOG_BLOCK_SIZE - LOG_PAGE_SIZE);
    return (pages + (pagesInBlock - 1)) >> (VMResource.LOG_BLOCK_SIZE - LOG_PAGE_SIZE);
  }

  // No rounding needed
  //
  public static VM_Address blocksToAddress(int blocks) {
    return VM_Address.fromInt(blocks << VMResource.LOG_BLOCK_SIZE);
  }

  // No rounding needed
  //
  public static VM_Address mmapChunksToAddress(int chunk) {
    return (VM_Address.fromInt(chunk << LazyMmapper.LOG_MMAP_CHUNK_SIZE));
  }



}
