package edacc.configurator.ga;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edacc.api.API;
import edacc.model.ExperimentResult;
import edacc.model.Instance;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;

class InstanceSeedPair {
    protected int idInstance;
    protected BigInteger seed;
    
    public InstanceSeedPair(int idInstance, BigInteger seed) {
        this.idInstance = idInstance;
        this.seed = seed;
    }
}

public class GAConfigurator {
    final int populationSize = 50;
    final int generations = 100;
    
    private int idExperiment;
    private API api;
    private List<InstanceSeedPair> parcour;
    private Random rng;
    private ParameterGraph pspace;
    private int jobCPUTimeLimit;

    public static void main(String[] args) throws Exception {
        GAConfigurator ga = new GAConfigurator("localhost", 3306, "edacc",
                "edaccteam", "EDACC", 16, 3, 5);
        ga.evolve();
        ga.shutdown();
    }

    public GAConfigurator(String hostname, int port, String username,
            String password, String database, int idExperiment,
            int numRunsPerInstance, int jobCPUTimeLimit) throws Exception {
        api = new API();
        api.connect(hostname, port, database, username, password);
        this.idExperiment = idExperiment;
        rng = new Random();
        List<Instance> expInstances = api.getExperimentInstances(idExperiment);
        parcour = new ArrayList<InstanceSeedPair>();
        for (int i = 0; i < numRunsPerInstance; i++) {
            for (Instance instance : expInstances) {
                parcour.add(new InstanceSeedPair(instance.getId(),BigInteger.valueOf(rng.nextInt(2147483647))));
            }
        }
        pspace = api.loadParameterGraphFromDB(idExperiment);
        this.jobCPUTimeLimit = jobCPUTimeLimit;
    }

    public void shutdown() {
        api.disconnect();
    }

    protected List<Individual> initializePopulation(int size) throws Exception {
        List<Individual> population = new ArrayList<Individual>();
        for (int i = 0; i < size; i++) {
            ParameterConfiguration cfg = pspace.getRandomConfiguration(rng);
            int idSolverConfig = api.createSolverConfig(idExperiment, cfg, cfg.toString());
            population.add(new Individual(idSolverConfig, cfg));
        }
        return population;
    }

    protected void evaluatePopulation(List<Individual> population) throws Exception {
        List<Integer> jobs = new ArrayList<Integer>();
        for (Individual ind : population) {
            if (ind.getCost() == null) {
                for (InstanceSeedPair isp: parcour) {
                    jobs.add(api.launchJob(idExperiment, ind.getIdSolverConfiguration(),
                            isp.idInstance, isp.seed, jobCPUTimeLimit));
                }
            }
        }
        
        Map<Integer, ExperimentResult> results;
        while (true) {
            Thread.sleep(2000);
            boolean all_done = true;
            results = api.getJobsByIDs(jobs);
            for (ExperimentResult result: results.values()) {
                all_done &= (result.getStatus().getStatusCode() >= 1 ||
                             result.getStatus().getStatusCode() < -1);
            }
            if (all_done) break;
        }
        
        Map<Integer, Float> individual_time_sum = new HashMap<Integer, Float>();
        for (ExperimentResult result: results.values()) {
            if (!individual_time_sum.containsKey(result.getSolverConfigId()))
                individual_time_sum.put(result.getSolverConfigId(), 0.0f);
            individual_time_sum.put(result.getSolverConfigId(),
                        individual_time_sum.get(result.getSolverConfigId()) + result.getResultTime());
        }
        
        for (Individual ind: population) {
            if (ind.getCost() == null) {
                // averate result time is the cost
                ind.setCost(individual_time_sum.get(ind.getIdSolverConfiguration()) / parcour.size());
            }
        }
    }

    public void evolve() throws Exception {
        List<Individual> population = initializePopulation(populationSize);
        
        for (int generation = 1; generation <= generations; generation++) {
            evaluatePopulation(population);
            break;
        }
    }

}
