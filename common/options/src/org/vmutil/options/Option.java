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
package org.vmutil.options;

/**
 * The abstract base class for all options. This class also has
 * the static interfaces to access the options system to set
 * option values.
 * <p>
 * All options within the system should have a unique name. No
 * two options shall have a name that is the same when a case
 * insensitive comparison between the names with spaces removed
 * is performed. Only basic alphanumeric characters and spaces
 * are allowed.
 * <p>
 * The VM is required to provide a one way mapping function that
 * takes the name and creates a VM style name, such as mapping
 * "No Finalizer" to noFinalizer. The VM may not remove any letters
 * when performing this mapping but may remove spaces and change
 * the case of any character.
 */
public abstract class Option {
  // Option types
  public static final int BOOLEAN_OPTION = 1;
  public static final int STRING_OPTION = 2;
  public static final int ENUM_OPTION = 3;
  public static final int INT_OPTION = 4;
  public static final int PAGES_OPTION = 6;
  public static final int MICROSECONDS_OPTION = 7;
  public static final int FLOAT_OPTION = 8;
  public static final int ADDRESS_OPTION = 9;

  /*
   * The possible output formats
   */
  public static final int READABLE = 0;
  public static final int RAW = 1;
  public static final int XML = 2;

  // Per option values
  private int type;
  private String name;
  private String description;
  private String key;
  private Option next;

  protected OptionSet set;

  /**
   * Construct a new option. This also calls the VM to map the option's
   * name into a unique option key and links it onto the option list.
   *
   * @param set The option set this option belongs to.
   * @param type The option type as defined in this class.
   * @param name The unique name of the option.
   * @param description A short description of the option and purpose.
   */
  protected Option(OptionSet set, int type, String name, String description) {
    this.type = type;
    this.name = name;
    this.description = description;
    this.set = set;
    this.key = set.register(this, name);
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
   * Update the next pointer in the Option chain.
   */
  void setNext(Option o) {
    next = o;
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
   * This is a validation method that can be implemented by leaf option
   * classes to provide additional validation. This should not be implemented
   * at other levels within the hierarchy to avoid confusion. The validate
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
    set.fail(this, message);
  }

  /**
   * Fail if a specified condition is met.
   *
   * @param condition The condition that indicates failure.
   * @param message The error message associated with the failure.
   */
  protected void failIf(boolean condition, String message) {
    if (condition) set.fail(this, message);
  }

  /**
   * A non-fatal error occurred during the setting of an option. This method
   * calls into the VM and shall not cause the system to stop.
   *
   * @param message The message associated with the warning.
   */
  protected void warn(String message) {
    set.warn(this, message);
  }

  /**
   * Warn if a specified condition is met.
   *
   * @param condition The condition that indicates warning.
   * @param message The message associated with the warning.
   */
  protected void warnIf(boolean condition, String message) {
    if (condition) set.warn(this, message);
  }
}

