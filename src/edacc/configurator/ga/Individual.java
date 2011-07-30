package edacc.configurator.ga;

import edacc.parameterspace.ParameterConfiguration;

public class Individual implements Comparable<Individual> {
	private ParameterConfiguration config;
	private int idSolverConfiguration;
	private Float cost;
	private String name;
	
	public Individual(ParameterConfiguration config) {
		this.config = config;
		this.cost = null;
		this.idSolverConfiguration = 0;
		this.name = null;
	}
	
	public Individual(Individual other) {
	    this.config = new ParameterConfiguration(other.config);
	    this.cost = other.cost;
	    this.idSolverConfiguration = other.idSolverConfiguration;
	    this.name = other.name;
	}

	public final String getName() {
        return name;
    }

    public final void setName(String name) {
        this.name = name;
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
	
    public final void setConfig(ParameterConfiguration config) {
        this.config = config;
    }

    public final void setIdSolverConfiguration(int idSolverConfiguration) {
        this.idSolverConfiguration = idSolverConfiguration;
    }

    @Override
    public int compareTo(Individual o) {
        return this.cost.compareTo(o.cost);
    }
}
