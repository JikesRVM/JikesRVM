/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * An option with a simple integer value.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class AddressOption extends Option {
  // values
  protected Address defaultValue;
  protected Address value;

  /**
   * Create a new int option.
   * 
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultValue The default value of the option.
   */
  protected AddressOption(String name, String desc, Address defaultValue) {
    super(ADDRESS_OPTION, name, desc);
    this.value = this.defaultValue = defaultValue;
  }

  /**
   * Read the current value of the option.
   * 
   * @return The option value.
   */
  @Uninterruptible
  public Address getValue() { 
    return this.value;
  }

  /**
   * Read the default value of the option.
   * 
   * @return The default value.
   */
  @Uninterruptible
  public Address getDefaultValue() { 
    return this.defaultValue;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions
   * option is set. This method also calls the validate method to allow
   * subclasses to perform any required validation.
   * 
   * @param value The new value for the option.
   */
  public void setValue(int value) {
    int oldValue = this.value.toInt();
    this.value = Address.fromIntZeroExtend(value);
    if (Options.echoOptions.getValue()) {
      Log.write("Option '");
      Log.write(this.getKey());
      Log.write("' set ");
      Log.write(oldValue);
      Log.write(" -> ");
      Log.writeln(value);
    }
    validate();
  }

  /**
   * Log the option value in raw format - delegate upwards
   * for fancier formatting.
   * 
   * @param format Output format (see Option.java for possible values)
   */
  @Override
  void log(int format) {
    switch (format) {
      case RAW:
        Log.write(value);
        break;
      default:
        super.log(format);
    }
  }
}
