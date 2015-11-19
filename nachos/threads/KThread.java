package nachos.threads;

import nachos.machine.*;

public class KThread {

	private static final int statusNew = 0;
	private static final int statusReady = 1;
	private static final int statusRunning = 2;
	private static final int statusBlocked = 3;
	private static final int statusFinished = 4;
	private static final char dbgThread = 't';

	private static int numCreated = 0;// 标识从0开始
	private int id = numCreated++;
	private int join_counter = 0;
	private int status = statusNew;

	private String name = "(unnamed thread)";
	private Runnable target;
	private TCB tcb;

	private static ThreadQueue readyQueue = null;
	private static ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(true);

	private static KThread currentThread = null;
	private static KThread toBeDestroyed = null;
	private static KThread idleThread = null;

	/**
	 * Additional state used by schedulers.
	 * 
	 * @see nachos.threads.PriorityScheduler.ThreadState
	 */
	public Object schedulingState = null;

	public KThread(Runnable target) {
		this();
		this.target = target;
	}

	public KThread() {
		boolean status = Machine.interrupt().disable();

		if (currentThread != null) {
			tcb = new TCB();
		} else {
			currentThread = this;
			tcb = TCB.currentTCB();// 第一个线程是主线程，指向第一个TCB
			name = "main";
			readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
			readyQueue.acquire(this);
			restoreState();// 对主线程置运行状态
			createIdleThread();
		}
		waitQueue.acquire(this);

		Machine.interrupt().restore(status);
	}

	public void fork() {// 执行KThread
		Lib.assertTrue(status == statusNew);
		Lib.assertTrue(target != null);
		Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: " + target);

		boolean intStatus = Machine.interrupt().disable();// 关中断，在线程将要执行的准备阶段不能被打断

		tcb.start(new Runnable() {
			public void run() {
				runThread();// 方法内有target
			}
		});

		ready();// 并未真正开始执行，只是将线程移动到ready队列

		Machine.interrupt().restore(intStatus);// 回到机器原来的状态，就好像生成线程这件事从没发生过
	}

	private void runThread() {
		begin();
		target.run();// 执行target
		finish();
	}

	private void begin() {
		Lib.debug(dbgThread, "Beginning thread: " + toString());
		Lib.assertTrue(this == currentThread);

		restoreState();

		Machine.interrupt().enable();// 开中断
	}

	public static void finish() {
		Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());

		Machine.interrupt().disable();
		Machine.autoGrader().finishingCurrentThread();// 将TCB变成将要结束的TCB
		Lib.assertTrue(toBeDestroyed == null);

		toBeDestroyed = currentThread;// 将当前线程变为将要结束的线程，下一个线程运行的时候自动消除它
		currentThread.status = statusFinished;// 当前线程状态置为完成

		KThread thread = currentThread().waitQueue.nextThread();

