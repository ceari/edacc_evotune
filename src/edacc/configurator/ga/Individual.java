package edacc.configurator.ga;

import edacc.parameterspace.ParameterConfiguration;

public class Individual implements Comparable<Individual> {
	private ParameterConfiguration config;
	private int idSolverConfiguration;
	private Float cost;
	private String name;
	
	public Individual(int idSolverConfiguration, ParameterConfiguration config, String name) {
		this.idSolverConfiguration = idSolverConfiguration;
		this.config = config;
		this.cost = null;
		this.name = name;
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

    @Override
    public int compareTo(Individual o) {
        return this.cost.compareTo(o.cost);
    }
}
