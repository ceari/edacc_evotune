package edacc.configurator.ga;

import edacc.parameterspace.ParameterConfiguration;

public class Individual implements Comparable<Individual> {
	private ParameterConfiguration config;
	private int idSolverConfiguration;
	private Float cost;
	
	public Individual(int idSolverConfiguration, ParameterConfiguration config) {
		this.idSolverConfiguration = idSolverConfiguration;
		this.config = config;
		this.cost = null;
	}

	public final Float getCost() {
		return cost;
	}

	public final void setCost(Float cost) {
		this.cost = cost;
	}

	public final ParameterConfiguration getConfig() {
		return config;
	}

	public final int getIdSolverConfiguration() {
		return idSolverConfiguration;
	}
	
	

    @Override
    public int compareTo(Individual o) {
        return this.cost.compareTo(o.cost);
    }
}
