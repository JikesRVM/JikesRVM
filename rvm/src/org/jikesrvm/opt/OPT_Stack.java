/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt;

import java.util.Iterator;
import java.util.ArrayList;

/**
 * OPT_Stack
 * OPT_Stack is a smaller implementation of java.util.Stack, that uses a linked
 * list rather than a vector.
 *
 * @author John Whaley
 * @date  3/18/98
 */
public class OPT_Stack<T> implements Iterable<T> {
  private final ArrayList<T> stack = new ArrayList<T>();

  public OPT_Stack() {
  }

  public OPT_Stack(T e) {
    push(e);
  }

  public final T push(T e) {
    stack.add(e);
    return  e;
  }

  public final T pop() {
    return stack.remove(stack.size()-1);
  }

  public final T getTOS() {
    return stack.get(stack.size()-1);
  }

  public final T peek() {
    return  getTOS();
  }

  public final boolean isEmpty() {
    return stack.isEmpty();
  }

  public final boolean empty() {
    return  isEmpty();
  }

  public final boolean compare(OPT_Stack<T> s2) {
    Iterator<T> i1 = iterator();
    Iterator<T> i2 = s2.iterator();
    if (isEmpty() && s2.isEmpty())
      return true;
    else if (isEmpty() || s2.isEmpty())
      return false;
    for (T t1 = i1.next(), t2 = i2.next();
         i1.hasNext() && i2.hasNext(); ) {
      if (t1 != t2)
        return false;
    }
    return !i1.hasNext() && !i2.hasNext();
  }

  public final OPT_Stack<T> copy() {
    OPT_Stack<T> s = new OPT_Stack<T>();
    s.stack.addAll(stack);
    return  s;
  }

  public final OPT_Stack<T> shallowCopy() {
    OPT_Stack<T> s = new OPT_Stack<T>();
    s.stack.addAll(stack);
    return  s;
  }

  public final Iterator<T> iterator() {
    return  stack.iterator();
  }

  public String toString() {
    StringBuilder sb = new StringBuilder(" --> ");
    for (T t : stack) {
      sb.append(t.toString());
      sb.append(' ');
    }
    return  sb.toString();
  }
}
