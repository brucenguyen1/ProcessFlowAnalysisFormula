package org.processmining.flowanalysis.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import org.jbpt.algo.tree.rpst.IRPSTNode;
import org.jbpt.algo.tree.rpst.RPST;
import org.jbpt.algo.tree.tctree.TCType;
import org.jbpt.graph.abs.IDirectedGraph;
import org.jbpt.graph.abs.IFragment;
import org.processmining.flowanalysis.graph.BPMNDirectedEdge;
import org.processmining.flowanalysis.graph.BPMNDirectedGraph;
import org.processmining.flowanalysis.graph.BPMNVertex;
import de.hpi.bpmn2_0.factory.AbstractBpmnFactory;
import de.hpi.bpmn2_0.model.Definitions;
import de.hpi.bpmn2_0.model.FlowElement;
import de.hpi.bpmn2_0.model.FlowNode;
import de.hpi.bpmn2_0.model.connector.SequenceFlow;
import de.hpi.bpmn2_0.model.event.Event;
import de.hpi.bpmn2_0.model.gateway.ExclusiveGateway;
import de.hpi.bpmn2_0.model.gateway.Gateway;
import de.hpi.bpmn2_0.model.gateway.GatewayDirection;
import de.hpi.bpmn2_0.model.gateway.ParallelGateway;
import de.hpi.diagram.SignavioUUID;
import de.vogella.algorithms.dijkstra.engine.DijkstraAlgorithm;
import de.vogella.algorithms.dijkstra.model.Edge;
import de.vogella.algorithms.dijkstra.model.Graph;
import de.vogella.algorithms.dijkstra.model.Vertex;
import de.hpi.bpmn2_0.model.Process;
import de.hpi.bpmn2_0.model.activity.Activity;
import de.hpi.bpmn2_0.model.activity.Task;
import de.hpi.bpmn2_0.model.bpmndi.BPMNDiagram;
import de.hpi.bpmn2_0.model.bpmndi.BPMNEdge;
import de.hpi.bpmn2_0.model.bpmndi.BPMNShape;
import de.hpi.bpmn2_0.model.bpmndi.di.DiagramElement;

public class FlowDiagramHelper {
	
	private DijkstraAlgorithm dijkstraAlgo = null;
    private Map<FlowNode,Vertex<FlowNode>> bpmnDijikstraNodeMap = new HashMap<>();
	
