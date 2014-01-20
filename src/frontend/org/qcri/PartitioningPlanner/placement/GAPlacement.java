package org.qcri.PartitioningPlanner.placement;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jaga.*;
import org.jaga.definitions.GAParameterSet;
import org.jaga.definitions.GAResult;
import org.jaga.exampleApplications.Example1Fitness;
import org.jaga.hooks.AnalysisHook;
import org.jaga.individualRepresentation.greycodedNumbers.NDecimalsIndividualSimpleFactory;
import org.jaga.individualRepresentation.greycodedNumbers.RangeConstraint;
import org.jaga.masterAlgorithm.ReusableSimpleGA;
import org.jaga.selection.RouletteWheelSelection;
import org.jaga.util.DefaultParameterSet;
import org.qcri.PartitioningPlanner.placement.Plan;


public class GAPlacement extends Placement {
	
	Long coldPartitionWidth = 1000L; // redistribute cold tuples in chunks of 1000
	ArrayList<Long> tupleIds = null;
	ArrayList<Long> accesses = null; 
	ArrayList<Integer> locations = null; 
	ArrayList<List<Plan.Range>> slices = null;
	ArrayList<Long> sliceSizes = null;
	int tupleCount = 0;
	int sliceCount = 0;
	Long totalAccesses = 0L;
	
	public GAPlacement(){
		
	}
	
	// initialize the private data members based on the input parameters
		private void init(ArrayList<Map<Long, Long>> hotTuplesList, Map<Integer, Long> partitionTotals, Plan aPlan, int partitionCount) {
			tupleIds = new ArrayList<Long>();
			accesses = new ArrayList<Long>(); 
			locations = new ArrayList<Integer>(); 
			slices = new ArrayList<List<Plan.Range>>();
			sliceSizes = new ArrayList<Long>();

			// copy partitionTotals into oldLoad
			totalAccesses = 0L;
			Map<Integer, Long> oldLoad = new HashMap<Integer, Long> ();
			for(Integer i : partitionTotals.keySet()) {
				totalAccesses += partitionTotals.get(i);
				oldLoad.put(i,  partitionTotals.get(i));
			}

			// copy aPlan into oldPlan
			Plan oldPlan = new Plan();
			Map<Integer, List<Plan.Range>> ranges = aPlan.getAllRanges();
			for(Integer i : ranges.keySet()) {
				List<Plan.Range> partitionRanges = ranges.get(i);
				oldPlan.addPartition(i);
				for(Plan.Range range : partitionRanges) {
					oldPlan.addRange(i, range.from, range.to);
				}
			}

			// calculate the load and plan if the hot tuples were removed, store
			// them in oldLoad and oldPlan
			tupleCount = 0;
			Integer partitionId = 0;
			for(Map<Long, Long>  hotTuples : hotTuplesList) {
				tupleCount += hotTuples.keySet().size();
				for(Long i : hotTuples.keySet()) {
					oldLoad.put(partitionId, oldLoad.get(partitionId) - hotTuples.get(i));
					oldPlan.removeTupleId(partitionId, i);
				}
				++partitionId;
			}

			// store the ids, access counts, and locations of each of the hot tuples
			partitionId = 0;
			for(Map<Long, Long>  hotTuples : hotTuplesList) {
				for(Long i : hotTuples.keySet()) {
					tupleIds.add(i);
					accesses.add(hotTuples.get(i));
					locations.add(partitionId);
				}
				++partitionId;
			}

			// store the ranges, sizes, access counts, and locations of each of the slices of cold tuples
			sliceCount = 0;
			for(Integer i : oldPlan.getAllRanges().keySet()) { // for each partition
				List<List<Plan.Range>> partitionSlices = oldPlan.getRangeSlices(i,  coldPartitionWidth);
				if(partitionSlices.size() > 0) {
					sliceCount += partitionSlices.size();
					Double tupleWeight = ((double) oldLoad.get(i)) / oldPlan.getTupleCount(i); // per tuple
					for(List<Plan.Range> slice : partitionSlices) {  // for each slice
						Long sliceSize = Plan.getRangeListWidth(slice);
						Long newWeight = (long) (tupleWeight *  ((double) sliceSize));
						slices.add(slice);
						sliceSizes.add(sliceSize);
						accesses.add(newWeight);
						locations.add(i);
					} // end for each slice
				}
			} // end for each partition
		}
		
	
	// hotTuples: tupleId --> access count
	// siteLoads: partitionId --> total access count
	public Plan computePlan(ArrayList<Map<Long, Long>> hotTuplesList, Map<Integer, Long> partitionTotals, String planFilename, int partitionCount, int timeLimit){
		
		Plan aPlan = new Plan(planFilename);
		this.init(hotTuplesList, partitionTotals, aPlan, partitionCount);

		for(int i = 0; i < partitionCount; ++i) {
		    if(partitionTotals.get(i) == null) {
			partitionTotals.put(i, 0L);
		    }
		}
		
		for(Integer i : partitionTotals.keySet()) {
			totalAccesses += partitionTotals.get(i);
		}
		
		int placementCount = tupleCount + sliceCount; // number of placements we will make
		Long meanAccesses = totalAccesses / partitionCount;

		System.out.println("Mean access count: " + meanAccesses);
		
		
		GAParameterSet params = new DefaultParameterSet();
		params.setPopulationSize(0);
		GAPlacementFitness fitness = new GAPlacementFitness();
		fitness.initialize(tupleIds,accesses,locations,slices,sliceSizes,tupleCount,
				sliceCount,totalAccesses,partitionCount); 
		params.setFitnessEvaluationAlgorithm(fitness);
		params.setSelectionAlgorithm(new RouletteWheelSelection(-10E10));
		params.setMaxGenerationNumber(100);
		NDecimalsIndividualSimpleFactory fact = new NDecimalsIndividualSimpleFactory(placementCount, 0, 30);
		for(int i = 0; i < placementCount; ++i) {
			fact.setConstraint(i, new RangeConstraint(0, partitionCount-1));
		}
		params.setIndividualsFactory(fact);

		ReusableSimpleGA ga = new ReusableSimpleGA(params);
		AnalysisHook hook = new AnalysisHook();
		hook.setLogStream(System.out);
		hook.setUpdateDelay(100);
		hook.setAnalyseGenMinFit(true);
		ga.addHook(hook);

		final int attempts = 1;

		GAResult [] allResults = new GAResult[attempts];
		for (int i = 0; i < attempts; i++) {
			hook.reset();
			GAResult result = ga.exec();
			allResults[i] = result;
		}
		System.out.println("\nALL DONE.\n");
		for (int i = 0; i < attempts; i++) {
			System.out.println("Result " + i + " is: " + allResults[i]);
		}

		
		
		

		aPlan = demoteTuples(hotTuplesList, aPlan);
		removeEmptyPartitions(aPlan);
		return aPlan;
		
	}
	

}
