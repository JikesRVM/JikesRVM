/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Julian Dolby
 */
interface OPT_SpecializationGraphEdge extends OPT_GraphEdge {

    VM_Method genericTargetMethod();

    VM_Type genericTargetClass();

    GNO_InstructionLocation callSite();

}