	/**
	 * Construct an RPST from a BPMN model
	 * @param bpmnModel
	 * @return
	 */
	public RPST<BPMNDirectedEdge, BPMNVertex> RPST(Process bpmnModel) {
		IDirectedGraph<BPMNDirectedEdge, BPMNVertex> graph = new BPMNDirectedGraph();
		HashMap<FlowNode, BPMNVertex> mapping = new HashMap<FlowNode, BPMNVertex>();
		
		for (FlowElement element : bpmnModel.getFlowElement()) {
            if (element instanceof SequenceFlow) {
            	FlowNode src = (FlowNode)(((SequenceFlow) element).getSourceRef());
                BPMNVertex srcRPST;
                if (!mapping.containsKey(src)) {
                	srcRPST = new BPMNVertex(src);
                	mapping.put(src, srcRPST);
                	graph.addVertex(srcRPST);
                }
                else {
                	srcRPST = mapping.get(src);
                }
                
                FlowNode tgt = (FlowNode)(((SequenceFlow) element).getTargetRef());
                BPMNVertex tgtRPST;
                if (!mapping.containsKey(tgt)) {
                	tgtRPST = new BPMNVertex(tgt);
                	mapping.put(tgt, tgtRPST);
                	graph.addVertex(tgtRPST);
                }
                else {
                	tgtRPST = mapping.get(tgt);
                }
	
	            BPMNDirectedEdge edge = graph.addEdge(srcRPST, tgtRPST);
	            edge.setName(element.getName());
	            edge.setSequenceFlow((SequenceFlow)element);
            }
        }

        RPST<BPMNDirectedEdge, BPMNVertex> rpst = new RPST<BPMNDirectedEdge, BPMNVertex>(graph);
        return rpst;
	}
	
	
	/**
	 * Unfold a fragment, meaning copying it out of a loop structure
	 * @param loopBranch: the looping branch containing the fragment
	 * @param loopEntry: the entry node of the loop fragment (the XOR-split gateway)
	 * @param loopExit:  the exit node of the loop fragment (the XOR-join gateway)
	 * @param definition: the BPMN definition, would be changed after this method
	 * @param marking: the current marking
	 * @return the new marking in the new model
	 * @throws Exception: when there is mismatch in the model
	 */
	public Set<SequenceFlow> unfoldFragment(IFragment<BPMNDirectedEdge, BPMNVertex> loopBranch, FlowNode loopEntry, FlowNode loopExit, 
									Definitions definition, Set<SequenceFlow> marking) throws Exception {
		Process model = (Process) definition.getRootElement().get(0);
		BPMNDiagram diagram = definition.getDiagram().get(0);
		
		//-------------------------------------------------
		// Extract nodes and edges from the fragment
		// Note: the fragment here is the looping branch, not the RPST loop structure fragment
		// This fragment excludes the entry, the exit, and the edges connected to them
		// fragmentNodes: contains all nodes of the fragment to be copied
		// fragmentEdges: contains all edges of the fragment to be copied
		//-------------------------------------------------
		Set<FlowNode> fragmentNodes = new HashSet<>();
		Set<SequenceFlow> fragmentEdges = new HashSet<>();
		for (BPMNDirectedEdge e : loopBranch) {
			fragmentNodes.add(e.getSource().getFlowNode());
			fragmentNodes.add(e.getTarget().getFlowNode());
			fragmentEdges.add(e.getSequenceFlow());
		}
		fragmentNodes.remove(loopEntry);
		fragmentNodes.remove(loopExit);
		fragmentEdges.remove(loopEntry.getOutgoingSequenceFlows().get(0)); // the entry is an XOR-join, it has only 1 outgoing flow
		fragmentEdges.remove(loopExit.getIncomingSequenceFlows().get(0)); // the exit is an XOR-split, it has only 1 incoming flow
		
		//-------------------------------------------------
		// Copy the fragment above to a new one
		// The new nodes are connected through new edges
		// Assume that there are only 3 types of nodes: Task, Exclusive Gateway, and Parallel Gateway.		
		// nodeMapping, edgeMapping: contains new nodes/new edges mapped to old ones
		// fragmentEntry, fragmentExit: the entry and exit nodes of the fragment
		// newFragmentEntry, newFragmentExit: mark the entry and exit of the new fragment
		//-------------------------------------------------
		FlowNode fragmentEntry = (FlowNode)loopEntry.getOutgoingSequenceFlows().get(0).getTargetRef();
		FlowNode fragmentExit = (FlowNode)loopExit.getIncomingSequenceFlows().get(0).getSourceRef();
		Map<FlowNode,FlowNode> nodeMapping = new HashMap<>(); // mapping from old node to new node
		Map<SequenceFlow,SequenceFlow> edgeMapping = new HashMap<>(); // mapping from old edge to new edge
		
		// Copy nodes: don't copy the entry and exit
		for (FlowNode oldNode : fragmentNodes) {
			FlowNode newNode = null;
			if (oldNode instanceof Task) {
				newNode = new Task();
			}
			else if (oldNode instanceof ExclusiveGateway) {
				newNode = new ExclusiveGateway();
				((ExclusiveGateway)newNode).setGatewayDirection(((ExclusiveGateway) oldNode).getGatewayDirection());
			}
			else if (oldNode instanceof ParallelGateway) {
				newNode = new ParallelGateway();
				((ParallelGateway)newNode).setGatewayDirection(((ParallelGateway) oldNode).getGatewayDirection());
			}
			else {
				throw new Exception("Unknown type of node for node with name = " + oldNode.getName());
			}
			newNode.setName(oldNode.getName()+".U");
			newNode.setId(oldNode.getId()+".U");
			model.addChild(newNode);
			diagram.getBPMNPlane().getDiagramElement().add(this.constructFlowNodes(newNode));
			nodeMapping.put(oldNode, newNode);
		}
		
		// Copy edges. They are used to connect the new nodes in nodeMapping
		for (SequenceFlow e : fragmentEdges) {
			SequenceFlow eNew = new SequenceFlow();
			model.addChild(eNew);
			eNew.setName(e.getName());
			eNew.setId(e.getId()+".U");
			
			if (nodeMapping.containsKey((FlowNode)e.getSourceRef())) {
				eNew.setSourceRef(nodeMapping.get((FlowNode)e.getSourceRef()));
				nodeMapping.get((FlowNode)e.getSourceRef()).getOutgoing().add(eNew);
			}
			else {
				throw new Exception("There is a mismatch between the sequence flow and the node " + 
										((FlowNode)e.getSourceRef()).getName() + " in the subgraph of an RPST fragment");
			}
			
			if (nodeMapping.containsKey((FlowNode)e.getTargetRef())) {
				eNew.setTargetRef(nodeMapping.get((FlowNode)e.getTargetRef()));
				nodeMapping.get((FlowNode)e.getTargetRef()).getIncoming().add(eNew);
			}
			else {
				throw new Exception("There is a mismatch between the sequence flow and the node " + 
						((FlowNode)e.getTargetRef()).getName() + " in the subgraph of an RPST fragment");
			}
			
			diagram.getBPMNPlane().getDiagramElement().add(this.constructEdgeNodes(diagram.getBPMNPlane().getDiagramElement(), eNew));
			edgeMapping.put(e, eNew);
		}
		FlowNode newFragmentEntry = nodeMapping.get(fragmentEntry);
		FlowNode newFragmentExit = nodeMapping.get(fragmentExit);
		
		//-------------------------------------------------
		// Identify the preceding and succeeding flows that connect to the looping branch
		// precedingFlow, succeedingFlow: the flows connected with the loop fragment
		// loopingEdge: the looping edge with r probability
		//-------------------------------------------------
		int nonloopingEdgeIndex = 0; //index of looping edge
		if (loopEntry.getIncomingSequenceFlows().get(0).getSourceRef() == loopExit) {
			nonloopingEdgeIndex = 1;
		}
		de.hpi.bpmn2_0.model.connector.Edge loopingEdge = loopEntry.getIncoming().get(1-nonloopingEdgeIndex); // the name of loopingEdge is probability r
		SequenceFlow precedingFlow = loopEntry.getIncomingSequenceFlows().get(nonloopingEdgeIndex);
		
		nonloopingEdgeIndex = 0;
		if (loopExit.getOutgoingSequenceFlows().get(0).getTargetRef() == loopEntry) {
			nonloopingEdgeIndex = 1;
		}
		SequenceFlow succeedingFlow = loopExit.getOutgoingSequenceFlows().get(nonloopingEdgeIndex);
		
		//-------------------------------------------------
		// Create a new XOR-split
		//-------------------------------------------------
		FlowNode newXORSplit = new ExclusiveGateway();
		((ExclusiveGateway)newXORSplit).setGatewayDirection(GatewayDirection.DIVERGING);
		newXORSplit.setName(loopEntry.getName()+".U");
		newXORSplit.setId(loopEntry.getId()+".U");
		model.addChild(newXORSplit);
		diagram.getBPMNPlane().getDiagramElement().add(this.constructFlowNodes(newXORSplit));
		
		//-------------------------------------------------
		// Create a new XOR-join
		//-------------------------------------------------
		FlowNode newXORJoin = new ExclusiveGateway();
		((ExclusiveGateway)newXORJoin).setGatewayDirection(GatewayDirection.CONVERGING);
		newXORJoin.setName(loopExit.getName()+".U");
		newXORJoin.setId(loopExit.getId()+".U");
		model.addChild(newXORJoin);
		diagram.getBPMNPlane().getDiagramElement().add(this.constructFlowNodes(newXORJoin));
		
		//-------------------------------------------------
		// Connect the new fragment with the preceding node
		//-------------------------------------------------
		precedingFlow.setTargetRef(newFragmentEntry);
		newFragmentEntry.getIncoming().add(precedingFlow);
		diagram.getBPMNPlane().getDiagramElement().add(this.constructEdgeNodes(diagram.getBPMNPlane().getDiagramElement(), precedingFlow));
		
		//-------------------------------------------------
		// Connect the new fragment with the new XOR-split
		//-------------------------------------------------
		SequenceFlow newFragmentExitFlow = new SequenceFlow();
		newFragmentExitFlow.setId(newFragmentExit.getId() + "_" + newXORSplit.getId());
		newFragmentExitFlow.setName("");
		model.addChild(newFragmentExitFlow);
		
		newFragmentExitFlow.setSourceRef(newFragmentExit);
		newFragmentExit.getOutgoing().add(newFragmentExitFlow);
		
		newFragmentExitFlow.setTargetRef(newXORSplit);
		newXORSplit.getIncoming().add(newFragmentExitFlow);
		
		diagram.getBPMNPlane().getDiagramElement().add(this.constructEdgeNodes(diagram.getBPMNPlane().getDiagramElement(), newFragmentExitFlow));
		
		//-------------------------------------------------
		// Connect the new XOR-split with the loop entry node
		//-------------------------------------------------
		SequenceFlow newXORSplitOutgoing = new SequenceFlow();
		newXORSplitOutgoing.setId(newXORSplit.getId() + "_" + loopEntry.getId());
		newXORSplitOutgoing.setName(loopingEdge.getName()); // set r probability label
		model.addChild(newXORSplitOutgoing);
		
		// src of the outgoing flow
		newXORSplitOutgoing.setSourceRef(newXORSplit);
		newXORSplit.getOutgoing().add(newXORSplitOutgoing);
		
		// target of the outgoing flow
		newXORSplitOutgoing.setTargetRef(loopEntry);
		loopEntry.getIncoming().add(newXORSplitOutgoing);
		
		diagram.getBPMNPlane().getDiagramElement().add(this.constructEdgeNodes(diagram.getBPMNPlane().getDiagramElement(), newXORSplitOutgoing));
		
		//-------------------------------------------------
		// Connect the loop exit node with the new XOR-join 
		//-------------------------------------------------
		SequenceFlow newXORJoinIncoming = new SequenceFlow();
		newXORJoinIncoming.setId(loopExit.getId() + "_" + newXORJoin.getId());
		newXORJoinIncoming.setName("1-" + loopingEdge.getName()); // set probability 1-r
		model.addChild(newXORJoinIncoming);

		newXORJoinIncoming.setSourceRef(loopExit);
		loopExit.getOutgoing().add(newXORJoinIncoming);

		newXORJoinIncoming.setTargetRef(newXORJoin);
		newXORJoin.getIncoming().add(newXORJoinIncoming);

		diagram.getBPMNPlane().getDiagramElement().add(this.constructEdgeNodes(diagram.getBPMNPlane().getDiagramElement(), newXORJoinIncoming));
		
		//-------------------------------------------------
		// Connect the new XOR-join with the succeeding node
		//-------------------------------------------------
		succeedingFlow.setSourceRef(newXORJoin);
		newXORJoin.getOutgoing().add(succeedingFlow);
		diagram.getBPMNPlane().getDiagramElement().add(this.constructEdgeNodes(diagram.getBPMNPlane().getDiagramElement(), succeedingFlow));
		
		//-------------------------------------------------
		// Connect the new XOR-split with the new XOR-joint
		//-------------------------------------------------
		SequenceFlow newShortcutFlow = new SequenceFlow();
		newShortcutFlow.setId(newXORSplit.getId() + "_" + newXORJoin.getId());
		newShortcutFlow.setName(1 + "-" + loopingEdge.getName()); // set probability label: 1-r
		model.addChild(newShortcutFlow);

		newShortcutFlow.setSourceRef(newXORSplit);
		newXORSplit.getOutgoing().add(newShortcutFlow);
		
		newShortcutFlow.setTargetRef(newXORJoin);
		newXORJoin.getIncoming().add(newShortcutFlow);
		
		diagram.getBPMNPlane().getDiagramElement().add(this.constructEdgeNodes(diagram.getBPMNPlane().getDiagramElement(), newShortcutFlow));
		
		//------------------------------------------------
		// Update marking
		//------------------------------------------------
		Set<SequenceFlow> newMarking = new HashSet<SequenceFlow>(marking); //copy from the current marking
		for (SequenceFlow e : fragmentEdges) {
			if (newMarking.contains(e)) {
				newMarking.add(edgeMapping.get(e)); //update new marking
				newMarking.remove(e);
			}
		}
		if (newMarking.contains(fragmentEntry.getIncomingSequenceFlows().get(0))) {
			newMarking.add(newFragmentEntry.getIncomingSequenceFlows().get(0));
			newMarking.remove(fragmentEntry.getIncomingSequenceFlows().get(0));
		}
		if (newMarking.contains(fragmentExit.getOutgoingSequenceFlows().get(0))) {
			newMarking.add(newFragmentExit.getOutgoingSequenceFlows().get(0));
			newMarking.remove(fragmentExit.getOutgoingSequenceFlows().get(0));
		}
		
		return newMarking;
	}
	
