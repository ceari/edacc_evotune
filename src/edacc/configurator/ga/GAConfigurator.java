package edacc.configurator.ga;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    final int populationSize = 30;
    final int generations = 100;
    final int tournamentSize = 4;
    final float crossoverProbability = 0.7f;
    final float mutationProbability = 0.05f;
    
    private int idExperiment;
    private API api;
    private List<InstanceSeedPair> parcour;
    private Random rng;
    private ParameterGraph pspace;
    private int jobCPUTimeLimit;

    public static void main(String... args) throws Exception {
        if (args.length < 8) {
            System.out.println("arguments: <hostname> <port> <username> <password> <database> <experiment id> <runs per instance> <job CPU time limit>");
            return;
        }
        GAConfigurator ga = new GAConfigurator(args[0], Integer.valueOf(args[1]), args[2],
                args[3], args[4], Integer.valueOf(args[5]), Integer.valueOf(args[6]), Integer.valueOf(args[7]));
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

    protected void evaluatePopulation(List<Individual> population, int generation) throws Exception {
        List<Integer> jobs = new ArrayList<Integer>();
        for (Individual ind : population) {
            if (ind.getCost() == null) {
                for (int i = 0; i < parcour.size(); i++) {
                    jobs.add(api.launchJob(idExperiment, ind.getIdSolverConfiguration(),
                            parcour.get(i).idInstance, parcour.get(i).seed, jobCPUTimeLimit));
                }
            }
        }
        
        Map<Integer, ExperimentResult> results;
        while (true) {
            Thread.sleep(4000);
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
                        individual_time_sum.get(result.getSolverConfigId()) + result.getResultTime() * (result.getResultCode().getResultCode() > 0 ? 1 : 10));
        }
        
        for (Individual ind: population) {
            if (ind.getCost() == null) {
                // par10 result time is the cost
                ind.setCost(individual_time_sum.get(ind.getIdSolverConfiguration()) / parcour.size());
            }
        }
    }
    
    protected Individual tournamentSelect(List<Individual> population) {
        List<Integer> tournament = new ArrayList<Integer>();
        List<Individual> tournamentIndividuals = new ArrayList<Individual>();
        for (int i = 0; i < tournamentSize; i++) {
            int ix;
            do {
                ix = rng.nextInt(populationSize);
            } while (tournament.contains(ix));
            tournament.add(ix);
            tournamentIndividuals.add(population.get(ix));
        }
        
        Collections.sort(tournamentIndividuals);
        return tournamentIndividuals.get(0);
    }

    public void evolve() throws Exception {
        List<Individual> population = initializePopulation(populationSize);
        
        Individual globalBest = null;
        
        for (int generation = 1; generation <= generations; generation++) {
            evaluatePopulation(population, generation);
            
            float sum = 0;
            for (int i = 0; i < populationSize; i++) {
                sum += population.get(i).getCost();
                if (globalBest == null || population.get(i).getCost() < globalBest.getCost()) {
                    globalBest = population.get(i);
                }
            }
            System.out.println("Generation " + generation + " - global best: " + globalBest.getConfig() +
                    " with par10 time " + globalBest.getCost() +
                    " - generation avg par10: " + sum / populationSize);
            
            List<Individual> newPopulation = new ArrayList<Individual>();
            for (int i = 0; i < populationSize; i++) {
                if (rng.nextFloat() < crossoverProbability) {
                    Individual parent1 = tournamentSelect(population);
                    Individual parent2 = tournamentSelect(population);
                    while (parent2 == parent1) parent2 = tournamentSelect(population);
                    ParameterConfiguration child = pspace.crossover(parent1.getConfig(), parent2.getConfig(), rng);
                    int idSolverConfig = api.createSolverConfig(idExperiment, child, child.toString());
                    //System.out.println(parent1.getConfig() + " + " + parent2.getConfig() + " recombine to " + child);
                    newPopulation.add(new Individual(idSolverConfig, child));
                }
                else {
                    newPopulation.add(tournamentSelect(population));
                }
            }
            for (int i = 0; i < populationSize; i++) {
                if (rng.nextFloat() < mutationProbability) {
                    if (newPopulation.get(i).getCost() == null) { // this is an unevaluated crossover child, only update its config
                        pspace.mutateParameterConfiguration(rng, newPopulation.get(i).getConfig());
                    } else {
                        // this is an already evaluated individual, create a new solver configuration for the mutated version
                        ParameterConfiguration cfg = new ParameterConfiguration(newPopulation.get(i).getConfig());
                        pspace.mutateParameterConfiguration(rng, cfg);
                        if (!cfg.equals(newPopulation.get(i).getConfig())) { // mutation didn't change the parameters, don't create new config
                            int idSolverConfig = api.createSolverConfig(idExperiment, cfg, cfg.toString());
                            Individual ind = new Individual(idSolverConfig, cfg);
                            newPopulation.set(i, ind);
                        }
                    }
                }
                // replace old population
                population.set(i, newPopulation.get(i));
            }
        }
    }

}
