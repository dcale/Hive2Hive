package org.hive2hive.processes.framework.concretes;

import java.util.Collection;
import java.util.LinkedList;

import org.hive2hive.processes.framework.ProcessState;
import org.hive2hive.processes.framework.RollbackReason;
import org.hive2hive.processes.framework.abstracts.Process;
import org.hive2hive.processes.framework.abstracts.ProcessComponent;
import org.hive2hive.processes.framework.exceptions.InvalidProcessStateException;

public class SequentialProcess extends Process {

	LinkedList<ProcessComponent> components = new LinkedList<ProcessComponent>();

	private int executionIndex = 0;
	private int rollbackIndex = 0;

	@Override
	public void join() {
		// TODO Auto-generated method stub

	}

	@Override
	protected void doExecute() throws InvalidProcessStateException {
				
		while (executionIndex < components.size() && getState() == ProcessState.RUNNING) {
			
			ProcessComponent next = components.get(executionIndex);
			next.start();
			executionIndex++;
		}
		
//		for (int i = 0; i < components.size(); i++, executionIndex++) {
//			ProcessComponent next = components.get(i);
//			next.start();
//		}
//		notifySucceeded();
	}

	@Override
	protected void doPause() {
		// TODO Auto-generated method stub
	}
	
	@Override
	protected void doResumeExecution() throws InvalidProcessStateException {
		// TODO Auto-generated method stub
	}
	
	@Override
	protected void doResumeRollback() {
		// TODO Auto-generated method stub
	}

	@Override
	protected void doRollback(RollbackReason reason) throws InvalidProcessStateException {

		rollbackIndex = executionIndex - 1;
		
		while (rollbackIndex >= 0 && getState() == ProcessState.ROLLBACKING) {
			
			ProcessComponent last = components.get(rollbackIndex);
			last.cancel(reason);
			rollbackIndex--;
		}
		
//		rollbackIndex = executionIndex - 1;
//		
//		for (int i = rollbackIndex; i >= 0; i--, rollbackIndex--) {
//			ProcessComponent last = components.get(i);
//			last.cancel(reason);
//		}
//		notifyFailed();
	}

	@Override
	protected void doAdd(ProcessComponent component) {
		components.add(component);
	}

	@Override
	protected void doRemove(ProcessComponent component) {
		components.remove(component);
	}

	@Override
	public Collection<ProcessComponent> getComponents() {
		return components;
	}
	
}
