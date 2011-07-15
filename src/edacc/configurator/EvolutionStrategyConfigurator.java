package edacc.configurator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import edacc.api.API;
import edacc.model.ExperimentResult;
import edacc.model.Instance;
import edacc.model.InstanceDAO;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

/**
 * Really simple evolution strategy configurator.
 *
 */
public class EvolutionStrategyConfigurator {
	static int idExperiment = 12;
	static int numruns = 2;
	static ArrayList<BigInteger> seeds = new ArrayList<BigInteger>();

	public static void main(String[] args) throws Exception {
		API api = new API();
		api.connect("localhost", 3306, "EDACC", "edacc", "edaccteam");
		
		ParameterGraph pspace = api.loadParameterGraphFromDB(idExperiment);
		Random rng = new Random();
		LinkedList<Instance> instances = InstanceDAO.getAllByExperimentId(idExperiment);
		for (Instance i: instances) {
			for (int j = 0; j < numruns; j++) seeds.add(BigInteger.valueOf(rng.nextInt(234567892)));
		}
		
		// generate initial population
		int pop_size = 5, generations = 100;
		double best_cost = Double.MAX_VALUE;
		ParameterConfiguration best_configuration = null;
		Vector<Double> cost = new Vector<Double>();
		Vector<ParameterConfiguration> population = new Vector<ParameterConfiguration>();
		for (int i = 0; i < pop_size; i++) {
			population.add(pspace.getRandomConfiguration(rng));
			cost.add(cost_func(api, population.get(i), rng, 0, i));
			if (cost.get(i) < best_cost) {
				best_cost = cost.get(i);
				best_configuration = new ParameterConfiguration(population.get(i));
			}
		}
		System.out.println("initial generation:");
		for (int i = 0; i < pop_size; i++) {
			System.out.println(population.get(i).toString() + " (cost: " + cost.get(i) + ")");
		}
		
		// evolve ...
		for (int g = 1; g <= generations; g++) {
			Vector<ParameterConfiguration> children = new Vector<ParameterConfiguration>();
			for (int i = 0; i < pop_size; i++) {
				children.add(new ParameterConfiguration(population.get(i)));
				pspace.mutateParameterConfiguration(rng, children.get(i));
				double child_cost = cost_func(api, children.get(i), rng, g, i);
				if (child_cost < cost.get(i)) { // replace parent if child is better
					population.set(i, children.get(i));
					cost.set(i, child_cost);
				}
				
				if (cost.get(i) < best_cost) { // keep track of overall best configuration found
					best_cost = cost.get(i);
					best_configuration = new ParameterConfiguration(population.get(i));
				}
			}
			
			System.out.println("new generation:");
			for (int i = 0; i < pop_size; i++) {
				System.out.println(population.get(i).toString() + " (cost: " + cost.get(i) + ")");
			}
			
			System.out.println("best configuration found so far:" + best_configuration.toString() + " (cost " + best_cost + ")");
		}
		
		System.out.println("best configuration:" + best_configuration.toString() + " (cost " + best_cost + ")");
		
		api.disconnect();
	}
	
	public static double cost_func(API api, ParameterConfiguration config, Random rng, int gen, int num) throws Exception {
		// get experiment instance-IDs will be provided by API instead of direct DAO access.
		LinkedList<Instance> instances = InstanceDAO.getAllByExperimentId(idExperiment);
		LinkedList<Integer> job_ids = new LinkedList<Integer>();
		int solver_config_id = api.createSolverConfig(idExperiment, config, "Config " + gen + "/" + num);
		int k = 0;
		for (Instance inst: instances) {
			for (int j = 0; j < numruns; j++) {
				job_ids.add(api.launchJob(idExperiment, solver_config_id, inst.getId(), seeds.get(k++), 15));
			}
		}
		while (true) {
			// poll DB for job status every 2 seconds
			Thread.sleep(1000);
			boolean all_done = true;
			for (int id: job_ids) {
				ExperimentResult res = api.getJob(id);
				all_done &= res.getStatus().getStatusCode() >= 1; // assuming nothing crashs or this will go on forever
			}
			if (all_done) break;
		}
		
		double total_time = 0.0;
		for (int id: job_ids) {
			ExperimentResult res = api.getJob(id);
			total_time += res.getResultTime();
		}
		return total_time / job_ids.size();
	}
}
