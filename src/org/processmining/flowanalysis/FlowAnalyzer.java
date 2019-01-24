package org.processmining.flowanalysis;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.jbpt.algo.tree.rpst.IRPSTNode;
import org.jbpt.algo.tree.rpst.RPST;
import org.jbpt.algo.tree.tctree.TCType;
import org.json.JSONException;
import org.processmining.flowanalysis.graph.BPMNDirectedEdge;
import org.processmining.flowanalysis.graph.BPMNVertex;
import org.processmining.flowanalysis.utils.FlowDiagramHelper;
import org.processmining.log.LogUtilites;
import org.processmining.plugins.signaturediscovery.encoding.EncodeTraces;

import com.processconfiguration.DefinitionsIDResolver;
import com.sun.xml.bind.IDResolver;

import de.hpi.bpmn2_0.replay.LogUtility;
import de.hpi.bpmn2_0.replay.Optimizer;
import de.hpi.bpmn2_0.replay.ReplayParams;
import de.hpi.bpmn2_0.replay.Replayer;
import de.hpi.bpmn2_0.transformation.BPMN2DiagramConverter;
import de.hpi.bpmn2_0.transformation.Bpmn2XmlConverter;
import de.hpi.bpmn2_0.backtracking2.Node;
import de.hpi.bpmn2_0.exceptions.BpmnConverterException;
import de.hpi.bpmn2_0.model.Definitions;
import de.hpi.bpmn2_0.model.FlowNode;
import de.hpi.bpmn2_0.model.Process;
import de.hpi.bpmn2_0.model.connector.SequenceFlow;

public class FlowAnalyzer {
	/**
	 * Construct a full flow analysis formula for a BPMN model
	 * @param bpmnModel
	 * @param unReachables: set of unreachable nodes from the last marking
	 * @return
	 * @throws Exception 
	 */
	public String formula(Process model, Set<FlowNode> unReachables) throws Exception {
		FlowDiagramHelper diagramHelper = new FlowDiagramHelper();
		RPST<BPMNDirectedEdge, BPMNVertex> rpst = diagramHelper.RPST(model);
		IRPSTNode<BPMNDirectedEdge, BPMNVertex> root = rpst.getRoot();
		String formula = this.formula(root, rpst, unReachables);
		return formula;
	}
	
