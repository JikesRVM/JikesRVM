/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;
/**
 * @author Ton Ngo 
 */
import java.util.*;
import java.io.*;

class BootMapCorruptException 
    extends Exception
   {
   public BootMapCorruptException(BootMap map, StreamTokenizer z) 
      {
      super("BootMap is corrupt" + "; Last Token=" +z.toString());
      }
   }
