/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM;

/*
 * Conversions between different units.
 *
 * @author Perry Cheng
 */
public class Conversions implements Constants, VM_Uninterruptible {

  public static VM_Extent roundDownMB (VM_Extent bytes) {
    return VM_Extent.fromInt((bytes.toInt() >>> LOG_MBYTE_SIZE) << LOG_MBYTE_SIZE);
  }

  // Round up (if necessary)
  //
  public static int MBToPages(EXTENT megs) {
    if (VMResource.LOG_PAGE_SIZE <= LOG_MBYTE_SIZE)
      return (megs << (LOG_MBYTE_SIZE - VMResource.LOG_PAGE_SIZE));
    else
      return (megs + ((VMResource.PAGE_SIZE >>> LOG_MBYTE_SIZE) - 1)) >>> (VMResource.LOG_PAGE_SIZE - LOG_MBYTE_SIZE);
  }

  public static int bytesToMmapChunksUp(EXTENT bytes) {
    return (bytes + (LazyMmapper.MMAP_CHUNK_SIZE - 1)) >>> LazyMmapper.LOG_MMAP_CHUNK_SIZE;
  }

  public static int pagesToMmapChunksUp(int pages) {
    return bytesToMmapChunksUp(pagesToBytes(pages));
  }

  public static int addressToMmapChunksDown (VM_Address addr) {
    return (addr.toInt()) >>> LazyMmapper.LOG_MMAP_CHUNK_SIZE;
  }

  public static int addressToPagesDown (VM_Address addr) {
    return (addr.toInt()) >>> LOG_PAGE_SIZE;
  }

  public static int addressToPages (VM_Address addr) {
    int page = addressToPagesDown(addr);
    if (VM.VerifyAssertions) VM._assert(pagesToAddress(page).EQ(addr));
    return page;
  }

  public static VM_Address pagesToAddress (int pages) {
    return VM_Address.fromInt(pages << LOG_PAGE_SIZE);
  }

  public static int addressToMmapChunksUp (VM_Address addr) {
    return ((addr.toInt()) + (LazyMmapper.MMAP_CHUNK_SIZE - 1)) >>> LazyMmapper.LOG_MMAP_CHUNK_SIZE;
  }

  public static int pagesToBytes(int pages) {
    return pages << LOG_PAGE_SIZE;
  }

  public static int bytesToPagesUp (int bytes) {
    return (bytes + PAGE_SIZE - 1) >>> LOG_PAGE_SIZE;
  }

  public static int bytesToPages (int bytes) {
    int pages = bytesToPagesUp(bytes);
    if (VM.VerifyAssertions) VM._assert(pagesToBytes(pages) == bytes);
    return pages;
  }

  public static VM_Address mmapChunksToAddress(int chunk) {
    return (VM_Address.fromInt(chunk << LazyMmapper.LOG_MMAP_CHUNK_SIZE));
  }



}
