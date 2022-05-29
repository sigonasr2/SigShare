package sig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import java.awt.Toolkit;
import sig.engine.Panel;

public class SigShare {
	public static final String PROGRAM_NAME="SigShare";
	public static void main(String[] args) {
		if (args.length==2&&args[1].equalsIgnoreCase("server")) {
			ServerSocket socket;
			try {
				socket = new ServerSocket(4191);
				System.out.println("Listening on port 4191.");
				try (Socket client = socket.accept()) {
					System.out.println("New client connection detected: "+client.toString());
					System.out.println("Sending initial data...");
					BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(),"ISO-8859-1"));
					OutputStream clientOutput = client.getOutputStream();
					int SCREEN_WIDTH=(int)Toolkit.getDefaultToolkit().getScreenSize().getWidth();
					int SCREEN_HEIGHT=(int)Toolkit.getDefaultToolkit().getScreenSize().getHeight();
					clientOutput.write(("DESKTOP "+(int)Toolkit.getDefaultToolkit().getScreenSize().getWidth()+" "+(int)Toolkit.getDefaultToolkit().getScreenSize().getHeight()+"\r\n").getBytes());
					System.out.println("Send initial screen");
					for (int y=0;y<SCREEN_HEIGHT;y++) {
						for (int x=0;x<SCREEN_WIDTH;x++) {
							
						}	
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} else 
		if (args.length==2&&args[1].equalsIgnoreCase("client")) {
			Socket socket;
			PrintWriter out;
			BufferedReader in;
			
			JFrame f = new JFrame(PROGRAM_NAME);
			Panel p = new Panel(f);

			try {
				socket = new Socket(args[0],4191);
				out = new PrintWriter(socket.getOutputStream(),true);
				in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
				
			
				while (true) {
					String line;
					if (in.ready()) {
					line=in.readLine();
					 //System.out.println(line);
					 if (line.contains("DESKTOP")) {
						 String[] split = line.split(Pattern.quote(" "));
						 
						p.init();
						
						f.add(p);
						f.setSize(Integer.parseInt(split[1]),Integer.parseInt(split[2]));
						f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
						f.setVisible(true);
						
						p.render();
					 }
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			System.err.println("Args: <Connecting IP Address> server|client");
			return;
		}
	}
}
