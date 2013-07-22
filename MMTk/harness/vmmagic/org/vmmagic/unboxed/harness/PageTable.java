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
package org.vmmagic.unboxed.harness;

import static org.vmmagic.unboxed.harness.MemoryConstants.LOG_BYTES_IN_PAGE;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.vmmagic.unboxed.Address;

final class PageTable {
  private final ConcurrentMap<Long, MemoryPage> pages = new ConcurrentHashMap<Long, MemoryPage>();

  private long pageTableEntry(Address p) {
    return p.toLong() >>> LOG_BYTES_IN_PAGE;
  }

  /**
   * Internal: get a page by page number, performing appropriate
   * checking and synchronization
   * @param p
   * @return
   */
  MemoryPage getPage(Address p) {
    MemoryPage page = pages.get(pageTableEntry(p));
    if (page == null) {
      throw new Error("Page not mapped: " + p);
    } else if (!page.readable) {
      throw new Error("Page not readable: " + p);
    }
    return page;
  }

  void setReadable(Address p) {
    MemoryPage page = pages.get(pageTableEntry(p));
    if (page == null) {
      throw new Error("Page not mapped: " + p);
    }
    page.readable = true;
  }

  void setNonReadable(Address p) {
    MemoryPage page = pages.get(pageTableEntry(p));
    if (page == null) {
      throw new Error("Page not mapped: " + p);
    }
    page.readable = false;
  }

  void mapPage(Address p) {
    long page = pageTableEntry(p);
    Trace.trace(Item.MEMORY,"Mapping page %s%n", p);
    MemoryPage newPage = new MemoryPage(p);
    if (pages.putIfAbsent(page, newPage) != null) {
      throw new Error("Page already mapped: " + p);
    }
  }

  void unmapPage(Address p) {
    long page = pageTableEntry(p);
    Trace.trace(Item.MEMORY,"Unmapping page %s%n", p);
    if (pages.get(page) != null) {
      pages.remove(page);
    }
  }

  void zeroPage(Address p) {
    MemoryPage page = pages.get(pageTableEntry(p));
    if (page == null) {
      throw new Error("Page not mapped: " + p);
    }
    page.zero();
  }
}
