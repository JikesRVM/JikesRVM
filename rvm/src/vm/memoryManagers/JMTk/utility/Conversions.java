/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Extent;


/*
 * Conversions between different units.
 *
 * @author Perry Cheng
 */
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
public class Conversions implements Constants, VM_Uninterruptible {

  public static VM_Extent roundDownMB (VM_Extent bytes) {
    VM_Word mask = VM_Word.one().lsh(LOG_BYTES_IN_MBYTE).sub(VM_Word.one()).not();
    return bytes.toWord().and(mask).toExtent();
  }

  // Round up (if necessary)
  //
  public static int MBToPages(int megs) {
    if (VMResource.LOG_BYTES_IN_PAGE <= LOG_BYTES_IN_MBYTE)
      return (megs << (LOG_BYTES_IN_MBYTE - VMResource.LOG_BYTES_IN_PAGE));
    else
      return (megs + ((VMResource.BYTES_IN_PAGE >>> LOG_BYTES_IN_MBYTE) - 1)) >>> (VMResource.LOG_BYTES_IN_PAGE - LOG_BYTES_IN_MBYTE);
  }

  public static int bytesToMmapChunksUp(int bytes) {
    return (bytes + (LazyMmapper.MMAP_CHUNK_SIZE - 1)) >>> LazyMmapper.LOG_MMAP_CHUNK_SIZE;
  }

  public static int pagesToMmapChunksUp(int pages) {
    return bytesToMmapChunksUp(pagesToBytes(pages));
  }

  public static int addressToMmapChunksDown (VM_Address addr) {
    VM_Word chunk = addr.toWord().rshl(LazyMmapper.LOG_MMAP_CHUNK_SIZE);
    return chunk.toInt();
  }

  public static int addressToPagesDown (VM_Address addr) {
    VM_Word chunk = addr.toWord().rshl(LOG_BYTES_IN_PAGE);
    return chunk.toInt();
  }

  public static int addressToPages (VM_Address addr) {
    int page = addressToPagesDown(addr);
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(pagesToAddress(page).EQ(addr));
    return page;
  }

  public static VM_Address pagesToAddress (int pages) {
    return VM_Word.fromIntZeroExtend(pages).lsh(LOG_BYTES_IN_PAGE).toAddress();
  }

  public static int addressToMmapChunksUp (VM_Address addr) {
    VM_Word chunk = addr.add(LazyMmapper.MMAP_CHUNK_SIZE - 1).toWord().rshl(LazyMmapper.LOG_MMAP_CHUNK_SIZE);
    return chunk.toInt();
  }

  public static int pagesToBytes(int pages) {
    return pages << LOG_BYTES_IN_PAGE;
  }

  public static int bytesToPagesUp(int bytes) {
    return bytesToPagesUp(VM_Extent.fromInt(bytes));
  }
  
  public static int bytesToPages(int bytes) {
    return bytesToPages(VM_Extent.fromInt(bytes));
  }
  
  public static int bytesToPagesUp(VM_Extent bytes) {
    return bytes.add(BYTES_IN_PAGE-1).toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
  }
  
  public static int bytesToPages(VM_Extent bytes) {
    int pages = bytesToPagesUp(bytes);
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(pagesToAddress(pages).toWord().toExtent().EQ(bytes));
    return pages;
  }

  public static VM_Address mmapChunksToAddress(int chunk) {
    return VM_Word.fromIntZeroExtend(chunk).lsh(LazyMmapper.LOG_MMAP_CHUNK_SIZE).toAddress();
  }



}
