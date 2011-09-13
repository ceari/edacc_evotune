package edacc.configurator.ga;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import edacc.api.API;
import edacc.api.APIImpl;
import edacc.api.costfunctions.CostFunction;
import edacc.api.costfunctions.PARX;
import edacc.model.ExperimentResult;
import edacc.parameterspace.ParameterConfiguration;
import edacc.parameterspace.graph.ParameterGraph;
import edacc.util.Pair;

/**
 * Genetic algorithm configurator using the EDACC API.
 * 
 * Algorithm:
 * initialize & evaluate population
 * while not termination criterion reached maxTerminationCriterionHits times do:
 *    do #populationSize of times:
 *        with a crossover probability select 2 parents, recombine them, add child to new generation
 *        otherwise copy one individual into new generation
 *    
 *    for each individual of the new population do:
 *      mutate each gene with a mutation probability
 *    
 *    replace old generation with new generation
 *    evaluate population
 *    
 * ----
 * 
 * Termination criterion: generation average cost has to be better than 0.95 times the average
 * of the prevrious generation in order to continue
 * 
 * Parent selection is done via tournament selection of size 5.
 * 5 individuals of the current population are picked at random and the best
 * is selected.
 * 
 * ----
 * 
 * Evaluation of each individual is done on all instances of the EDACC experiment.
 * The cost of an inidividual is its average runtime of all runs.
 */
public class GAConfigurator {
    private int populationSize = 40;
    private int tournamentSize = 3;
    private float crossoverProbability = 0.8f;
    private float mutationProbability = 0.1f;
    private float mutationStandardDeviationFactor = 0.1f;
    private int maxTerminationCriterionHits = 3;
    private boolean useExistingConfigs = false;
    
    private CostFunction costFunction = new PARX(10);
    
    private int terminationCriterionHits = 0;
    private int idExperiment;
    private API api;
    //private List<InstanceSeedPair> parcour;
    private Random rng;
    private ParameterGraph pspace;
    private int jobCPUTimeLimit;
    private boolean use2PointCrossover = false;

