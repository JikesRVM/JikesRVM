/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
abstract class OPT_LinkedListElement {
  OPT_LinkedListElement next;

  /**
   * Returns a pointer to the next element in the list, or null if this is the
   * last element.
   *
   * @returns pointer to next element
   */
  public final OPT_LinkedListElement getNext () {
    return  next;
  }

  /**
   * put your documentation comment here
   * @param next
   */
  public final void setNext (OPT_LinkedListElement next) {
    if (this != next)
      this.next = next;
  }

  /**
   * put your documentation comment here
   * @param e
   */
  public final void insertAfter (OPT_LinkedListElement e) {
    if (this != e) {
      e.next = next;
      next = e;
    }
  }

  /**
   * put your documentation comment here
   * @param e
   */
  public final void insertBefore (OPT_LinkedListElement e) {
    if (this != e)
      e.next = this;
  }

  /**
   * put your documentation comment here
   */
  public final void removeNext () {
    next = next.next;
  }

  /**
   * Append given linked list to self.
   *
   * @param l list to append
   */
  final OPT_LinkedListElement append (OPT_LinkedListElement l) {
    if (this == l)
      return  this;
    if (next != null)
      next.append(l); 
    else 
      next = l;
    return  this;
  }
}



