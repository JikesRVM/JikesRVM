/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
import java.util.Vector;

/**
 * a wrapper around class information for
 * tree based network debugger
 *
 * @author Manu Sridharan
 */

public class JDP_Class implements java.io.Serializable
{
  /**
   * name of class
   */
  String name;

  /**
   * name to display in tree
   */
  String displayName;
  
  /**
   * true if represents an instance
   * false if represents a class
   */
  boolean instance;

  /**
   * address for instance,
   * invalid for class
   */
  int address;

  /**
   * fields
   */
  Vector fields = new Vector(); // JDP_Field

  /**
   * for serialization purposes
   */
  private static final long serialVersionUID = -2394420200773339035L;


  public String toString()
  {
    return (displayName == null) ? name : displayName;
  }
}
