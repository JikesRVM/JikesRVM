/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Dick Attanasio
 */
class VM_FinalizerListElement {

int value;
Object pointer;
VM_FinalizerListElement next;


VM_FinalizerListElement(int input) 
{
  value = input;
}

VM_FinalizerListElement(Object input) 
{
  value = VM_Magic.objectAsAddress(input);
}

}
