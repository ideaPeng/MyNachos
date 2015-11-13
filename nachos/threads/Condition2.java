package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {

	private Lock conditionLock;
	private ThreadQueue waitQueue = null;

	/**
	 * Allocate a new condition variable.
	 *
	 * @param conditionLock
	 *            the lock associated with this condition variable. The current
	 *            thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 *            <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean status = Machine.interrupt().disable();

		conditionLock.release();
		waitQueue.waitForAccess(KThread.currentThread());
		KThread.currentThread().sleep();// 失去锁的线程sleep，亦即阻塞，如果有其他线程wake本线程，则向下进行
		conditionLock.acquire();// 睡眠线程重新获得锁

		Machine.interrupt().restore(status);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean status = Machine.interrupt().disable();
		KThread thread = waitQueue.nextThread();
		if (thread != null) {
			thread.ready();// 只是单纯的放到等待队列哦，不是立刻执行哦
		}

		Machine.interrupt().restore(status);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean status = Machine.interrupt().disable();

		KThread thread = waitQueue.nextThread();
		while (thread != null) {
			thread.ready();
			thread = waitQueue.nextThread();
		}
		Machine.interrupt().restore(status);
	}
}