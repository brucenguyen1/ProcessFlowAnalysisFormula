package org.processmining.flowanalysis.graph;

import org.jbpt.hypergraph.abs.IGObject;
import org.jbpt.hypergraph.abs.IVertex;
import org.jbpt.hypergraph.abs.Vertex;

import de.hpi.bpmn2_0.model.FlowNode;
import de.hpi.bpmn2_0.model.activity.Activity;
import de.hpi.bpmn2_0.model.gateway.ExclusiveGateway;
import de.hpi.bpmn2_0.model.gateway.Gateway;
import de.hpi.bpmn2_0.model.gateway.GatewayDirection;
import de.hpi.bpmn2_0.model.gateway.InclusiveGateway;
import de.hpi.bpmn2_0.model.gateway.ParallelGateway;

public class BPMNVertex extends Vertex {
	private FlowNode node = null;
	
	public BPMNVertex(FlowNode node) {
		this.node = node;
		this.setName(node.getName());
		this.setId(node.getId());
	}
	
	public FlowNode getFlowNode() {
		return node;
	}
	
	
    public boolean isANDSplit() {
        if ((node instanceof ParallelGateway) && 
            ((Gateway)node).getGatewayDirection().equals(GatewayDirection.DIVERGING)) {
            return true;
        }
        else {
            return false;
        }
    } 

   public boolean isANDJoin() {
        if ((node instanceof ParallelGateway) && 
            ((Gateway)node).getGatewayDirection().equals(GatewayDirection.CONVERGING)) {
            return true;
        }
        else {
            return false;
        }        
    }
    
    public boolean isXORSplit() {
        if ((node instanceof ExclusiveGateway) && 
            ((Gateway)node).getGatewayDirection().equals(GatewayDirection.DIVERGING)) {
            return true;
        }
        else {
            return false;
        }        
    } 
    
    public boolean isXORJoin() {
        if ((node instanceof ExclusiveGateway) && 
            ((Gateway)node).getGatewayDirection().equals(GatewayDirection.CONVERGING)) {
            return true;
        }
        else {
            return false;
        }        
    }
    
    public boolean isActivity() {
        return (node instanceof Activity);
    }     
    
    public boolean isORSplit() {
        if ((node instanceof InclusiveGateway) && 
            ((Gateway)node).getGatewayDirection().equals(GatewayDirection.DIVERGING)) {
            return true;
        }
        else {
            return false;
        }
    }
    
    public boolean isORJoin() {
        if ((node instanceof InclusiveGateway) && 
            ((Gateway)node).getGatewayDirection().equals(GatewayDirection.CONVERGING)) {
            return true;
        }
        else {
            return false;
        }
    }    	
}
