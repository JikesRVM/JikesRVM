/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;


/*
 * Conversions between different units.
 *
 * @author Perry Cheng
 */
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
public class Conversions implements Constants, VM_Uninterruptible {

  public static VM_Extent roundDownMB (VM_Extent bytes) {
    return VM_Extent.fromInt((bytes.toInt() >>> LOG_BYTES_IN_MBYTE) << LOG_BYTES_IN_MBYTE);
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
    return (addr.toInt()) >>> LazyMmapper.LOG_MMAP_CHUNK_SIZE;
  }

  public static int addressToPagesDown (VM_Address addr) {
    return (addr.toInt()) >>> LOG_BYTES_IN_PAGE;
  }

  public static int addressToPages (VM_Address addr) {
    int page = addressToPagesDown(addr);
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(pagesToAddress(page).EQ(addr));
    return page;
  }

  public static VM_Address pagesToAddress (int pages) {
    return VM_Address.fromInt(pages << LOG_BYTES_IN_PAGE);
  }

  public static int addressToMmapChunksUp (VM_Address addr) {
    return ((addr.toInt()) + (LazyMmapper.MMAP_CHUNK_SIZE - 1)) >>> LazyMmapper.LOG_MMAP_CHUNK_SIZE;
  }

  public static int pagesToBytes(int pages) {
    return pages << LOG_BYTES_IN_PAGE;
  }

  public static int bytesToPagesUp (int bytes) {
    return (bytes + BYTES_IN_PAGE - 1) >>> LOG_BYTES_IN_PAGE;
  }

  public static int bytesToPages (int bytes) {
    int pages = bytesToPagesUp(bytes);
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(pagesToBytes(pages) == bytes);
    return pages;
  }

  public static VM_Address mmapChunksToAddress(int chunk) {
    return (VM_Address.fromInt(chunk << LazyMmapper.LOG_MMAP_CHUNK_SIZE));
  }



}
