package org.processmining.flowanalysis.utils;

import java.util.HashSet;
import java.util.Set;

import org.processmining.flowanalysis.graph.BPMNDirectedEdge;
import org.processmining.flowanalysis.graph.BPMNVertex;

public class Fragment {
	private Set<BPMNVertex> nodes = new HashSet<BPMNVertex>();
	private Set<BPMNDirectedEdge> edges = new HashSet<BPMNDirectedEdge>();
	private BPMNVertex startNode = null;
	private BPMNVertex endNode = null;
	
	public Set<BPMNVertex> getNodes() {
		return nodes;
	}
	
	public Set<BPMNDirectedEdge> getEdges() {
		return edges;
	}
	
	public void setStartNode(BPMNVertex startNode) {
		this.startNode = startNode;
	}
	
	public BPMNVertex getStartNode() {
		return startNode;
	}
	
	public void setEndNode(BPMNVertex endNode) {
		this.endNode = endNode;
	}
	
	public BPMNVertex getEndNode() {
		return endNode;
	}
}