		if (thread != null) {
			thread.ready();
		}
		sleep();// 将当前线程置为完成态，读取下一个就绪线程
	}

	public static void sleep() {
		// 如果线程执行完，则是从finish来，否则线程锁死，读取下一个线程
		Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());
		Lib.assertTrue(Machine.interrupt().disabled());
		if (currentThread.status != statusFinished)
			currentThread.status = statusBlocked;
		runNextThread();
	}

	private static void runNextThread() {// 执行下一个线程
		KThread nextThread = readyQueue.nextThread();

		if (nextThread == null)
			nextThread = idleThread;// 如果线程队列为空则执行idle线程

		nextThread.run();
	}

	private void run() {
		Lib.assertTrue(Machine.interrupt().disabled());

		Machine.yield();// 当前java线程放弃CPU
		currentThread.saveState();// 无实际操作
		Lib.debug(dbgThread, "Switching from: " + currentThread.toString() + " to: " + toString());

		currentThread = this;
		tcb.contextSwitch();
		currentThread.restoreState();

	}

	public static void yield() {// 运行线程放弃cpu，将当前线程放入就绪队列，读取就绪队列下一个线程运行
		Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());
		Lib.assertTrue(currentThread.status == statusRunning);

		boolean intStatus = Machine.interrupt().disable();

		currentThread.ready();// 正在执行的线程放入就绪队列，执行就绪队列的下一个线程
		runNextThread();// 运行下一个线程

		Machine.interrupt().restore(intStatus);
	}

	public void ready() {// 将线程移动到ready队列
		Lib.debug(dbgThread, "Ready thread: " + toString());
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(status != statusReady);

		status = statusReady;

		if (this != idleThread) {
			readyQueue.waitForAccess(this);

		} // 将线程移入队列，idle线程不用放入等待队列
		Machine.autoGrader().readyThread(this);// 空方法
	}

	private static void createIdleThread() {
		// the idleThread is used to yield() all the time,
		// and make sure thar the readyQueue always has a KThread
		Lib.assertTrue(idleThread == null);

		idleThread = new KThread(new Runnable() {
			public void run() {
				while (true)
					yield();
			}// idle线程一直执行的操作是yield（放弃）
		});
		idleThread.setName("idle");
		Machine.autoGrader().setIdleThread(idleThread);// 空方法
		idleThread.fork();
	}

	protected void restoreState() {
		// 恢复状态，执行此线程，如果有要结束的线程就结束它
		Lib.debug(dbgThread, "Running thread: " + currentThread.toString());
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
		Lib.assertTrue(tcb == TCB.currentTCB());

		Machine.autoGrader().runningThread(this);
		status = statusRunning;
		if (toBeDestroyed != null) {
			toBeDestroyed.tcb.destroy();
			toBeDestroyed.tcb = null;
			toBeDestroyed = null;
		}
	}

	public void join() {
		// 线程B中有A.join()语句，则B等A执行完才能执行
		Lib.debug(dbgThread, "Joining to thread: " + toString());
		Lib.assertTrue(this != currentThread);
		Lib.assertTrue(join_counter == 0);
		join_counter++;
		boolean status = Machine.interrupt().disable();

		if (this.status != statusFinished) {
			waitQueue.waitForAccess(KThread.currentThread());
			currentThread.sleep();

		}
		Machine.interrupt().restore(status);
	}

	public static KThread currentThread() {// 返回当前KThread
		Lib.assertTrue(currentThread != null);
		return currentThread;
	}

	private static class PingTest implements Runnable {
		PingTest(int which) {
			this.which = which;
		}

		public void run() {
			for (int i = 0; i < 5; i++) {
				System.out.println("*** thread " + which + " looped " + i + " times");
				currentThread.yield();// 每次执行一次就让权
			}
		}

		private int which;
	}

	public static void selfTest() {// 检测是否工作正常
		Lib.debug(dbgThread, "Enter KThread.selfTest");

		// new KThread(new PingTest(1)).setName("forked thread").fork();
		// new PingTest(0).run();
		// joinTest();
		// condition2Test();
		// alarmTest();
		// communicatorTest();
		// boatTest();
		// prioritySchduleTest();
		selftest_LotteryScheduler();
	}

	public static void joinTest() {// 检测join是否工作正常
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		System.out.println("______join test begin_____");

		final KThread thread1 = new KThread(new PingTest(1));
		thread1.setName("forked thread").fork();

		new KThread(new Runnable() {
			public void run() {
				System.out.println("b begins running");
				thread1.join();
				currentThread.yield();
				System.out.println("b is over");
			}
		}).fork();

	}

	public static void condition2Test() {// 检测Condition2是否工作正常
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		System.out.println("______Condition2 test begin_____");
		final Lock lock = new Lock();
		final Condition2 condition2 = new Condition2(lock);

		new KThread(new Runnable() {
			public void run() {
				lock.acquire();// 线程执行之前获得锁
				KThread.currentThread().yield();
				condition2.sleep();
				System.out.println("thread1 executing");
				condition2.wake();

				lock.release();// 线程执行完毕将锁释放
				System.out.println("thread1 execute successful");
			}
		}).fork();

		new KThread(new Runnable() {
			public void run() {
				lock.acquire();// 线程执行之前获得锁

				KThread.currentThread().yield();
				condition2.wake();
				System.out.println("thread2 executing");
				condition2.sleep();

				lock.release();// 线程执行完毕将锁释放

				System.out.println("thread2 execute successful");
			}
		}).fork();

	}

	public static void alarmTest() {// 检测Alarm是否工作正常

		new KThread(new Runnable() {
			public void run() {
				System.out.println(Machine.timer().getTime());

				ThreadedKernel.alarm.waitUntil(300);

				System.out.println(Machine.timer().getTime());
				System.out.println("successful");
			}
		}).fork();

	}

	public static void communicatorTest() {// 检测Communicator是否工作正常
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		System.out.println("______Communicator test begin_____");
		final Communicator communicator = new Communicator();

		new KThread(new Runnable() {
			public void run() {
				communicator.speak(20);

				System.out.println("thread1 successful");
			}
		}).fork();

		new KThread(new Runnable() {
			public void run() {

				communicator.speak(30);
				System.out.println("thread2 successful");
			}
		}).fork();

		new KThread(new Runnable() {

			public void run() {

				System.out.println(communicator.listen());
				System.out.println("thread3 successful");

			}
		}).fork();

		new KThread(new Runnable() {

			public void run() {

				System.out.println(communicator.listen());
				System.out.println("thread4 successful");

			}
		}).fork();

	}

	public static void boatTest() {// 检测join是否工作正常
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		System.out.println("______Boat test begin_____");

		new KThread(new Runnable() {
			public void run() {

				Boat.selfTest();
				System.out.println("successful");
			}
		}).fork();

	}

	public static void prioritySchduleTest() {

		final KThread thread1 = new KThread(new Runnable() {
			public void run() {
				for (int i = 0; i < 3; i++) {
					KThread.currentThread().yield();
					System.out.println("thread1");
				}
			}
		});

		KThread thread2 = new KThread(new Runnable() {
			public void run() {
				for (int i = 0; i < 3; i++) {
					KThread.currentThread().yield();
					System.out.println("thread2");
				}
			}
		});

		KThread thread3 = new KThread(new Runnable() {
			public void run() {
				thread1.join();
				for (int i = 0; i < 3; i++) {
					KThread.currentThread().yield();
					System.out.println("thread3");
				}
			}
		});

		boolean status = Machine.interrupt().disable();

		ThreadedKernel.scheduler.setPriority(thread1, 2);
		ThreadedKernel.scheduler.setPriority(thread2, 4);
		ThreadedKernel.scheduler.setPriority(thread3, 6);

		Machine.interrupt().restore(status);

		thread1.fork();
		thread2.fork();
		thread3.fork();

	}

	public static void selftest_LotteryScheduler() {

		final KThread threadA = new KThread(new Runnable() {
			public void run() {
				KThread.currentThread().yield();
				System.out.println("Thread A get the lottery,16.7%");
			}
		});

		KThread threadB = new KThread(new Runnable() {
			public void run() {
				KThread.currentThread().yield();
				System.out.println("ThreadB get the lottery,33.3%");
			}
		});

		KThread threadC = new KThread(new Runnable() {
			public void run() {
				System.out.println("ThreadC got the lottery,66.7% ");
				threadA.join();
				System.out.println("ThreadC is Over");
			}
		});

		boolean status = Machine.interrupt().disable();

		ThreadedKernel.scheduler.setPriority(threadA, 2);
		ThreadedKernel.scheduler.setPriority(threadB, 4);
		ThreadedKernel.scheduler.setPriority(threadC, 6);

		Machine.interrupt().restore(status);

		threadA.fork();
		threadB.fork();
		threadC.fork();

	}

	public KThread setTarget(Runnable target) {// 设置target，返回自身
		Lib.assertTrue(status == statusNew);

		this.target = target;
		return this;
	}

	public KThread setName(String name) {// 设置名字，返回自身
		this.name = name;
		return this;
	}

	public String getName() {// 返回名字
		return name;
	}

	public String toString() {// 返回名字和自身标识
		return (name + " (#" + id + ")");
	}

	public int compareTo(Object o) {// 通过比较标识符来比较线程大小
		KThread thread = (KThread) o;

		if (id < thread.id)
			return -1;
		else if (id > thread.id)
			return 1;
		else
			return 0;
	}

	protected void saveState() {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
	}
}