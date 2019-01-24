package org.processmining.flowanalysis.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import org.deckfour.xes.model.XLog;
import org.json.JSONException;
import org.processmining.flowanalysis.FlowAnalyzer;
import org.processmining.flowanalysis.utils.OpenLogFilePlugin;

import com.processconfiguration.DefinitionsIDResolver;
import com.sun.xml.bind.IDResolver;
import de.hpi.bpmn2_0.exceptions.BpmnConverterException;
import de.hpi.bpmn2_0.model.Definitions;
import de.hpi.bpmn2_0.replay.Optimizer;
import de.hpi.bpmn2_0.replay.ReplayParams;
import de.hpi.bpmn2_0.transformation.BPMN2DiagramConverter;
import test.TestReplayer;
import de.hpi.bpmn2_0.model.Process;

public class TestFlowAnalyzer {
	// 1st argument: name of a BPMN model file (.bpmn)
	// 2nd argument: name of a log file (.xes or .mxml)
	// name of a output formula file = 2nd_argument + "_formula.csv"
	// These files must be in the directory of this tool
	public static void main(String[] args) {
		final Logger LOGGER = Logger.getLogger(TestReplayer.class.getCanonicalName());
		
    	File fileModel = new File(System.getProperty("user.dir") + "/" + args[0]);
    	File fileLog = new File(System.getProperty("user.dir") + "/" + args[1]);
    	String fileFormula = System.getProperty("user.dir") + "/" + args[1] + "_formula.csv";
        
		try {
			
			//------------------------------------------
			// Read BPMN model file
			//------------------------------------------
//			LOGGER.info("Read BPMN model file: " + fileModel);
//			Definitions bpmnDefinition = null;
//		    bpmnDefinition = readBPMNfromFile(fileModel);
//		    //LOGGER.info("BPMN Diagram Definition" + bpmnDefinition.toString());
			
			//------------------------------------------
			// Read event log file
			//------------------------------------------
		    LOGGER.info("Import event log file: " + fileLog);
		    OpenLogFilePlugin logImporter = new OpenLogFilePlugin();
			XLog log = (XLog)logImporter.importFile(fileLog);
			
			//------------------------------------------
			// Optimize logs and process model
			//------------------------------------------
//			LOGGER.info("Optimize string use for event log and the model");
//			Optimizer optimizer = new Optimizer();
//			optimizer.optimizeLog(log);
//			bpmnDefinition = optimizer.optimizeProcessModel(bpmnDefinition);
			
			//------------------------------------------
			// Read replayer parameters
			//------------------------------------------		
			LOGGER.info("Read replay papameters from parameter file: " + "properties.xml");
			 InputStream is = TestFlowAnalyzer.class.getResourceAsStream("/properties.xml");
//			 InputStream is = new FileInputStream(System.getProperty("user.dir") + "/properties.xml");
			 Properties props = new Properties();            
			 props.loadFromXML(is);
			 ReplayParams params = new ReplayParams();
			 params.setMaxCost(Double.valueOf(props.getProperty("MaxCost")).doubleValue());
			 params.setMaxDepth(Integer.valueOf(props.getProperty("MaxDepth")).intValue());
			 params.setMinMatchPercent(Double.valueOf(props.getProperty("MinMatchPercent")).doubleValue());
			 params.setMaxMatchPercent(Double.valueOf(props.getProperty("MaxMatchPercent")).doubleValue());
			 params.setMaxConsecutiveUnmatch(Integer.valueOf(props.getProperty("MaxConsecutiveUnmatch")).intValue());
			 params.setActivityMatchCost(Double.valueOf(props.getProperty("ActivityMatchCost")).doubleValue());
			 params.setActivitySkipCost(Double.valueOf(props.getProperty("ActivitySkipCost")).doubleValue());
			 params.setEventSkipCost(Double.valueOf(props.getProperty("EventSkipCost")).doubleValue());
			 params.setNonActivityMoveCost(Double.valueOf(props.getProperty("NonActivityMoveCost")).doubleValue());
			 params.setTraceChunkSize(Integer.valueOf(props.getProperty("TraceChunkSize")).intValue());
			 params.setMaxNumberOfNodesVisited(Integer.valueOf(props.getProperty("MaxNumberOfNodesVisited")).intValue());
			 params.setMaxActivitySkipPercent(Double.valueOf(props.getProperty("MaxActivitySkipPercent")).doubleValue());
			 params.setMaxNodeDistance(Integer.valueOf(props.getProperty("MaxNodeDistance")).intValue());
			 params.setTimelineSlots(Integer.valueOf(props.getProperty("TimelineSlots")).intValue());
			 params.setTotalEngineSeconds(Integer.valueOf(props.getProperty("TotalEngineSeconds")).intValue());
			 params.setProgressCircleBarRadius(Integer.valueOf(props.getProperty("ProgressCircleBarRadius")).intValue());
			 params.setSequenceTokenDiffThreshold(Integer.valueOf(props.getProperty("SequenceTokenDiffThreshold")).intValue());
			 params.setMaxTimePerTrace(Long.valueOf(props.getProperty("MaxTimePerTrace")).longValue());
			 params.setMaxTimeShortestPathExploration(Long.valueOf(props.getProperty("MaxTimeShortestPathExploration")).longValue());
			 params.setExactTraceFitnessCalculation(props.getProperty("ExactTraceFitnessCalculation"));
			 params.setBacktrackingDebug(props.getProperty("BacktrackingDebug"));
			 params.setExploreShortestPathDebug(props.getProperty("ExploreShortestPathDebug"));     
			 params.setCheckViciousCycle(props.getProperty("CheckViciousCycle"));
			 params.setStartEventToFirstEventDuration(Integer.valueOf(props.getProperty("StartEventToFirstEventDuration")).intValue());
			 params.setLastEventToEndEventDuration(Integer.valueOf(props.getProperty("LastEventToEndEventDuration")).intValue());  
          

//			LOGGER.info("Start analyzing");
			FlowAnalyzer flowAnalyzer = new FlowAnalyzer();
//			Process model = (Process)bpmnDefinition.getRootElement().get(0);
//			String formula = flowAnalyzer.formula(model);
//			System.out.println(formula);
			flowAnalyzer.consructRemainingFormula(log, fileModel, params, fileFormula);
			System.out.println("DONE. Formulas have been generated in: " + fileFormula);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
//    public static Definitions readBPMNfromFile(File file) throws BpmnConverterException, JSONException, JAXBException {
//        // Parse BPMN from XML to JAXB
//        Unmarshaller unmarshaller = BPMN2DiagramConverter.newContext().createUnmarshaller();
//        unmarshaller.setProperty(IDResolver.class.getName(), new DefinitionsIDResolver());
//        Definitions definitions = unmarshaller.unmarshal(new StreamSource(file), Definitions.class).getValue();
//
//        return definitions;
//    }
}
