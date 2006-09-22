/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2003
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.vm.gcspy;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * VM-neutral stub file for a class that provides generally useful
 * methods.
 * 
 * $Id$
 * 
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class Util implements Uninterruptible {
  public static final Address malloc(int size) { return Address.zero(); }
  public static final void free(Address addr) {}
  public static final void dumpRange(Address start, Address end) {}
  public static final Address getBytes(String str) { return Address.zero(); }
  public static final void formatSize(Address buffer, int size) {}
  public static final Address formatSize(String format, int bufsize, int size) {
    return Address.zero();
  }
  public static final int numToBytes(byte[] buffer, long value, int radix) {
    return 0;
  }
  public static final int sprintf(Address str, Address format, Address value) {
    return 0;
  }
}

