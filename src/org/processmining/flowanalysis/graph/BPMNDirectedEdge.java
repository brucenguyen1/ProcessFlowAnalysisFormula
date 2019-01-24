package org.processmining.flowanalysis.graph;

import org.jbpt.graph.abs.AbstractDirectedEdge;
import org.jbpt.graph.abs.AbstractMultiDirectedGraph;
import org.jbpt.hypergraph.abs.Vertex;

import de.hpi.bpmn2_0.model.connector.SequenceFlow;

public class BPMNDirectedEdge extends AbstractDirectedEdge<BPMNVertex> {
	private boolean isMarked = false;
	private SequenceFlow sequenceFlow = null;
	
	protected BPMNDirectedEdge(AbstractMultiDirectedGraph<?, BPMNVertex> g, BPMNVertex source, BPMNVertex target) {
		super(g, source, target);
	}
	
	public boolean isMarked() {
		return isMarked;
	}
	
	public void setMarked(boolean isMarked) {
		this.isMarked = isMarked;
	}
	
	public SequenceFlow getSequenceFlow() {
		return this.sequenceFlow;
	}
	
	public void setSequenceFlow(SequenceFlow sequenceFlow) {
		this.sequenceFlow = sequenceFlow;
		this.setName(sequenceFlow.getName());
		this.setId(sequenceFlow.getId());
	}
}

