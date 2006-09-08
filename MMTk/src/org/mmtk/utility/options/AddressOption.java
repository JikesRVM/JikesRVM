/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.UninterruptiblePragma;
import org.vmmagic.unboxed.Address;

/**
 * An option with a simple integer value.
 * 
 * $Id: IntOption.java 10489 2006-06-21 07:38:18Z steveb-oss $
 * 
 * @author Daniel Frampton
 * @version $Revision: 10489 $
 * @date $Date: 2006-06-21 17:38:18 +1000 (Wed, 21 Jun 2006) $
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
  public Address getValue() throws UninterruptiblePragma {
    return this.value;
  }

  /**
   * Read the default value of the option.
   * 
   * @return The default value.
   */
  public Address getDefaultValue() throws UninterruptiblePragma {
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
}
