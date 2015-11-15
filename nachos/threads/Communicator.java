package nachos.threads;

import java.util.LinkedList;
import java.util.Queue;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {

	private int word;
	private int speakerCount;
	private int listenerCount;
	private Lock lock;
	private Condition2 speaker;
	private Condition2 listener;
	private static Queue<Integer> wordQueue;

	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		word = 0;
		speakerCount = 0;
		listenerCount = 0;
		lock = new Lock();
		speaker = new Condition2(lock);
		listener = new Condition2(lock);
		wordQueue = new LinkedList<Integer>();
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param word
	 *            the integer to transfer.
	 */
	public void speak(int word) {
		boolean status = Machine.interrupt().disable();

		lock.acquire();
		if (listenerCount == 0) {
			speakerCount++;
			wordQueue.offer(word);
			speaker.sleep();
			listener.wake();
			speakerCount--;
		}else{
			wordQueue.offer(word);
			listener.wake();
		}
		lock.release();
		Machine.interrupt().restore(status);
		
		//考虑为什么使用return
		return;
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return the integer transferred.
	 */
	public int listen() {
		boolean status = Machine.interrupt().disable();
		
		lock.acquire();
		if(speakerCount != 0){
			speaker.wake();
			listener.sleep();
		}else{
			listenerCount ++;
			listener.sleep();
			listenerCount --;
		}
		lock.release();
		Machine.interrupt().restore(status);
		
		return wordQueue.poll();
	}
}
