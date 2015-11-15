package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

public class PriorityScheduler extends Scheduler {

	public PriorityScheduler() {
	}

	public ThreadQueue newThreadQueue(boolean transferPriority) {// 分配一个线程队列
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {// 得到线程的优先级
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {// 得到线程的有效优先级
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {// 设置线程优先级
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {// 增加运行线程的优先级
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {// 降低运行线程的优先级
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1; // 新线程默认优先级

	public static final int priorityMinimum = 0; // 线程最低优先级是0

	public static final int priorityMaximum = 7; // 线程最高优先级是7

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread
	 *            the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {// 得到线程的优先级状态，如果线程优先级未创建则创建为默认优先级
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {// 优先级队列类，继承自线程队列

		PriorityQueue(boolean transferPriority) {// 自动调用父类无参数构造方法，创建一个线程队列

			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {// 传入等待队列的线程
			Lib.assertTrue(Machine.interrupt().disabled());

			getThreadState(thread).waitForAccess(this);

		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());

			if (!thread.getName().equals("main")) {
				getThreadState(thread).acquire(this);
			}
		}

		public KThread nextThread() {

			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me

			int max = -1;
			index = 0;
			ThreadState state = null, temp = null;
			while ((temp = pickNextThread()) != null) {
				if (temp.getEffectivePriority() > max) {
					state = temp;
					max = temp.getEffectivePriority();
				}

			}
			if (state == null) {
				return null;
			} else {

				return waitQueue.remove(waitQueue.indexOf(state)).thread;
			}

		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			// implement me
			if (index < waitQueue.size()) {
				index++;
				return waitQueue.get(index - 1);
			}

			return null;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;
		public LinkedList<ThreadState> waitQueue = new LinkedList<ThreadState>();
		public ThreadState linkedthread = null;
		private int index;
	}

	protected class ThreadState {

		public ThreadState(KThread thread) {
			this.thread = thread;
			setPriority(priorityDefault);
			waitQueue = new PriorityQueue(true);
		}

		public int getPriority() {
			return priority;
		}

		public int getEffectivePriority() {// 得到有效优先级
			// implement me

			effectivepriority = -1;

			for (int i = 0; i < waitQueue.waitQueue.size(); i++) {
				if (waitQueue.waitQueue.get(i).getEffectivePriority() > effectivepriority)
					effectivepriority = waitQueue.waitQueue.get(i).getEffectivePriority();
			}
			if (effectivepriority > priority)
				setPriority(effectivepriority);

			return priority;

		}

		public void setPriority(int priority) {// 优先级传递
			if (this.priority == priority)
				return;
			this.priority = priority;

			// implement me
		}

		public void waitForAccess(PriorityQueue waitQueue) {// 将此线程状态存入传入的等待队列
			// implement me

			waitQueue.waitQueue.add(this);

			if (waitQueue.linkedthread != null && waitQueue.linkedthread != this) {
				waitQueue.linkedthread.waitQueue.waitForAccess(this.thread);
			}

		}

		public void acquire(PriorityQueue waitQueue) {// 相当于一个线程持有的队列锁
			// implement me

			Lib.assertTrue(waitQueue.waitQueue.isEmpty());

			waitQueue.linkedthread = this;
		}

		protected KThread thread;// 这个对象关联的线程

		protected int priority;// 关联线程的优先级

		protected int effectivepriority;// 有效优先级

		protected PriorityQueue waitQueue;

	}
}