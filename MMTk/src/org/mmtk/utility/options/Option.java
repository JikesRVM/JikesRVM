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
import org.mmtk.utility.statistics.Xml;
import org.mmtk.vm.VM;

/**
 * The abstract base class for all options. This class also has
 * the static interfaces to access the options system to set
 * option values.
 *
 * All options within the system should have a unique name. No
 * two options shall have a name that is the same when a case
 * insensitive comparison between the names with spaces removed
 * is performed. Only basic alphanumeric characters and spaces
 * are allowed.
 *
 * The VM is required to provide a one way mapping function that
 * takes the name and creates a VM style name, such as mapping
 * "No Finalizer" to noFinalizer. The VM may not remove any letters
 * when performing this mapping but may remove spaces and change
 * the case of any character.
 */
public abstract class Option {
  // options registry
  private static Option head;
  private static Option tail;

  /**
   * Initialize the options system so that options can be created.
   * This also creates the first option that can be used to echo
   * the setting of options to the console to assist debugging.
   */
  static {
    head = tail = null;
  }

  // Option types
  public static final int BOOLEAN_OPTION = 1;
  public static final int STRING_OPTION = 2;
  public static final int ENUM_OPTION = 3;
  public static final int INT_OPTION = 4;
  public static final int PAGES_OPTION = 6;
  public static final int MICROSECONDS_OPTION = 7;
  public static final int FLOAT_OPTION = 8;
  public static final int ADDRESS_OPTION = 9;
  
  /**
   * The possible output formats
   */
  public static final int READABLE = 0;
  public static final int RAW = 1;
  public static final int XML = 2;


  /**
   * Using the VM determined key, look up the corresponding option,
   * or return null if an option can not be found.
   * 
   * @param key The (unique) option key.
   * @return The option, or null.
   */
  public static Option getOption(String key) {
    Option o = getFirst();
    while (o != null) {
      if (o.key.equals(key)) {
        return o;
      }
      o = o.getNext();
    }
    return null;
  }

  /**
   * Return the first option. This can be used with the getNext method to
   * iterate through the options.
   * 
   * @return The first option, or null if no options exist.
   */
  public static Option getFirst() {
    return head;
  }

  // Per option values
  private int type;
  private String name;
  private String description;
  private String key;
  private Option next;

  /**
   * Construct a new option. This also calls the VM to map the option's
   * name into a unique option key and links it onto the option list.
   * 
   * @param type The option type as defined in this class.
   * @param name The unique name of the option.
   * @param description A short description of the option and purpose.
   */
  protected Option(int type, String name, String description) {
    this.type = type;
    this.name = name;
    this.description = description;
    this.key = VM.options.getKey(name);
    if (tail == null) {
      tail = head = this;
    } else {
      tail.next = this;
      tail = this;
    }
  }

  /**
   * Return the VM determined key for an option
   * 
   * @return The key.
   */
  public String getKey() {
    return this.key;
  }

  /**
   * Return the next option in the linked list.
   * 
   * @return The next option or null if this is the last option.
   */
  public Option getNext() {
    return this.next;
  }

  /**
   * Return the name for the option.
   * 
   * @return The option name.
   */
  public String getName() {
    return this.name;
  }

  /**
   * Return the option description.
   * 
   * @return The option description.
   */
  public String getDescription() {
    return this.description;
  }

  /**
   * Return the type of the option.
   * 
   * @return The option type.
   */
  public int getType() {
    return this.type;
  }
  
  /**
   * Log the option value in one of several formats
   * 
   * @param format Output format (see Option.java for possible values)
   */
  void log(int format) {
    switch (format) {
      case READABLE:
        Log.write("Option '");
        Log.write(getKey());
        Log.write(" = ");
        log(RAW);
        Log.writeln();
        break;
      case XML:
        Xml.openMinorTag("option");
        Xml.attribute("name",getKey());
        Xml.openAttribute("value");
        log(RAW);
        Xml.closeAttribute();
        Xml.closeMinorTag();
        break;
      case RAW:
        VM.assertions.fail("Subtypes of Option must implement log(RAW)");
        break;
    }
  }
  
  /**
   * This is a validation method that can be implemented by leaf option
   * classes to provide additional validation. This should not be implemented
   * at other levels within the heirarchy to avoid confusion. The validate
   * method works against the current value of the option (post-set).
   */
  protected void validate() {}

  /**
   * A fatal error occurred during the setting of an option. This method
   * calls into the VM and is required to cause the system to stop.
   * 
   * @param message The error message associated with the failure.
   */
  protected void fail(String message) {
    VM.options.fail(this, message);
  }

  /**
   * Fail if a specified condition is met.
   * 
   * @param condition The condition that indicates failure.
   * @param message The error message associated with the failure.
   */
  protected void failIf(boolean condition, String message) {
    if (condition) VM.options.fail(this, message);
  }

  /**
   * A non-fatal error occurred during the setting of an option. This method
   * calls into the VM and shall not cause the system to stop.
   * 
   * @param message The message associated with the warning.
   */
  protected void warn(String message) {
    VM.options.warn(this, message);
  }

  /**
   * Warn if a specified condition is met.
   * 
   * @param condition The condition that indicates warning.
   * @param message The message associated with the warning.
   */
  protected void warnIf(boolean condition, String message) {
    if (condition) VM.options.warn(this, message);
  }
}

