package org.hive2hive.core.processes.implementations.register;

import org.hive2hive.core.model.Locations;
import org.hive2hive.core.processes.framework.abstracts.ProcessStep;
import org.hive2hive.core.processes.framework.exceptions.InvalidProcessStateException;
import org.hive2hive.core.processes.framework.exceptions.ProcessExecutionException;
import org.hive2hive.core.processes.implementations.context.RegisterProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocationsCreationStep extends ProcessStep {

	private final static Logger logger = LoggerFactory.getLogger(LocationsCreationStep.class);

	private final RegisterProcessContext context;

	public LocationsCreationStep(RegisterProcessContext context) {
		this.context = context;
	}

	@Override
	protected void doExecute() throws InvalidProcessStateException, ProcessExecutionException {
		String userId = context.consumeUserId();
		logger.trace("Creating new user locations list. user id ='{}'", userId);
		context.provideUserLocations(new Locations(userId));
	}
}
