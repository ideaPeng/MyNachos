package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

public class LotteryScheduler extends Scheduler {

	public LotteryScheduler() {
	}

	public ThreadQueue newThreadQueue(boolean transferPriority) {// 分配一个线程队列
		return new LotteryQueue(transferPriority);
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
	protected class LotteryQueue extends ThreadQueue {// 优先级队列类，继承自线程队列

		LotteryQueue(boolean transferPriority) {// 自动调用父类无参数构造方法，创建一个线程队列

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

			if (waitQueue.isEmpty())
				return null;

			int alltickets = 0;

			for (int i = 0; i < waitQueue.size(); i++)// 计算队列中所有彩票的总数
			{
				ThreadState thread = waitQueue.get(i);
				alltickets = alltickets + thread.getEffectivePriority();

			}

			int numOfWin = Lib.random(alltickets + 1);// 产生中奖的数目

			int nowtickets = 0;
			KThread winThread = null;
			ThreadState thread = null;
			for (int i = 0; i < waitQueue.size(); i++)// 得到获胜的线程
			{
				thread = waitQueue.get(i);
				nowtickets = nowtickets + thread.getEffectivePriority();
				if (nowtickets >= numOfWin) {
					winThread = thread.thread;
					break;
				}

			}

			if (winThread != null)
				waitQueue.remove(thread);

			return winThread;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {

			// implement me (if you want)

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
		//private int index;
	}

	protected class ThreadState {

		public ThreadState(KThread thread) {
			this.thread = thread;
			setPriority(priorityDefault);
			waitQueue = new LotteryQueue(true);
		}

		public int getPriority() {
			return priority;
		}

		public int getEffectivePriority() {// 得到有效优先级
			// implement me

			effectivepriority = priority;

			for (int i = 0; i < waitQueue.waitQueue.size(); i++)

				effectivepriority = effectivepriority + waitQueue.waitQueue.get(i).getEffectivePriority();// 把等待线程持有的彩票的总数给这个线程

			return effectivepriority;

		}

		public void setPriority(int priority) {// 优先级传递
			if (this.priority == priority)
				return;
			this.priority = priority;

			// implement me
		}

		public void waitForAccess(LotteryQueue waitQueue) {// 将此线程状态存入传入的等待队列
			// implement me
			waitQueue.waitQueue.add(this);

			if (waitQueue.linkedthread != null && waitQueue.linkedthread != this) {
				waitQueue.linkedthread.waitQueue.waitForAccess(this.thread);
			}

		}

		public void acquire(LotteryQueue waitQueue) {// 相当于一个线程持有的队列锁

			waitQueue.linkedthread = this;
		}

		protected KThread thread;// 这个对象关联的线程

		protected int priority;// 关联线程的优先级

		protected int effectivepriority;// 有效优先级

		protected LotteryQueue waitQueue;

	}
}