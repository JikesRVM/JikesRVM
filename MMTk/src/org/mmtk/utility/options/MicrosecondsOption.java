/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;

import org.vmmagic.pragma.UninterruptiblePragma;

/**
 * A time option that stores values at a microsecond granularity.
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class MicrosecondsOption extends Option {
  // values
  protected int defaultValue;
  protected int value;

  /**
   * Create a new microsecond option.
   *
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultUs The default value of the option (usec).
   */ 
  protected MicrosecondsOption(String name, String desc, int defaultUs) { 
    super(MICROSECONDS_OPTION, name, desc);
    this.value = this.defaultValue = defaultUs;
  }

  /**
   * Read the current value of the option in microseconds.
   *
   * @return The option value.
   */
  public int getMicroseconds() throws UninterruptiblePragma {
    return this.value;
  }

  /**
   * Read the current value of the option in milliseconds.
   *
   * @return The option value.
   */
  public int getMilliseconds() throws UninterruptiblePragma {
    return this.value / 1000;
  }

  /**
   * Read the default value of the option in microseconds.
   *
   * @return The default value.
   */
  public int getDefaultMicroseconds() throws UninterruptiblePragma {
    return this.defaultValue;
  }

  /**
   * Read the default value of the option in milliseconds.
   *
   * @return The default value.
   */
  public int getDefaultMilliseconds() throws UninterruptiblePragma {
    return this.defaultValue / 1000;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions 
   * option is set. An error occurs if the value is negative, and then the
   * validate method is called to allow subclasses to perform any additional 
   * validation.
   *
   * @param value The new value for the option.
   */
  public void setMicroseconds(int value) {
    int oldValue = this.value;
    if (echoOptions.getValue()) {
      Log.write("Option '");
      Log.write(this.getKey());
      Log.write("' set ");
      Log.write(oldValue);
      Log.write(" -> ");
      Log.writeln(value);
    }
    failIf(value < 0, "Unreasonable " + this.getName() + " value");
    this.value = value;
    validate();
  }
}