	private String formula(IRPSTNode<BPMNDirectedEdge, BPMNVertex> node, RPST<BPMNDirectedEdge, BPMNVertex> rpst, Set<FlowNode> unReachables) throws Exception {
		//System.out.println("Start constructing formula for fragment starting with node " + node.getEntry().getFlowNode().getName());
		String formula = "";
		if (node.getType() == TCType.TRIVIAL) {
			if (node.getEntry().isActivity()) {
				formula = unReachables.contains(node.getEntry().getFlowNode()) ? "0" : node.getEntry().getFlowNode().getName();
			}
		}
		else if (node.getType() == TCType.POLYGON) {
			Set<IRPSTNode<BPMNDirectedEdge,BPMNVertex>> children = rpst.getChildren(node);
			for (IRPSTNode<BPMNDirectedEdge, BPMNVertex> child : FlowDiagramHelper.getOrderedChildren(node, children)) {
				String childFormula = this.formula(child,rpst, unReachables);
				if (!childFormula.isEmpty()) { // this is to avoid the case of an empty XOR branch with no activities 
					formula = formula.isEmpty() ? childFormula :  (formula + "+" + childFormula); 
				}
			}
			//if (formula.contains("+") && !formula.startsWith("(") && !formula.endsWith(")")) formula = "(" + formula + ")";
		}
		else if (node.getType() == TCType.BOND) {
			Set<IRPSTNode<BPMNDirectedEdge,BPMNVertex>> children = rpst.getChildren(node);
			
			if (node.getEntry().isANDSplit()) {		// Parallel AND
				for (IRPSTNode<BPMNDirectedEdge, BPMNVertex> child : children) {
					String childFormula = this.formula(child,rpst,unReachables);
					formula = formula.isEmpty() ? childFormula :  (formula + "," + childFormula);
				}
				if (!formula.isEmpty()) formula = "Max(" + formula + ")";
			}

			else if (node.getEntry().isXORJoin()) { //Repeat loop
				// !rpst.getGraph().getEdgesWithSourceAndTarget(node.getExit(), node.getEntry()).isEmpty()
				// Assume this is a repeat loop, i.e. there is an edge from the exit to the entry
				// and only one looping branch, so totally two branches of the XOR gateway
				BPMNDirectedEdge loopEdge = rpst.getGraph().getEdgesWithSourceAndTarget(node.getExit(), node.getEntry()).iterator().next();
				for (IRPSTNode<BPMNDirectedEdge, BPMNVertex> child : children) {
					if (!child.getFragment().contains(loopEdge)) { // this is the looping branch
						String r = loopEdge.getName();
						formula = "(" + formula(child,rpst,unReachables) + ")" + "/(1-" + r + ")";
						break;
					}
				}
				//if (!formula.isEmpty()) formula = "(" + formula + ")";
			}
			
			else if (node.getEntry().isXORSplit()) { // Branching XOR
				for (BPMNDirectedEdge branch : rpst.getGraph().getOutgoingEdges(node.getEntry()))  {
					String childFormula = "";
					boolean found = false;
					for (IRPSTNode<BPMNDirectedEdge, BPMNVertex> child : children) {
						if (child.getFragment().contains(branch)) {
							found = true;
							String branchFormula = this.formula(child, rpst, unReachables);
							if (!branchFormula.isEmpty()) childFormula = branch.getName() + "*" + bracket(branchFormula);
							break;
						}
					}
					if (!found) { 
						throw new Exception("Cannot find an RPST node containing the ougoing edge with name=" + branch.getName());
					}
					else if (!childFormula.isEmpty()) {
						formula = formula.isEmpty() ? childFormula :  (formula + "+" + childFormula);
					}
				}
				//if (formula.contains("+") && !formula.startsWith("(") && !formula.endsWith(")")) formula = "(" + formula + ")";
			}
			
		}
		else {
			throw new Exception("Cannot recognize a RPST type for node with name=" + node.getName());
		}
		
		//if (!formula.isEmpty()) System.out.println(formula);
		return formula;
		
	}
	
	private String bracket(String formula) {
		if (!formula.contains("+")) {
			return formula;
		}
		else {
			return "(" + formula + ")";
		}
	}
	
//	private boolean getLoopEdge(IRPSTNode<BPMNDirectedEdge, BPMNVertex> node, RPST<BPMNDirectedEdge, BPMNVertex> rpst) {
//		if (node.getEntry().isXORSplit()) {
//			for (IRPSTNode<BPMNDirectedEdge, BPMNVertex> child : rpst.getChildren(node)) {
//				if (child.getType() == TCType.TRIVIAL) {
//					if (child.getEntry() == node.getExit() && child.getExit() == node.getEntry()) {
//						return true;
//					}
//				}
//			}
//		}
//		return false;
//	}
	

//	public String constructRemainingFormula(XTrace trace, Process bpmnModel) {
//		
//	}
	
