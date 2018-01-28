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
package org.jikesrvm.tools.bootImageWriter;

import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.say;

import java.util.Stack;

/**
 * A wrapper around the calling context to aid in tracing.
 */
class TraceContext extends Stack<String> {
  private static final long serialVersionUID = -9048590130621822408L;
  /**
   * Report a field that is part of our library's (GNU Classpath's)
   implementation, but not the host JDK's implementation.
   */
  public void traceFieldNotInHostJdk() {
    traceNulledWord(": field not in host jdk");
  }

  /** Report a field that is an instance field in the host JDK but a static
     field in ours.  */
  public void traceFieldNotStaticInHostJdk() {
    traceNulledWord(": field not static in host jdk");
  }

  /** Report a field that is a different type  in the host JDK.  */
  public void traceFieldDifferentTypeInHostJdk() {
    traceNulledWord(": field different type in host jdk");
  }

  /**
   * Report an object of a class that is not part of the bootImage.
   */
  public void traceObjectNotInBootImage() {
    traceNulledWord(": object not in bootimage");
  }

  /**
   * Report nulling out a pointer.
   */
  private void traceNulledWord(String message) {
    say(this.toString(), message, ", writing a null");
  }

  /**
   * Report an object of a class that is not part of the bootImage.
   */
  public void traceObjectFoundThroughKnown() {
    say(this.toString(), ": object found through known");
  }

  /**
   * Generic trace routine.
   */
  public void trace(String message) {
    say(this.toString(), message);
  }

  /**
   * Return a string representation of the context.
   * @return string representation of this context
   */
  @Override
  public String toString() {
    StringBuilder message = new StringBuilder();
    for (int i = 0; i < size(); i++) {
      if (i > 0) message.append(" --> ");
      message.append(elementAt(i));
    }
    return message.toString();
  }

  /**
   * Push an entity onto the context
   */
  public void push(String type, String fullName) {
    StringBuilder sb = new StringBuilder("(");
    sb.append(type).append(")");
    sb.append(fullName);
    push(sb.toString());
  }

  /**
   * Push a field access onto the context
   */
  public void push(String type, String decl, String fieldName) {
    StringBuilder sb = new StringBuilder("(");
    sb.append(type).append(")");
    sb.append(decl).append(".").append(fieldName);
    push(sb.toString());
  }

  /**
   * Push an array access onto the context
   */
  public void push(String type, String decl, int index) {
    StringBuilder sb = new StringBuilder("(");
    sb.append(type).append(")");
    sb.append(decl).append("[").append(index).append("]");
    push(sb.toString());
  }
}
