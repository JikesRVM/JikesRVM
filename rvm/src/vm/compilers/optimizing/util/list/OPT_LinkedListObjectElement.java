/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
final class OPT_LinkedListObjectElement extends OPT_LinkedListElement {
  Object value;

  /**
   * put your documentation comment here
   * @return 
   */
  Object getValue () {
    return  value;
  }

  /**
   * put your documentation comment here
   * @param   Object o
   */
  OPT_LinkedListObjectElement (Object o) {
    value = o;
  }

  /**
   * put your documentation comment here
   * @param   Object o
   * @param   OPT_LinkedListObjectElement rest
   */
  OPT_LinkedListObjectElement (Object o, OPT_LinkedListObjectElement rest) {
    value = o;
    next = rest;
  }

  /**
   * put your documentation comment here
   * @param o
   * @param rest
   * @return 
   */
  static OPT_LinkedListObjectElement cons (Object o, 
      OPT_LinkedListObjectElement rest) {
    return  new OPT_LinkedListObjectElement(o, rest);
  }

  /**
   * put your documentation comment here
   * @return 
   */
  OPT_LinkedListObjectElement copyFrom () {
    OPT_LinkedListObjectElement from = this;
    OPT_LinkedListObjectElement to = new 
        OPT_LinkedListObjectElement(from.value);
    OPT_LinkedListObjectElement to_curr = to;
    for (;;) {
      from = (OPT_LinkedListObjectElement)from.next;
      if (from == null)
        return  to;
      OPT_LinkedListObjectElement to_next = 
          new OPT_LinkedListObjectElement(from.value);
      to_curr.next = to_next;
      to_curr = to_next;
    }
  }
}



