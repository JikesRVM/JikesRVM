/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Class that performs scalar replacement of aggregates
 *
 * @author Stephen Fink
 */
public interface OPT_AggregateReplacer {

  /** 
   * Perform the transformation
   */
  public abstract void transform ();
}