	/**
	 * Unfold a BPMN model given a marking with token inside loops
	 * If no tokens are within a loop in the model, the model is returned as is.
	 * @param Definition: the model definition would be changed
	 * @param marking: the last marking in the model
	 * @return the new marking in the new model
	 * @throws Exception 
	 */
	public Set<SequenceFlow> unfold(Definitions definition, Set<SequenceFlow> marking) throws Exception {
		
		//----------------------------------------------------
		// Construct the RPST and mark the edges of the RPST graph in correspondence with 
		// tokens in the marking. Note that there is a one-to-one
		// mapping between the sequence flows in the BPMN diagram
		// and the edges in the underlying graph of the RPST
		//----------------------------------------------------
		RPST<BPMNDirectedEdge, BPMNVertex> rpst = this.RPST((Process)definition.getRootElement().get(0));
		for (BPMNDirectedEdge e : rpst.getGraph().getEdges()) {
			e.setMarked(false);
			FlowNode src = e.getSource().getFlowNode();
			FlowNode tgt = e.getTarget().getFlowNode();
			for (SequenceFlow flow : marking) {
				if (src == (FlowNode)flow.getSourceRef() && tgt == (FlowNode)flow.getTargetRef()) {
					e.setMarked(true);
				}
			}
		}
		
		//----------------------------------------------------
		// Traverse breadth-first the RPST from bottom up to 
		// search for repeat loop fragment
		//----------------------------------------------------
		Queue<IRPSTNode<BPMNDirectedEdge, BPMNVertex>> queue = new LinkedList<IRPSTNode<BPMNDirectedEdge,BPMNVertex>>();
		Stack<IRPSTNode<BPMNDirectedEdge, BPMNVertex>> stack = new Stack<IRPSTNode<BPMNDirectedEdge,BPMNVertex>>();
		queue.add(rpst.getRoot());
		while (!queue.isEmpty()) {
			IRPSTNode<BPMNDirectedEdge, BPMNVertex> node = queue.poll();
			for (IRPSTNode<BPMNDirectedEdge, BPMNVertex> child : rpst.getChildren(node)) {
				queue.add(child);
			}
			stack.push(node);
		}
		
		//Now traverse bottom-up 
		List<IRPSTNode<BPMNDirectedEdge, BPMNVertex>> loopNodes = new ArrayList<>(); // list of repeat loop RPST nodes
		List<IRPSTNode<BPMNDirectedEdge, BPMNVertex>> loopBranches = new ArrayList<>(); // list of the repeat branches of the repeat loop RPST nodes 
		while (!stack.isEmpty()) {
			IRPSTNode<BPMNDirectedEdge, BPMNVertex> node = stack.pop();
			if (node.getType() == TCType.BOND && node.getEntry().isXORJoin()) { // this is a repeat loop
				// Assume a repeat loop has an edge from the exit to the entry
				// and only one looping branch, so it has totally two branches of the XOR gateway
				Set<IRPSTNode<BPMNDirectedEdge,BPMNVertex>> children = rpst.getChildren(node);
				BPMNDirectedEdge loopEdge = rpst.getGraph().getEdgesWithSourceAndTarget(node.getExit(), node.getEntry()).iterator().next();
				for (IRPSTNode<BPMNDirectedEdge, BPMNVertex> child : children) {
					if (!child.getFragment().contains(loopEdge)) { // this is the looping branch
						for (BPMNDirectedEdge e : child.getFragment()) {
							if (e.isMarked()) { // contains some tokens within the loop
								loopNodes.add(node);
								loopBranches.add(child);
								break;
							}
						}
					}
				}
			}
		}
		
		// Now unfold for every looping block found
		// Assume there is no nested loop, all the loops are separated in the model
		// The input model will be changed with added new fragment
		// The marking is also updated
		Set<SequenceFlow> newMarking = new HashSet<>(marking); // copy the marking
		for (int i=0;i<loopNodes.size();i++) {
			newMarking = unfoldFragment(loopBranches.get(i).getFragment(), loopNodes.get(i).getEntry().getFlowNode(), 
										loopNodes.get(i).getExit().getFlowNode(), definition, newMarking);
		}
		
		return newMarking;
	}
	
