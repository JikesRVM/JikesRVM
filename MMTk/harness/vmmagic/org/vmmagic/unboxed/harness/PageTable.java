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

import java.util.HashMap;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.vmmagic.unboxed.Address;
import static org.vmmagic.unboxed.harness.MemoryConstants.*;

final class PageTable {
  private final HashMap<Long, MemoryPage> pages = new HashMap<Long, MemoryPage>();

  private long pageTableEntry(Address p) {
    return p.toLong() >>> LOG_BYTES_IN_PAGE;
  }

  /**
   * Internal: get a page by page number, performing appropriate
   * checking and synchronization
   * @param p
   * @return
   */
  synchronized MemoryPage getPage(Address p) {
    MemoryPage page = pages.get(pageTableEntry(p));
    if (page == null) {
      throw new RuntimeException("Page not mapped: " + p);
    } else if (!page.readable) {
      throw new RuntimeException("Page not readable: " + p);
    }
    return page;
  }

  synchronized void setReadable(Address p) {
    MemoryPage page = pages.get(pageTableEntry(p));
    if (page == null) {
      throw new RuntimeException("Page not mapped: " + p);
    }
    page.readable = true;
  }

  synchronized void setNonReadable(Address p) {
    MemoryPage page = pages.get(pageTableEntry(p));
    if (page == null) {
      throw new RuntimeException("Page not mapped: " + p);
    }
    page.readable = false;
  }

  synchronized void mapPage(Address p) {
    long page = pageTableEntry(p);
    Trace.trace(Item.MEMORY,"Mapping page %s%n", p);
    if (pages.get(page) != null) {
      throw new RuntimeException("Page already mapped: " + p);
    }
    pages.put(page, new MemoryPage(p));
  }

  synchronized void zeroPage(Address p) {
    synchronized(pages) {
      MemoryPage page = pages.get(pageTableEntry(p));
      if (page == null) {
        throw new RuntimeException("Page not mapped: " + p);
      }
      page.zero();
    }
  }
}
