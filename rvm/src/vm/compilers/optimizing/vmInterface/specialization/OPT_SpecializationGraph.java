/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 *  This graph represents a set of specializations to perform.
 * Specializations to perform are represented by the nodes in the
 * graph, and the edges denote the call sites and receiver types of
 * calls to specialized methods.
 *
 *  Such graphs are used to direct the specialization mechanism in
 * determining what call sites need specialization and what
 * specialized methods to generate.  The specialization mechanism is
 * given some of these graphs, each graph with an associated
 * specialization compiler phase, and it invokes the phase during
 * compilation of the appropriate methods giving it the specialization
 * contexts that are hte nodes of the graph.
 *
 */
interface OPT_SpecializationGraph extends OPT_Graph {

    OPT_SpecializationGraphNode getSingleContext(VM_Method m);

    OPT_SpecializationGraphEdgeEnumeration
	enumerateEdges();

    OPT_SpecializationGraphEdgeEnumeration
	enumerateEdges(OPT_SpecializationGraphNode source,
		       GNO_InstructionLocation callSite);
    
}

