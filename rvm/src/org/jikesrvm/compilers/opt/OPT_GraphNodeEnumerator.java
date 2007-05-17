/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import java.util.Enumeration;
import java.util.Iterator;

public abstract class OPT_GraphNodeEnumerator implements OPT_GraphNodeEnumeration {

  public final OPT_GraphNode nextElement() { return next(); }

  public static OPT_GraphNodeEnumerator create(Enumeration<OPT_GraphNode> e) {
    return new Enum(e);
  }

  public static OPT_GraphNodeEnumerator create(Iterator<OPT_GraphNode> i) {
    return new Iter(i);
  }

  public static OPT_GraphNodeEnumerator create(Iterable<OPT_GraphNode> i) {
    return new Iter(i.iterator());
  }

  public static class Enum extends OPT_GraphNodeEnumerator {
    private final Enumeration<OPT_GraphNode> e;

    Enum(Enumeration<OPT_GraphNode> e) {
      this.e = e;
    }

    public final boolean hasMoreElements() { return e.hasMoreElements(); }

    public final OPT_GraphNode next() { return e.nextElement(); }
  }

  public static class Iter extends OPT_GraphNodeEnumerator {
    private final Iterator<OPT_GraphNode> i;

    Iter(Iterator<OPT_GraphNode> i) {
      this.i = i;
    }

    public final boolean hasMoreElements() { return i.hasNext(); }

    public final OPT_GraphNode next() { return i.next(); }
  }
}
