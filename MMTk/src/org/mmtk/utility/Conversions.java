/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package org.mmtk.utility;

import org.mmtk.utility.heap.*;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/*
import com.ibm.JikesRVM.VM_Offset;
 * Conversions between different units.
 *
 * @author Perry Cheng
 */
public class Conversions implements Constants, Uninterruptible {

//   public static Address roundDownVM(Address addr) {
//     return roundDown(addr.toWord(), VMResource.LOG_BYTES_IN_VM_REGION).toAddress();
//   }

//   public static Extent roundDownVM(Extent bytes) {
//     return roundDown(bytes.toWord(), VMResource.LOG_BYTES_IN_VM_REGION).toExtent();
//   }

  public static Address roundDownMB(Address addr) {
    return roundDown(addr.toWord(), LOG_BYTES_IN_MBYTE).toAddress();
  }

  public static Extent roundDownMB(Extent bytes) {
    return roundDown(bytes.toWord(), LOG_BYTES_IN_MBYTE).toExtent();
  }

  private static Word roundDown(Word value, int logBase) {
    Word mask = Word.one().lsh(logBase).sub(Word.one()).not();
    return value.and(mask);
  }

  // Round up (if necessary)
  //
  public static int MBToPages(int megs) {
    if (LOG_BYTES_IN_PAGE <= LOG_BYTES_IN_MBYTE)
      return (megs << (LOG_BYTES_IN_MBYTE - LOG_BYTES_IN_PAGE));
    else
      return (megs + ((BYTES_IN_PAGE >>> LOG_BYTES_IN_MBYTE) - 1)) >>> (LOG_BYTES_IN_PAGE - LOG_BYTES_IN_MBYTE);
  }

  public static int bytesToMmapChunksUp(Extent bytes) {
    return bytes.add(LazyMmapper.MMAP_CHUNK_SIZE - 1).toWord().rshl(LazyMmapper.LOG_MMAP_CHUNK_SIZE).toInt();
  }

  public static int pagesToMmapChunksUp(int pages) {
    return bytesToMmapChunksUp(pagesToBytes(pages));
  }

  public static int addressToMmapChunksDown (Address addr) {
    Word chunk = addr.toWord().rshl(LazyMmapper.LOG_MMAP_CHUNK_SIZE);
    return chunk.toInt();
  }

  public static int addressToPagesDown (Address addr) {
    Word chunk = addr.toWord().rshl(LOG_BYTES_IN_PAGE);
    return chunk.toInt();
  }

  public static int addressToPages (Address addr) {
    int page = addressToPagesDown(addr);
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(pagesToAddress(page).EQ(addr));
    return page;
  }

  public static Address pagesToAddress (int pages) {
    return Word.fromIntZeroExtend(pages).lsh(LOG_BYTES_IN_PAGE).toAddress();
  }

  public static int addressToMmapChunksUp (Address addr) {
    Word chunk = addr.add(LazyMmapper.MMAP_CHUNK_SIZE - 1).toWord().rshl(LazyMmapper.LOG_MMAP_CHUNK_SIZE);
    return chunk.toInt();
  }

  public static Extent pagesToBytes(int pages) {
    return Word.fromIntZeroExtend(pages).lsh(LOG_BYTES_IN_PAGE).toExtent();
  }

  public static int pagesToMBytes(int pages) {
    return pages >> (LOG_BYTES_IN_MBYTE - LOG_BYTES_IN_PAGE);
  }

  public static int pagesToKBytes(int pages) {
    return pages << (LOG_BYTES_IN_PAGE - LOG_BYTES_IN_KBYTE);
  }

  /**
    @deprecated : use int bytesToPagesUp(Extent bytes) if possible
  */
  public static int bytesToPagesUp(int bytes) {
    return bytesToPagesUp(Extent.fromIntZeroExtend(bytes));
  }
  
  /**
    @deprecated : use int bytesToPagesUp(Extent bytes) if possible
  */
  public static int bytesToPages(int bytes) {
    return bytesToPages(Extent.fromIntZeroExtend(bytes));
  }
  
  public static int bytesToPagesUp(Extent bytes) {
    return bytes.add(BYTES_IN_PAGE-1).toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
  }
  
  public static int bytesToPages(Extent bytes) {
    int pages = bytesToPagesUp(bytes);
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(pagesToAddress(pages).toWord().toExtent().EQ(bytes));
    return pages;
  }

  public static int bytesToPages(Offset bytes) {
    if (Assert.VERIFY_ASSERTIONS) {
      long val = bytes.toLong();
      Assert._assert(val >= MIN_INT && val <= MAX_INT);
    }
    if (bytes.sGE(Offset.zero()))
      return bytesToPagesUp(bytes.toInt());
    else
      return -bytesToPagesUp(-bytes.toInt());
  }
  
  public static Address mmapChunksToAddress(int chunk) {
    return Word.fromIntZeroExtend(chunk).lsh(LazyMmapper.LOG_MMAP_CHUNK_SIZE).toAddress();
  }

  public static Address pageAlign(Address address) {
    return address.toWord().rshl(LOG_BYTES_IN_PAGE).lsh(LOG_BYTES_IN_PAGE).toAddress();
  }

  public static boolean isPageAligned(Address address) {
    return pageAlign(address).EQ(address);
  }
}