    /** 
     * Read config file and start the configuration
     * @param args
     * @throws Exception
     */
    public static void main(String... args) throws Exception {
        if (args.length < 1) {
            System.out.println("Missing configuration file. Use java -jar GA.jar <config file path>");
            return;
        }
        Scanner scanner = new Scanner(new File(args[0]));
        String hostname = "", user = "", password = "", database = "";
        int idExperiment = 0;
        int port = 3306;
        int populationSize = 42;
        int tournamentSize = 4;
        float crossoverProbability = 0.8f;
        float mutationProbability = 0.05f;
        float mutationStandardDeviationFactor = 0.05f;
        int maxTerminationCriterionHits = 4;
        int jobCPUTimeLimit = 13;
        int numRunsPerInstance = 2;
        long seed = System.currentTimeMillis();
        boolean use2PointCrossover = false;
        boolean useExistingConfigs = false;
        
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().startsWith("%")) continue;
            String[] keyval = line.split("=");
            String key = keyval[0].trim();
            String value = keyval[1].trim();
            if ("host".equals(key)) hostname = value;
            else if ("user".equals(key)) user = value;
            else if ("password".equals(key)) password = value;
            else if ("port".equals(key)) port = Integer.valueOf(value);
            else if ("database".equals(key)) database = value;
            else if ("idExperiment".equals(key)) idExperiment = Integer.valueOf(value);
            else if ("populationSize".equals(key)) populationSize = Integer.valueOf(value);
            else if ("tournamentSize".equals(key)) tournamentSize = Integer.valueOf(value);
            else if ("crossoverProbability".equals(key)) crossoverProbability = Float.valueOf(value);
            else if ("mutationProbability".equals(key)) mutationProbability = Float.valueOf(value);
            else if ("mutationStandardDeviationFactor".equals(key)) mutationStandardDeviationFactor = Float.valueOf(value);
            else if ("maxTerminationCriterionHits".equals(key)) maxTerminationCriterionHits = Integer.valueOf(value);
            else if ("jobCPUTimeLimit".equals(key)) jobCPUTimeLimit = Integer.valueOf(value);
            else if ("numRunsPerInstance".equals(key)) numRunsPerInstance = Integer.valueOf(value);
            else if ("seed".equals(key)) seed = Long.valueOf(value);
            else if ("useExistingConfigs".equals(key)) useExistingConfigs = Integer.valueOf(value) == 1;
            else if ("use2PointCrossover".equals(key)) use2PointCrossover = Integer.valueOf(value) == 1;
        }
        scanner.close();
        GAConfigurator ga = new GAConfigurator(hostname, port, user, password, database,
                idExperiment, populationSize, tournamentSize, crossoverProbability, mutationProbability,
                mutationStandardDeviationFactor, maxTerminationCriterionHits, numRunsPerInstance,
                jobCPUTimeLimit, seed, use2PointCrossover, useExistingConfigs);
        ga.evolve();
        ga.shutdown();
    }

    public GAConfigurator(String hostname, int port, String username,
            String password, String database, int idExperiment,
            int populationSize, int tournamentSize, float crossoverProbability,
            float mutationProbability, float mutationStandardDeviationFactor,
            int maxTerminationCriterionHits,
            int numRunsPerInstance, int jobCPUTimeLimit, long seed, boolean use2PointCrossover,
            boolean useExistingConfigs) throws Exception {
        if (populationSize % 2 != 0 || populationSize <= 0) throw new IllegalArgumentException("Population size has to be a multiple of 2 and >= 2.");
        api = new APIImpl();
        api.connect(hostname, port, database, username, password);
        this.idExperiment = idExperiment;
        this.populationSize = populationSize;
        this.tournamentSize = tournamentSize;
        this.crossoverProbability = crossoverProbability;
        this.mutationProbability = mutationProbability;
        this.mutationStandardDeviationFactor = mutationStandardDeviationFactor;
        this.maxTerminationCriterionHits = maxTerminationCriterionHits;
        this.useExistingConfigs = useExistingConfigs;
        rng = new edacc.util.MersenneTwister(seed);
        pspace = api.loadParameterGraphFromDB(idExperiment);
        if (pspace == null) throw new Exception("No parameter graph found.");
        this.jobCPUTimeLimit = jobCPUTimeLimit;
    }

    public void shutdown() {
        api.disconnect();
    }

    protected List<Individual> initializePopulation(int size) throws Exception {
        List<Individual> population = new ArrayList<Individual>();
        if (useExistingConfigs) {
            List<Integer> bestConfigs = api.getBestConfigurations(idExperiment, costFunction, size);
            for (Integer idSolverConfig: bestConfigs) {
                Individual ind = new Individual(api.getParameterConfiguration(idExperiment, idSolverConfig));
                ind.setCost(api.getSolverConfigurationCost(idSolverConfig));
                ind.setIdSolverConfiguration(idSolverConfig);
                ind.setName(api.getSolverConfigName(idSolverConfig));
                System.out.println("using existing config " + ind.getConfig() + " with cost " + ind.getCost());
                population.add(ind);
            }
            for (int i = 0; i < size - bestConfigs.size(); i++) {
                ParameterConfiguration cfg = pspace.getRandomConfiguration(rng);
                population.add(new Individual(cfg));
            }
        }
        else {
            for (int i = 0; i < size; i++) {
                ParameterConfiguration cfg = pspace.getRandomConfiguration(rng);
                population.add(new Individual(cfg));
            }
        }
        return population;
    }

    protected void evaluatePopulation(List<Individual> population, int generation) throws Exception {
        List<Integer> jobs = new ArrayList<Integer>();
        int courseLength = api.getCourseLength(idExperiment);
        int numJobs = Math.min(generation * courseLength / 10, courseLength);
        for (Individual ind : population) {
            if (ind.getIdSolverConfiguration() != 0) continue;
            // check if an equal solver config already exists and use its results
            // the cost of this existing config has to be set at the end because it could
            // be that two equal configs were created but not evaluated
            int idSolverConfig = api.exists(idExperiment, ind.getConfig());
            if (idSolverConfig != 0) {
                ind.setIdSolverConfiguration(idSolverConfig);
                ind.setName(api.getSolverConfigName(idSolverConfig));
            } else { // otherwise create a new solver configuration and launch jobs
                String name = "Gen " + generation + " " + api.getCanonicalName(idExperiment, ind.getConfig());
                ind.setIdSolverConfiguration(api.createSolverConfig(idExperiment, ind.getConfig(), name));
                ind.setCost(null);
                ind.setName(name);
                int[] cpuTimeLimits = new int[courseLength];
                for (int i = 0; i < courseLength; i++) cpuTimeLimits[i] = jobCPUTimeLimit;
                jobs.addAll(api.launchJob(idExperiment, ind.getIdSolverConfiguration(), cpuTimeLimits, numJobs, rng));
            }
        }
        
        Map<Integer, ExperimentResult> results;
        while (true) {
            Thread.sleep(3000);
            boolean all_done = true;
            results = api.getJobsByIDs(jobs);
            for (ExperimentResult result: results.values()) {
                all_done &= (result.getStatus().getStatusCode() >= 1 ||
                             result.getStatus().getStatusCode() < -1);
            }
            if (all_done) break;
        }
        
        Map<Integer, List<ExperimentResult>> resultsBySolverConfig = new HashMap<Integer, List<ExperimentResult>>();
        for (ExperimentResult result: results.values()) {
        	if (!resultsBySolverConfig.containsKey(result.getSolverConfigId())) {
        		resultsBySolverConfig.put(result.getSolverConfigId(), new ArrayList<ExperimentResult>());
        	}
        	resultsBySolverConfig.get(result.getSolverConfigId()).add(result);
        }
        
        for (Integer idSolverConfig: resultsBySolverConfig.keySet()) {
            float cost = costFunction.calculateCost(resultsBySolverConfig.get(idSolverConfig));
            api.updateSolverConfigurationCost(idSolverConfig, cost, costFunction);
            
            for (Individual ind: population) {
                if (ind.getIdSolverConfiguration() == idSolverConfig) {
                    ind.setCost(cost);
                }
            }
        }
        
        for (Individual ind: population) {
            if (ind.getCost() == null) {
                ind.setCost(api.getSolverConfigurationCost(ind.getIdSolverConfiguration()));
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
    
    protected boolean terminationCriterion(Float generationAverage, List<Individual> population) {
        if (generationAverage == null) return false;
        float sum = 0;
        for (int i = 0; i < populationSize; i++) {
            sum += population.get(i).getCost();
        }
        float avg = sum / population.size();
        
        if (avg > (generationAverage * 0.95)) {
            // if the current average is not better than 0.95 times the old
            // average we haven't made significant progress -> terminate
            terminationCriterionHits++;
            if (terminationCriterionHits >= maxTerminationCriterionHits) return true;
        }
        return false;
    }

    public void evolve() throws Exception {
        Individual globalBest = null;
        Float generationAverage = null;
        int generation = 1;
        
        List<Individual> population = initializePopulation(populationSize);
        evaluatePopulation(population, generation);
        
        while (!terminationCriterion(generationAverage, population)) {
            // keep track of global best individual
            float sum = 0;
            for (int i = 0; i < populationSize; i++) {
                sum += population.get(i).getCost();
                if (globalBest == null || population.get(i).getCost() < globalBest.getCost()) {
                    globalBest = new Individual(population.get(i));
                }
            }
            // update generationAverage
            generationAverage = sum / populationSize;
            
            // print some information
            System.out.println("---------\nGeneration " + generation + " - global best: " + globalBest.getName() +
                    " with average time " + globalBest.getCost() +
                    " - generation avg: " + generationAverage);
            
            
            // prepare mating pool (parent selection)
            List<Individual> matingPool = new ArrayList<Individual>();
            for (int i = 0; i < populationSize; i++) {
                matingPool.add(tournamentSelect(population));
            }
            
            List<Individual> newPopulation = new ArrayList<Individual>();
            // parent recombination
            for (int i = 0; i < populationSize; i += 2) {
                if (rng.nextFloat() < crossoverProbability) {
                    Individual parent1 = matingPool.get(i);
                    Individual parent2 = matingPool.get((i+1) % populationSize); // wrap around
                    Pair<ParameterConfiguration, ParameterConfiguration> children;
                    if (use2PointCrossover) {
                        children = pspace.crossover2Point(parent1.getConfig(), parent2.getConfig(), rng);
                    } else {
                        children = pspace.crossover(parent1.getConfig(), parent2.getConfig(), rng);
                    }
                    newPopulation.add(new Individual(children.getFirst()));
                    newPopulation.add(new Individual(children.getSecond()));
                }
                else {
                    newPopulation.add(matingPool.get(i));
                    newPopulation.add(matingPool.get((i+1) % populationSize));
                }
            }
            
            // mutation
            for (int i = 0; i < populationSize; i++) {
                pspace.mutateParameterConfiguration(rng, newPopulation.get(i).getConfig(), mutationStandardDeviationFactor, mutationProbability);
                // replace old population
                population.set(i, newPopulation.get(i));
            }
            
            generation += 1;
            evaluatePopulation(population, generation);
        }
        
        
        // print some information about the final population
        float sum = 0;
        for (int i = 0; i < populationSize; i++) {
            sum += population.get(i).getCost();
            if (globalBest == null || population.get(i).getCost() < globalBest.getCost()) {
                globalBest = population.get(i);
            }
        }
        generationAverage = sum / populationSize;
        System.out.println("---------\nno significant improvement in generation average - terminating");
        System.out.println("Generation " + generation + " - global best: " + globalBest.getName() +
                " with average time " + globalBest.getCost() +
                " - generation avg: " + generationAverage + "----------------------\n----------------------");

    }
}