	/**
	 * Construct a cycle time remaining formula for a partial trace
	 * and a BPMN model
	 * @param log: event log file
	 * @param bpmnDefinition: the definition of the bpmn model
	 * @param params: replaying parameters
	 * @return the formula is written to a file, one formula per trace
	 * The unfolding BPMN model for every trace is also generated.
	 */
	public void consructRemainingFormula(XLog log, File modelFile, ReplayParams params, String formulaFile) {
		Optimizer optimizer = new Optimizer();
		optimizer.optimizeLog(log);
		
		Map<String, String> traceFormulaMap = new HashMap<>();
		Map<String, String> traceRelayPathMap = new HashMap<>();
		Map<String, String> traceLastMarkingMap = new HashMap<>();
		
		try {
			FileWriter fw = new FileWriter(formulaFile);
		    EncodeTraces.getEncodeTraces().read(log); //build a mapping from traceId to charstream
		    for (XTrace trace : log) {
		    	String replayPath = "";
		    	String lastMarking = "";
		    	String newMarkingStr = "";
		    	String formula = "";
		    	
		    	String traceId = LogUtility.getConceptName(trace);
		    	String traceString = EncodeTraces.getEncodeTraces().getCharStream(traceId);
		    	if (traceFormulaMap.containsKey(traceString)) {
		    		formula = traceFormulaMap.get(traceString);
		    		replayPath = traceRelayPathMap.get(traceString);
		    		lastMarking = traceLastMarkingMap.get(traceString);
		    	}
		    	else {
		    		FlowDiagramHelper diagramHelper = new FlowDiagramHelper();
		    		
					//------------------------------------------
					// Read BPMN model file
					//------------------------------------------
					Definitions definition = null;
					definition = readBPMNfromFile(modelFile);
					definition = optimizer.optimizeProcessModel(definition);
					Process model = (Process) definition.getRootElement().get(0);
					
					//------------------------------------------
					// Align trace and model
					//------------------------------------------
					Replayer replayer = new Replayer(definition, params);
			    	System.out.println("Trace ID: " + LogUtilites.getConceptName(trace));
			    	Node replayRes = replayer.align(trace);
			    	
					//------------------------------------------
					// Generate formula
					//------------------------------------------
			    	if (replayRes != null) {
				    	if (!replayRes.getState().isProperCompletion()) {
				    		Set<SequenceFlow> newMarking = diagramHelper.unfold(definition, replayRes.getState().getMarkings());
				    		newMarkingStr = this.getMarkingString(newMarking);
				    		
				    		//Write to .bpmn file
				    		if (!newMarking.equals(replayRes.getState().getMarkings())) {
					    		FileWriter modelWriter = new FileWriter(System.getProperty("user.dir") + "\\" + traceId + "_unfolding.bpmn");
					    		Bpmn2XmlConverter xmlConverter = new Bpmn2XmlConverter(definition,"xsdPath");
					    		definition.unusedNamespaceDeclarations = new ArrayList<>();
					    		modelWriter.write(xmlConverter.getXml().toString());
					    		modelWriter.close();
				    		}
				    		
				    		Set<FlowNode> unReachables = diagramHelper.getUnReachable(model, newMarking);
				    		formula  = this.formula(model, unReachables);
				    		//formula = this.resetActivities(formula, unReachables);
				    	}
				    	replayPath = replayRes.getPathString();
				    	lastMarking = replayRes.getState().getMarkingsText();
				    	traceFormulaMap.put(traceString, formula);
				    	traceRelayPathMap.put(traceString, replayPath);
				    	traceLastMarkingMap.put(traceString, lastMarking);
			    	}
			    	System.out.println("Formula: " + formula);
		    	}
		    	
//		    	System.out.println("Replay path: " + replayPath);
//		    	System.out.println("Last marking: " + lastMarking);
//		    	System.out.println("Remaining Cycle Time Formula: " + formula); 
		    	fw.write(LogUtilites.getConceptName(trace) + ";" + formula + ";" + replayPath + ";" + lastMarking + ";" + newMarkingStr + System.getProperty("line.separator"));
		    }
		    fw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}				
	}
	
	
	/**
	 * Update all unreachable activities in a given formula to zero 
	 * @param formula
	 * @param unReachableActivities
	 * @return
	 */
	private String resetActivities(String formula, Set<FlowNode> unReachables) {
		for (FlowNode node : unReachables) {
			formula = formula.replace(node.getName(), "0");
		}
		return formula;
	}
	
	private static Definitions readBPMNfromFile(File file) throws BpmnConverterException, JSONException, JAXBException {
        // Parse BPMN from XML to JAXB
        Unmarshaller unmarshaller = BPMN2DiagramConverter.newContext().createUnmarshaller();
        unmarshaller.setProperty(IDResolver.class.getName(), new DefinitionsIDResolver());
        Definitions definitions = unmarshaller.unmarshal(new StreamSource(file), Definitions.class).getValue();

        return definitions;
    }
	
	private String getMarkingString(Set<SequenceFlow> marking) {
		String result = "";
		for (SequenceFlow f : marking) {
			String fString = f.getSourceRef().getName() + "->" + f.getTargetRef().getName();
			result += result.isEmpty() ? fString : ("," + fString);
		}
		return result;
	}
}
