/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Dick Attanasio
 */
class VM_FinalizerListElement {

    VM_Address value;
    Object pointer;
    VM_FinalizerListElement next;

    VM_FinalizerListElement(Object input) {
	value = VM_Magic.objectAsAddress(input);
    }

    // Do not provide interface for Object 

    void move(VM_Address newAddr) {
	value = newAddr;
    }

    void finalize (VM_Address newAddr) {
	value = VM_Address.zero();
	pointer = VM_Magic.addressAsObject(newAddr);
    }
}
