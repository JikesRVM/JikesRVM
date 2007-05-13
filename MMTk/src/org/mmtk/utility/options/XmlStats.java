/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.utility.options;

/**
 * Display statistics and options in XML rather than himan-readable
 * format.
 * 
 *
 */
public class XmlStats extends BooleanOption {

  /**
   * Create the option.
   */
  public XmlStats() {
    super("Xml Stats", "Print end-of-run statistics in XML format", false);
  }

}
