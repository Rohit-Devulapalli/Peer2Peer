package peers;

import java.net.Socket;
import java.net.SocketAddress;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.UnknownHostException;

import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import Logging.Logging;

/* This class contains the threads for receiving and sending messages. The receiver thread continuously
 * monitors the input stream to receive incoming messages. It forwards these messages to the message handler
 * function to take appropriate action. It contains another thread that sends out the messages from this 
 * peer to all other peers. It does so with the help of a blocking queue that sends out messages one after the other. 
 */
public class ComSupervisor implements Runnable {

	Socket socket;
	Peer peer;
	int peerID;
	Handshake handShake;
	BlockingQueue<Message> msgQ = new LinkedBlockingQueue<>();
	OutputWriter out;
	InReader in;
	AtomicInteger remotePeerId = new AtomicInteger(-1);
	FileSupervisor fileSupervisor;
	PeerSupervisor peerSupervisor;
	MessagesToSend ms;
	int expectedRemotePeer = -1;

	ComSupervisor(Socket s, Peer thisOne, FileSupervisor fs, PeerSupervisor ps) {
		this.socket = s;
		this.peer = thisOne;
		this.peerID = peer.getPeerID();
		handShake = new Handshake(thisOne.getPeerID());
		this.fileSupervisor = fs;
		this.peerSupervisor = ps;
	}

	ComSupervisor(BlockingQueue<Message> queue) {
		this.msgQ = queue;
	}

	public void run() {
		System.out.println("comSupervisor thread is running .....");
		try {
//			start thread for sending messages
			MessagesToSend ms = new MessagesToSend();
			Thread t = new Thread(ms);
			t.setName("MessageQThread");
			t.start();
			
			in = new InReader(socket.getInputStream());
			Handshake handshake = new Handshake(peer.getPeerID());
			out = new OutputWriter(socket.getOutputStream());
			out.writeObject(handshake);
			Handshake msg = (Handshake) in.readObject();

			remotePeerId.set(msg.getPeerId());

			System.out.println("connected with :" + remotePeerId.get());

			if(expectedRemotePeer != -1 && (remotePeerId.get() != expectedRemotePeer)) {
				throw new Exception("neighbor ID " + remotePeerId + " is not same as expected ID = "+ expectedRemotePeer); 
			}
            Logging logrecord = new Logging();
			String m = "is connected with";
            logrecord.mes(remotePeerId.get(),m);
			MessageSupervisor messageSupervisor = new MessageSupervisor(remotePeerId, fileSupervisor, peerSupervisor);
			Message bitfieldResponse = messageSupervisor.respondWithBitfield(msg);
//			System.out.println("my bitfield message = " + bitfieldResponse.getMesType());
			writeMessage(bitfieldResponse);

			while(true){
				try {
					System.out.println("in other messages block");
					Message otherMessage = (Message) in.readObject();
					System.out.println("received message " + otherMessage.getMesType() + " from " + remotePeerId);
					Message responseMessage = messageSupervisor.handleAllMessages(otherMessage);
					if(responseMessage!=null) {
						if(responseMessage.getMesType()=="Piece") {
							System.out.println("response message " + responseMessage.getMesType() + " to " + remotePeerId);
//							System.out.println("message content = "+responseMessage.);
						}
					}
					writeMessage(responseMessage);
				} catch (Exception e) {
					// System.exit(0);
					e.printStackTrace();
					break;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void writeMessage(Message message) throws IOException {
		if(message != null) {
			out.writeObject(message);
		}
	}

	public synchronized void addToQueue(final Message m) {
//		System.out.println(m.getMesType()+" length is "+m.mLen);
		msgQ.add(m);
	}
	
	public void closeResources() {
		try {
			if(out!=null)
				out.close();
			if(in!=null)
				in.close();
//			socket.close();
		} catch (IOException e) {
			System.out.println("Exception at closing resources");
			e.printStackTrace();
		}
	}

//	****************************************************************************
	/*
	 * nested class to throw all messages to be sent to remote peers in a
	 * synchronized queue called blocking queue and send them eventually(one by one)
	 */
	class MessagesToSend implements Runnable {

		public void run() {
			while(true) {
				if (!msgQ.isEmpty()) {
					try {
						Message msg = msgQ.take();
//						System.out.println("next to send out = "+msg.getMesType());
						out.writeObject(msg);
					} catch (Exception e) {
						e.printStackTrace();
					}

				}
			}
		}
	}
//	*******************************************************************************
}
