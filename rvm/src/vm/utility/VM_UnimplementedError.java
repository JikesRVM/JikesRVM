/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This error is thrown when the VM encounters an operation
 * that is not yet implemented.
 *
 * @author Igor Pechtchanski
 */
public class VM_UnimplementedError extends VirtualMachineError {
  /**
   * Constructs a new instance of this class with its
   * walkback filled in.
   */
  public VM_UnimplementedError() {
    super();
  }

  /**
   * Constructs a new instance of this class with its
   * walkback and message filled in.
   * @param detailMessage message to fill in
   */
  public VM_UnimplementedError(java.lang.String detailMessage) {
    super(detailMessage+": not implemented");
  }
}

