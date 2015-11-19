package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.LinkedList;

/*
 * Encapsulates（封装） the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed. This class is
 * extended by other classes to support additional functionality (such as
 * additional syscalls). 
 * 将用户进程的状态封装起来而不是包含用户的线程。这包括地址的转换状态，文件表，以及程序执行的信息。
 * 这个类有其他的类扩展以便支持额外的功能
 */
public class UserProcess {

	OpenFile openfiles[] = new OpenFile[16];// 进程打开的文件表
	/** The program being run by this process. */
	protected Coff coff;// 进程对应的coff

	public int status = 0;// 进程的状态
	/** This process's page table. */
	protected TranslationEntry[] pageTable;// 页表
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;// 页数

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;// 堆栈占得页数

	private int initialPC, initialSP;

	private int argc, argv;

	public UThread thread = null;// 这个进程所对应的实际线程

	private int pid = 0;// 进程号

	public boolean normalExit = false;// 退出状态，是否为正常退出

	public LinkedList<UserProcess> childProcess = new LinkedList();// 所创建的子进程链表

	public UserProcess parentProcess = null;// 创建这个进程的父进程

	private static int numOfProcess = 0;// 进程计数器

	private static int numOfRunningProcess = 0;// 运行进程计数器

	private static final int pageSize = Processor.pageSize;// 页大小

	private Lock joinLock = new Lock();// 进程join方法等待锁

	private Condition joinCondition = new Condition(joinLock);// join方法使用的条件变量

	private static final char dbgProcess = 'a';

	/*
	 * Allocate a new process.
	 */

	public UserProcess() {

		pid = numOfProcess++;
		numOfRunningProcess++;
		// 线程创建后，键盘输入流和显示输出流在不调用open方法的情况下自动打开
		openfiles[0] = UserKernel.console.openForReading();
		openfiles[1] = UserKernel.console.openForWriting();

	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {// 调用了上面的构造器创建一个进程
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 *         用特定参数执行一个特定的线程，尝试加载程序，然后创建线程执行它
	 */
	public boolean execute(String name, String[] args) {

		if (!load(name, args))
			return false;// 如果不能导入就返回false，程序执行失败

		thread = new UThread(this);// 产生新的用户线程，并执行
		thread.setName(name).fork();
		return true;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		// 这里的filesystem实际是stubfilesystem的一个实例，executable也是一个StubOpenFile，里面有一个物理文件，可以随机访问
		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);// 如果文件系统存在就打开，否则返回null
		// System.out.println(executable);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;// 文件不存在时，无法加载，不能执行
		}

		try {
			coff = new Coff(executable);// 用coff代表磁盘中的实际文件，导入虚拟内存时，从间接从物理磁盘导入

		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		numPages = 0;

		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}

			numPages += section.getLength();// 得到section中所有页数
		}
		// 确保argv数组的适合一页的长度
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;// 参数总长度，4是指针长度 ，1是空值
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;// 程序的页数还要加上堆栈的页数
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;// 最后一页用来存放参数

		if (!loadSections())// 把每一块导入
			return false;