    public DijkstraAlgorithm getDijkstraAlgo(Process model) {
        if (dijkstraAlgo == null) {
        	bpmnDijikstraNodeMap.clear();
            //Create Dijistra nodes but keep a mapping from BPMN node to Dijistra node
            //so that we can apply for Dijistra edge in the next step
            for (FlowElement element : model.getFlowElement()) {
            	if (element instanceof FlowNode) {
            		FlowNode node = (FlowNode)element;
            		bpmnDijikstraNodeMap.put(node, new Vertex<FlowNode>(node.getId(), node.getName(), node));
            	}
            }
            
            List<Edge<FlowNode>> edges = new ArrayList<>();
            FlowNode source;
            FlowNode target;
            for (FlowElement element : model.getFlowElement()) {
            	if (element instanceof SequenceFlow) {
            		SequenceFlow flow = (SequenceFlow)element;
	                source = (FlowNode)flow.getSourceRef();
	                target = (FlowNode)flow.getTargetRef();
	                edges.add(new Edge<>(source.getId()+"-"+target.getId(), bpmnDijikstraNodeMap.get(source),
	                                   bpmnDijikstraNodeMap.get(target), 1));
            	}
            }
            
            dijkstraAlgo = new DijkstraAlgorithm(new Graph<>(new ArrayList<>(bpmnDijikstraNodeMap.values()),edges));
            
        
        }
        return dijkstraAlgo;
    }
    
