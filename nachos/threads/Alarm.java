package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	private static LinkedList<SleepingThread> sleepingThreadList = new LinkedList<>();

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean status = Machine.interrupt().disable();

		long currentTime = Machine.timer().getTime();
		int size = sleepingThreadList.size();

		if (size != 0)  {
			for (int i = 0; i < size; i++) {
				if(currentTime >=sleepingThreadList.get(i).getWakeTime()){
					sleepingThreadList.remove(i).getThread().ready();
					size --;
				}
			}
		}
		KThread.currentThread().yield();
		Machine.interrupt().restore(status);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 *
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 *
	 * @param x
	 *            the minimum number of clock ticks to wait.
	 *
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		boolean status = Machine.interrupt().disable();
		
		long wakeTime = Machine.timer().getTime() + x;
		SleepingThread sleepingThread = new SleepingThread(KThread.currentThread(),wakeTime);
		sleepingThreadList.add(sleepingThread);
		
//以下实现的是有序链表
		
//		int size = sleepingThreadList.size();
//		if(size == 0){
//			sleepingThreadList.add(sleepingThread);
//		}else{
//			for(int i = 0; i < size; i++){
//				if(wakeTime < sleepingThreadList.get(i).getWakeTime()){
//					sleepingThreadList.add(i,sleepingThread);
//					break;
//				}
//			}
//			sleepingThreadList.add(size,sleepingThread);
//		}
		KThread.currentThread().sleep();
		
		Machine.interrupt().restore(status);
	}

	public class SleepingThread {
		private KThread thread = null;
		private long wakeTime = 0;

		public SleepingThread(KThread thread, long wakeTime) {
			this.thread = thread;
			this.wakeTime = wakeTime;
		}

		public KThread getThread() {
			return thread;
		}

		public long getWakeTime() {
			return wakeTime;
		}
	}
}
