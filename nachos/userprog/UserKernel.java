package nachos.userprog;

import java.util.*;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */

public class UserKernel extends ThreadedKernel {
	
	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;

	public static Lock allocateMemoryLock;// 内存分配锁，在用户程序申请内存页的时候使用

	public static LinkedList<Integer> memoryLinkedList;// 内存页的链表，用于存放空闲内存页的编号
	
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();// 什么都没做
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);// 设置调度程序和文件系统，创建第一个线程(主线程和idel线程)和alarm

		console = new SynchConsole(Machine.console());// 创建控制台

		Machine.processor().setExceptionHandler(new Runnable() {// 设置异常处理器
			public void run() {
				exceptionHandler();
			}
		});

		memoryLinkedList = new LinkedList(); // 初始化内存链表
		for (int i = 0; i < Machine.processor().getNumPhysPages(); i++)
			memoryLinkedList.add((Integer) i); // 将虚拟内存分页并放入链表中

		allocateMemoryLock = new Lock();// 初始化内存分配锁
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();// KThread，Semaphore，SynchList检测

	}

	/**
	 * Returns the current process.
	 *
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {// 返回当前用户进程
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 *
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {// 异常处理器
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;// 获取当前用户进程
		int cause = Machine.processor().readRegister(Processor.regCause);// 得到产生异常的编号
		process.handleException(cause);// 进程根据编号处理异常
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 *
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {

		super.run();// 空方法

		UserProcess process = UserProcess.newUserProcess();// 第一个用户进程,是shell，通过shell来装入执行其他的用户程序

		String shellProgram = Machine.getShellProgramName();// 返回Kernel.shellProgram这个名字

		Lib.assertTrue(process.execute(shellProgram, new String[] {}));// 执行shell进程，并且确保一定成功

		KThread.currentThread().finish();// 结束这个进程

	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}
}