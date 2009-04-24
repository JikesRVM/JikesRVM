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
package org.mmtk.utility.statistics;

import org.mmtk.utility.Log;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Word;

/**
 * Utility class for writing statistics out in XML format.
 */
@Uninterruptible
public class Xml {
  /**
   * Mark the start of XML output
   */
  public static void begin() {
    Log.writeln("<xml-begin/> <!-- Everything until xml-end is now valid xml -->");
  }

  /**
   * Mark the end of XML output
   */
  public static void end() {
    Log.writeln("<xml-end/> <!-- Non-xml data follows ... -->");
  }

  /**
   * Close the innermost XML tag and pop it from the stack.
   */
  public static void closeTag(String name) {
    Log.write("</"); Log.write(name); Log.writeln(">");
  }

  /**
   * Open an XML tag.
   *
   * @param name Tag name
   * @param endTag Should the tag be closed, or left open for
   *               adding additional attributes
   */
  static void openTag(String name, boolean endTag) {
    openMinorTag(name);
    if (endTag)
      closeTag(false);
  }

  /**
   * Open a simple XML entity.
   *
   * @param name Name of the entity
   */
  static void openTag(String name) { openTag(name,true); }

  /**
   * Output a "stat" entity, with a given name, <code>double</code>value and optionally, units.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   * @param units The units, or null for no units.
   */
  public static void singleValue(String name, double value, String units) {
    openMinorTag("stat");
    attribute("name",name);
    attribute("value",value);
    if (units != null) attribute("units",units);
    closeMinorTag();
  }

  /**
   * Convenience version of singleValue where units are not specified.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void singleValue(String name, double value) {
    singleValue(name,value,null);
  }

  /**
   * Output a "config" entity, with a given name and <code>boolean</code>value.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void configItem(String name, boolean value) {
    openMinorTag("conf");
    attribute("name",name);
    attribute("value",value);
    closeMinorTag();
  }

  /**
   * Output a "config" entity, with a given name and <code>String</code>value.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void configItem(String name, String value) {
    openMinorTag("conf");
    attribute("name",name);
    attribute("value",value);
    closeMinorTag();
  }

  /**
   * Output a "stat" entity, with a given name, <code>long</code> value and
   * optionally, units.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   * @param units The units, or null for no units.
   */
  public static void singleValue(String name, long value, String units) {
    openMinorTag("stat");
    attribute("name",name);
    attribute("value",value);
    if (units != null) attribute("units",units);
    closeMinorTag();
  }

  /**
   * Convenience version of singleValue where units are not specified.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void singleValue(String name, long value) {
    singleValue(name,value,null);
  }

  /**
   * Add a word-valued attribute to an open XML tag.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void attribute(String name, Word value) {
    openAttribute(name); Log.write(value); closeAttribute();
  }

  /**
   * Add a byte[]-valued attribute to an open XML tag.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void attribute(String name, byte[] value) {
    openAttribute(name); Log.write(value); closeAttribute();
  }

  /**
   * Add a String-valued attribute to an open XML tag.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void attribute(String name, String value) {
    openAttribute(name); Log.write(value); closeAttribute();
  }

  /**
   * Add a boolean-valued attribute to an open XML tag.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void attribute(String name, boolean value) {
    openAttribute(name); Log.write(value); closeAttribute();
  }

  /**
   * Add a double-valued attribute to an open XML tag.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void attribute(String name, double value) {
    openAttribute(name); Log.write(value); closeAttribute();
  }

  /**
   * Add a long-valued attribute to an open XML tag.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void attribute(String name, long value) {
    openAttribute(name); Log.write(value); closeAttribute();
  }

  /**
   * Add an int-valued attribute to an open XML tag.
   *
   * @param name Name of the entity
   * @param value The value of the entity
   */
  public static void attribute(String name, int value) {
    openAttribute(name); Log.write(value); closeAttribute();
  }

  /**
   * Close an attribute (actually a simple close-quote)
   */
  public static void closeAttribute() {
    Log.write("\"");
  }

  /**
   * Open an attribute (write "{name}=\")
   *
   * @param name Name of the entity
   */
  public static void openAttribute(String name) {
    Log.write(" "); Log.write(name); Log.write("=\"");
  }

  /**
   * Start a tag
   */
  public static void startTag() {
    Log.write("<");
  }

  /**
   * End a tag, optionally closing it (if it is a simple entity)
   *
   * @param close If true, close the tag with "/>" rather than ">"
   */
  public static void closeTag(boolean close) {
    closeTag(close,true);
  }

  /**
   * End a tag, optionally closing it (if it is a simple entity),
   * and optionally printing end-of-line
   *
   * @param close If true, close the tag with "/>" rather than ">"
   * @param endLine If true end the current line.
   */
  public static void closeTag(boolean close, boolean endLine) {
    if (close) Log.write("/");
    Log.write(">");
    if (endLine) Log.writeln();
  }

  /**
   * Close a tag with a "/>"
   */
  public static void closeMinorTag() {
    closeTag(true,true);
  }

  /**
   * Open a tag without pushing it on the tag stack - must end this
   * with a call to closeMinorTag()
   *
   * @param name Name of the entity
   */
  public static void openMinorTag(String name) {
    Log.write("<"); Log.write(name);
  }

  /**
   * Open an XML comment
   */
  public static void openComment() {
    Log.write("<!-- ");
  }

  /**
   * Close an XML comment
   */
  public static void closeComment() {
    Log.write(" -->");
  }

  /**
   * Add a comment, bracketing it with open- and close-comment tags.
   *
   * @param comment The comment.
   */
  public static void comment(String comment) {
    openComment();
    Log.write(comment);
    closeComment();
    Log.writeln();
  }

}