    public Vertex<FlowNode> getDijikstraVertex(FlowNode node) {
        if (bpmnDijikstraNodeMap.containsKey(node)) {
            return bpmnDijikstraNodeMap.get(node);
        } else {
            return null;
        }
    }
    
    private boolean existPath(FlowNode source, FlowNode target, Process model) {
        if (source == target) {
            return true;
        }
        else {
        	DijkstraAlgorithm algo = this.getDijkstraAlgo(model);
        	algo.execute(getDijikstraVertex(source));
            ArrayList<Vertex> path = algo.getPath(this.getDijikstraVertex(target));
            return (path != null && path.size() > 0);
        }
    }
    
	
	/**
	 * Get all activity nodes that can be reached from the marking
	 * THis is a simplified version assuming that the model is sound 
	 * @param model: the process model
	 * @param marking: the marking with tokens on sequence flows
	 * @return: set of reachable activity nodes
	 */
    public Set<FlowNode> getUnReachable(Process model, Set<SequenceFlow> marking) {
		Set<FlowNode> reachables = new HashSet<>();
		for (SequenceFlow s : marking) {
			FlowNode src = (FlowNode)s.getTargetRef();
			if (src instanceof Activity) reachables.add(src);
			for (FlowElement element : model.getFlowElement()) {
				if (element instanceof Activity) {
					if (this.existPath(src, (FlowNode)element, model)) {
						reachables.add((FlowNode)element);
					}
				}
			}
		}
		
		Set<FlowNode> unReachable = new HashSet<>();
		for (FlowElement element : model.getFlowElement()) {
			if (element instanceof FlowNode && !reachables.contains(element)) {
				unReachable.add((FlowNode)element);
			}
		}
		
		return unReachable;
	}
	
