package edu.tamu.aser.mcr;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;

import edu.tamu.aser.mcr.config.Configuration;
import edu.tamu.aser.mcr.trace.Trace;

public class StartExploring implements Runnable {

	private Trace traceObj;

	private Vector<String> schedule_prefix;

	private Queue<List<String>> exploreQueue;

	public static class BoxInt {

		volatile int  value;

		public BoxInt(int initial) {
			this.value = initial;
		}

		public synchronized int getValue() {
			return this.value;
		}

		public synchronized void increase() {
			this.value++;
		}

		public synchronized void decrease() {
			this.value--;
		}
	}

	public final static BoxInt executorsCount = new BoxInt(0);

	public StartExploring(Trace trace, Vector<String> prefix,
			Queue<List<String>> queue) {
		this.traceObj = trace;
		this.schedule_prefix = prefix;
		this.exploreQueue = queue;
	}

	public Trace getTrace() {
		return this.traceObj;
	}

	public Vector<String> getCurrentSchedulePrefix() {
		return this.schedule_prefix;
	}

	public Queue<List<String>> exploreQueue() {
		return this.exploreQueue;
	}

	/**
	 * start exploring other interleavings
	 * 
	 */
	public void run() {
		try {
			ExploreSeedInterleavings.setQueue(exploreQueue);
			traceObj.finishedLoading(true);
			ExploreSeedInterleavings.execute(traceObj, schedule_prefix);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
		}
		finally {
			if (Configuration.DEBUG) {
				System.out.println("  Exploration Done with this trace! >>\n\n");
			}
		}
	}
}