		// store arguments in last page
		// 把参数存进最后一页
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 *         为这个进程分配内存，将coff块导入进内存，如果成功，进程就会执行（这是进程初始化会失败的最后一步）
	 */
	protected boolean loadSections() {

		UserKernel.allocateMemoryLock.acquire();// 获得锁，确保内存分配时原子性的

		if (numPages > UserKernel.memoryLinkedList.size()) {// 如果程序的页数大于处理器的空闲物理页数，就会失败
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.allocateMemoryLock.release();
			return false;
		}

		pageTable = new TranslationEntry[numPages];// 创建页表
		for (int i = 0; i < numPages; i++) {
			int nextPage = UserKernel.memoryLinkedList.remove();// 将要分配的页从空闲内存链表中取出

			pageTable[i] = new TranslationEntry(i, nextPage, true, false, false, false);// 虚拟页号，实际页号，是否有效，是否只读，是否使用，脏位

		}
		UserKernel.allocateMemoryLock.release();// 内存分配完毕，释放锁

		// 将每一块导入
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess,
					"\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;// 得到虚拟页号
				pageTable[vpn].readOnly = section.isReadOnly();
				section.loadPage(i, pageTable[vpn].ppn);// 将每一部分的每一页导入到内存中，i是段内的序号，ppn是内存实际
														// 序号

			}
		}

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);// 将处理器的页表置为这个线程的页表
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found. 从这个进程的虚拟内存中读一个null结尾的字符串。从特定的地址读最多 maxlength+1长度的字符。搜索null
	 *         终止符，包括这个终止符把它转化为String类型，如果没有终止符，返回null
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		// 从虚拟内存读字符串
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		// 将指定虚拟地址的数据从内存中读出，放入字符数组中
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the
	 *            array.
	 * @return the number of bytes successfully transferred.
	 *         从进程的虚拟内存中将数据转化为特定的数组。这个方法处理地址转换的细节。这个方法在发生错误时必须不能
	 *         损坏当前进程，但是应该返回成功复制的字节的数量，如果没有数据复制返回0
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (length > (pageSize * numPages - vaddr))
			length = pageSize * numPages - vaddr;// 读出的长度超过了文件的长度,只能读出从虚拟地址到结尾的内容
		if (data.length - offset < length)
			length = data.length - offset;// 读出长度超过了数组的长度,只能读出从指针到结尾的长度

		int transferredbyte = 0;// 已转换的字符数

		do {

			int pageNum = Processor.pageFromAddress(vaddr + transferredbyte);// 得到页号

			if (pageNum < 0 || pageNum >= pageTable.length)// 判断页号的合法性
				return 0;

			int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);// 得到在页中的索引

			int leftByte = pageSize - pageOffset;// 这一页剩下的字符数量

			int amount = Math.min(leftByte, length - transferredbyte);// 得到这一次要读的字符数量

			int realAddress = pageTable[pageNum].ppn * pageSize + pageOffset;// 得到虚拟内存中的实际地址

			System.arraycopy(memory, realAddress, data, offset + transferredbyte, amount);// 将数据从memory复制到data中

			transferredbyte = transferredbyte + amount; // 转换数量增加
		} while (transferredbyte < length);

		return transferredbyte;// 返回转换的数量
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {// 将字符串写入内存中
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual
	 *            memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (length > (pageSize * numPages - vaddr))
			length = pageSize * numPages - vaddr;// 写入的长度超过了文件的长度只能写入从虚拟地址到结尾的内容
		if (data.length - offset < length)
			length = data.length - offset;// 写入长度超过了数组的长度只能写入从指针到结尾的长度

		int transferredbyte = 0;
		do {

			int pageNum = Processor.pageFromAddress(vaddr + transferredbyte);
			if (pageNum < 0 || pageNum >= pageTable.length)
				return 0;
			int pageOffset = Processor.offsetFromAddress(vaddr + transferredbyte);
			int leftByte = pageSize - pageOffset;
			int amount = Math.min(leftByte, length - transferredbyte);
			int realAddress = pageTable[pageNum].ppn * pageSize + pageOffset;
			System.arraycopy(data, offset + transferredbyte, memory, realAddress, amount);// 将数据从data复制到memory中
			transferredbyte = transferredbyte + amount;
		} while (transferredbyte < length);

		return transferredbyte;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {// 释放内存
		UserKernel.allocateMemoryLock.acquire();

		for (int i = 0; i < numPages; i++) {
			UserKernel.memoryLinkedList.add(pageTable[i].ppn);// 将该用户进程占用的内存加入空闲内存链表中
			pageTable[i] = null;// 页表项目置为null

		}
		UserKernel.allocateMemoryLock.release();

	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {// 初始化寄存器

		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)// 所有的都为0
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);// PC寄存器初始化
		processor.writeRegister(Processor.regSP, initialSP);// SP寄存器初始化

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
			syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();

		case syscallCreate:
			return handleCreate(a0);

		case syscallExit:
			return handleExit(a0);

		case syscallJoin:
			return handleJoin(a0, a1);

		case syscallExec:
			return handleExec(a0, a1, a2);

		case syscallOpen:
			return handleOpen(a0);

		case syscallRead:
			return handleRead(a0, a1, a2);

		case syscallWrite:
			return handleWrite(a0, a1, a2);

		case syscallClose:
			return handleClose(a0);

		case syscallUnlink:
			return handleUnlink(a0);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	private int handleHalt() {
		// 处理halt（）的系统调用，停止机器的操作，只有root进程可以调用
		if (pid == 0)
			Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleExec(int fileAddress, int argc, int argvAddress) {
		// 处理Exec（）的系统调用，创建一个子进程，并执行
		String filename = readVirtualMemoryString(fileAddress, 256);// 从内存中读出这个子进程使用的文件的名字

		if (filename == null || argc < 0 || argvAddress < 0 || argvAddress > numPages * pageSize)
			return -1;

		String[] args = new String[argc];// 得到子进程的参数表
		for (int i = 0; i < argc; i++) {
			byte[] argsAddress = new byte[4];
			if (readVirtualMemory(argvAddress + i * 4, argsAddress) > 0)
				args[i] = readVirtualMemoryString(Lib.bytesToInt(argsAddress, 0), 256);
		}
		UserProcess process = UserProcess.newUserProcess();
		if (!process.execute(filename, args))
			return -1;
		process.parentProcess = this;// 将父进程指向调用这个方法的进程
		childProcess.add(process);// 在子进程的链表中加入新创建的进程
		return process.pid;
	}

	private int handleExit(int status) {
		// 处理exit（）的系统调用，退出
		coff.close();// 关闭文件
		for (int i = 0; i < 16; i++)// 关闭所有打开的文件
		{
			if (openfiles[i] != null) {
				openfiles[i].close();
				openfiles[i] = null;
			}
		}
		this.status = status;// 把状态置入
		normalExit = true;// 属于正常退出

		if (parentProcess != null)// 如果有父进程，就从父进程的子进程链表中删除，而且如果父进程join子进程，唤醒父进程
		{
			joinLock.acquire();
			joinCondition.wake();
			joinLock.release();
			parentProcess.childProcess.remove(this);
		}

		unloadSections();// 释放内存

		KThread.finish();// 将进程置为完成态，进行下一个进程，如果有join的进程，将join移出等待队列

		if (numOfRunningProcess == 1)// 如果是最后一个进程，则关闭机器
			Machine.halt();

		numOfRunningProcess--;

		return 0;
	}

	private int handleJoin(int pid, int statusAddress) {

		UserProcess process = null;
		for (int i = 0; i < childProcess.size(); i++)// 找到是否属于自己的子进程
		{
			if (pid == childProcess.get(i).pid) {
				process = childProcess.get(i);
				break;
			}
		}

		if (process == null || process.thread == null)// 如果没有子进程或者子进程还没创建UThread，出错
			return -1;

		process.joinLock.acquire();// 获得join锁
		process.joinCondition.sleep();// 进程休眠等待直到子进程结束将其唤醒
		process.joinLock.release();// 释放join锁，此时子线程已经结束

		byte[] childstat = new byte[4];
		Lib.bytesFromInt(childstat, 0, process.status);// 得到子进程的状态
		int numWriteByte = writeVirtualMemory(statusAddress, childstat);// 将子进程的状态装入自己拥有的内存
		if (process.normalExit && numWriteByte == 4)// 子进程是正常结束，且写入状态成功
			return 1;

		return 0;
	}

	private int handleCreate(int fileAddress) {
		// 处理create（）的系统调用，创建一个文件，返回文件描述符

		String filename = readVirtualMemoryString(fileAddress, 256);
		System.out.println("filename to be create -->" + filename);
		if (filename == null)
			return -1;// 文件名不存在,创建失败

		int fileDescriptor = findEmpty();

		if (fileDescriptor == -1)
			return -1;// 进程打开文件数已经达到上限，无法创建并打开
		else {
			openfiles[fileDescriptor] = ThreadedKernel.fileSystem.open(filename, true);// 文件不存在直接创建
			return fileDescriptor;
		}
	}

	private int handleOpen(int fileAddress) {// 处理open（）的系统调用，打开一个文件
		String filename = readVirtualMemoryString(fileAddress, 256);

		if (filename == null)
			return -1;// 文件名不存在

		int fileDescriptor = findEmpty();

		if (fileDescriptor == -1)
			return -1;// 进程打开文件数已经达到上限，无法打开

		else {
			openfiles[fileDescriptor] = ThreadedKernel.fileSystem.open(filename, false);

			return fileDescriptor;// 成功返回文件描述符
		}
	}

	private int handleRead(int fileDescriptor, int bufferAddress, int length) {// 处理read（）的系统调用，从文件中读出数据写入自己拥有的内存的指定地址
		if (fileDescriptor > 15 || fileDescriptor < 0 || openfiles[fileDescriptor] == null)
			return -1;// 文件未打开，出错

		byte temp[] = new byte[length];
		int readNumber = openfiles[fileDescriptor].read(temp, 0, length);

		if (readNumber <= 0)
			return 0;// 没读出数据

		int writeNumber = writeVirtualMemory(bufferAddress, temp);

		return writeNumber;
	}

	private int handleWrite(int fileDescriptor, int bufferAddress, int length) {// 处理write（）的系统调用，将自己拥有的内存的指定地址的数据写入文件
		if (fileDescriptor > 15 || fileDescriptor < 0 || openfiles[fileDescriptor] == null)
			return -1;// 文件未打开,出错

		byte temp[] = new byte[length];
		int readNumber = readVirtualMemory(bufferAddress, temp);

		if (readNumber <= 0)
			return 0;// 没读出数据

		int writeNumber = openfiles[fileDescriptor].write(temp, 0, length);
		if (writeNumber < length)
			return -1;// 未完全写入，出错

		return writeNumber;
	}

	private int handleClose(int fileDescriptor) {// 处理close（）的系统调用，用于关闭打开的文件
		if (fileDescriptor > 15 || fileDescriptor < 0 || openfiles[fileDescriptor] == null)
			return -1;// 文件不存在，关闭出错

		openfiles[fileDescriptor].close();
		openfiles[fileDescriptor] = null;
		return 0;
	}

	private int handleUnlink(int fileAddress) {// 处理unlink（）的系统调用，用于删除文件
		String filename = readVirtualMemoryString(fileAddress, 256);

		if (filename == null)
			return 0;// 文件不存在,不必删除

		if (ThreadedKernel.fileSystem.remove(filename))// 删除磁盘中实际的文件
			return 0;
		else
			return -1;
	}

	private int findEmpty()// 找到合适的文件打开表的位置，返回文件描述符
	{
		for (int i = 0; i < 16; i++) {
			if (openfiles[i] == null)
				return i;
		}
		return -1;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {// 异常处理器，未使用
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}
}