    /**
     * Get a list of children of an RPST node which is a POLYGON
     * The list is chained based on the entry and exit nodes of the children
     * from the beginning to end.
     * @param node
     * @param unOrderedChildren
     * @return ordered list
     * @throws Exception 
     */
    public static List<IRPSTNode<BPMNDirectedEdge,BPMNVertex>> getOrderedChildren(IRPSTNode<BPMNDirectedEdge,BPMNVertex> node, 
    																		Set<IRPSTNode<BPMNDirectedEdge,BPMNVertex>> unOrderedChildren) throws Exception {
    	if (node.getType() != TCType.POLYGON) {
    		return null;
    	}
    	else {
    		List<IRPSTNode<BPMNDirectedEdge,BPMNVertex>> sortedList = new ArrayList<IRPSTNode<BPMNDirectedEdge,BPMNVertex>>();
    		String searchId = node.getEntry().getFlowNode().getId();
    		String endingId = node.getExit().getFlowNode().getId();
    		while (!searchId.equals(endingId)) {
    			boolean found = false;
    			for (IRPSTNode<BPMNDirectedEdge,BPMNVertex> child : unOrderedChildren) {
    				if (child.getEntry().getFlowNode().getId().equals(searchId)) {
    					sortedList.add(child);
    					found = true;
    					searchId = child.getExit().getFlowNode().getId();
    					break;
    				}
    			}
    			if (!found) {
    				throw new Exception("Error while sorting the children RPST nodes based on entry-exit chain. Cannot find the next RPST node with entry Id=" + searchId);
    			}
    		}
    		return sortedList;
    	}
    }
    
    private BPMNEdge constructEdgeNodes(List<DiagramElement> diagramElement, SequenceFlow flow) {
        BPMNEdge diagramElem = null;
        if (flow != null) {
            diagramElem = new BPMNEdge();
            diagramElem.setId(SignavioUUID.generate());
            diagramElem.setBpmnElement(flow);
            diagramElem.setSourceElement(findDiagramElement(diagramElement, flow.getSourceRef()));
            diagramElem.setTargetElement(findDiagramElement(diagramElement, flow.getTargetRef()));
            diagramElem.getWaypoint().add(new de.hpi.bpmn2_0.model.bpmndi.dc.Point(0, 0));
            diagramElem.getWaypoint().add(new de.hpi.bpmn2_0.model.bpmndi.dc.Point(0, 0));
        }
        return diagramElem;
    }

    private BPMNShape constructFlowNodes(FlowElement flow) {
        BPMNShape diagramElem = null;
        if (flow != null) {
            diagramElem = new BPMNShape();
            diagramElem.setId(SignavioUUID.generate());
            diagramElem.setBpmnElement(flow);
            diagramElem.setIsHorizontal(true);

            if (flow instanceof Event) {
                diagramElem.setBounds(createEventBounds());
            } else if (flow instanceof Activity) {
                diagramElem.setBounds(createTaskBounds());
            } else if (flow instanceof Gateway) {
                diagramElem.setBounds(createGatewayBounds());
            }
        }
        return diagramElem;
    }

    private de.hpi.bpmn2_0.model.bpmndi.dc.Bounds createLaneBounds() {
        de.hpi.bpmn2_0.model.bpmndi.dc.Bounds bound = new de.hpi.bpmn2_0.model.bpmndi.dc.Bounds();
        bound.setX(0.0);
        bound.setY(0.0);
        bound.setWidth(500.0);
        bound.setHeight(100.0);
        return bound;
    }

    private de.hpi.bpmn2_0.model.bpmndi.dc.Bounds createEventBounds() {
        de.hpi.bpmn2_0.model.bpmndi.dc.Bounds bound = new de.hpi.bpmn2_0.model.bpmndi.dc.Bounds();
        bound.setX(0.0);
        bound.setY(0.0);
        bound.setWidth(30.0);
        bound.setHeight(30.0);
        return bound;
    }

    private de.hpi.bpmn2_0.model.bpmndi.dc.Bounds createTaskBounds() {
        de.hpi.bpmn2_0.model.bpmndi.dc.Bounds bound = new de.hpi.bpmn2_0.model.bpmndi.dc.Bounds();
        bound.setX(0.0);
        bound.setY(0.0);
        bound.setWidth(100.0);
        bound.setHeight(80.0);
        return bound;
    }

    private de.hpi.bpmn2_0.model.bpmndi.dc.Bounds createGatewayBounds() {
        de.hpi.bpmn2_0.model.bpmndi.dc.Bounds bound = new de.hpi.bpmn2_0.model.bpmndi.dc.Bounds();
        bound.setX(0.0);
        bound.setY(0.0);
        bound.setWidth(40.0);
        bound.setHeight(40.0);
        return bound;
    }


    private DiagramElement findDiagramElement(List<DiagramElement> diagramElements, final FlowElement flowElement) {
        for (final DiagramElement diagramElement : diagramElements) {
            if (diagramElement instanceof BPMNShape) {
                if (((BPMNShape)diagramElement).getBpmnElement().equals(flowElement)) {
                    return diagramElement;
                }
            }
        }
        return null;
    }
	
}
