package sig;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.Robot;

public class SigShare {
	static Robot r;
	public static final String PROGRAM_NAME="SigShare";
	public static void main(String[] args) throws AWTException {
		r = new Robot();
		if (args.length==2&&args[1].equalsIgnoreCase("server")) {
			ServerSocket socket;
			try {
				socket = new ServerSocket(4191);
				System.out.println("Listening on port 4191.");
				try (Socket client = socket.accept()) {
					System.out.println("New client connection detected: "+client.toString());
					System.out.println("Sending initial data...");
					BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(),"ISO-8859-1"));
					DataOutputStream clientOutput = new DataOutputStream(client.getOutputStream());
					int SCREEN_WIDTH=(int)GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getWidth();
					int SCREEN_HEIGHT=(int)GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getHeight();
					int[] pixels = new int[SCREEN_WIDTH*SCREEN_HEIGHT];
					clientOutput.write(("DESKTOP "+SCREEN_WIDTH+" "+SCREEN_HEIGHT+"\r\n").getBytes());
					System.out.println("Send initial screen");
					//char[] screen = new char[SCREEN_WIDTH*SCREEN_HEIGHT];
					BufferedImage screenshot = CaptureScreen();
					for (int y=0;y<SCREEN_HEIGHT;y++) {
						for (int x=0;x<SCREEN_WIDTH;x++) {
							int col = pixels[y*SCREEN_WIDTH+x] = screenshot.getRGB(x, y);
							int r = ((col&0x00FF0000)>>>16)/8;
							int g = ((col&0x0000FF00)>>>8)/8;
							int b = ((col&0x000000FF))/8;
							char compressedCol=(char)((r<<10)+(g<<5)+b);
							clientOutput.writeChar(compressedCol);
							//screen[y*SCREEN_WIDTH+x]=compressedCol;
						}	
					}
					System.out.println("Begin diff monitoring...");
					int frame=0;
					while (true) {
						screenshot = CaptureScreen();
						for (int y=0;y<SCREEN_HEIGHT;y++) {
							for (int x=0;x<SCREEN_WIDTH;x++) {
								int col = screenshot.getRGB(x, y);
								byte b1=0,b2=0,b3=0;
								if (col!=pixels[y*SCREEN_WIDTH+x]) {
									b1=(byte)(x&0xFF); //bits 1-8 for x
									b2=(byte)(((x&0xF00)>>>8)+((y&0xF)<<4)); //bits 9-12 for x, bits 1-4 for y
									b3=(byte)((y>>>4)&0xFF);//bits 5-12 for y
									pixels[y*SCREEN_WIDTH+x]=col;
									int r = ((col&0x00FF0000)>>>16)/8;
									int g = ((col&0x0000FF00)>>>8)/8;
									int b = ((col&0x000000FF))/8;
									char compressedCol=(char)((r<<10)+(g<<5)+b);
									clientOutput.writeChar(compressedCol);
									clientOutput.writeByte(b1);
									clientOutput.writeByte(b2);
									clientOutput.writeByte(b3);
									System.out.println("  Pixel ("+x+","+y+") "+b1+"/"+(b2&0xF)+"/"+((b2&0xF0)>>>4)+"/"+b3+" sent");
								}
								//screen[y*SCREEN_WIDTH+x]=compressedCol;
							}	
						}
						System.out.println("Frame "+frame+++" processed");
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
			DataInputStream in;
			
			JFrame f = new JFrame(PROGRAM_NAME);
			Panel p = new Panel(f);

			try {
				socket = new Socket(args[0],4191);
				out = new PrintWriter(socket.getOutputStream(),true);
				in=new DataInputStream(socket.getInputStream());
				
			
				while (true) {
					String line;
					if (in.available()>0) {
					line=in.readLine();
					 //System.out.println(line);
					 if (line.contains("DESKTOP")) {
						 String[] split = line.split(Pattern.quote(" "));
						 
						int SCREEN_WIDTH=Integer.parseInt(split[1]);
						int SCREEN_HEIGHT=Integer.parseInt(split[2]);
						p.init(SCREEN_WIDTH,SCREEN_HEIGHT);
						
						f.add(p);
						f.setSize(SCREEN_WIDTH,SCREEN_HEIGHT);
						f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
						f.setVisible(true);

						int expectedChars = SCREEN_WIDTH*SCREEN_HEIGHT;
						System.out.println("Expecting "+(expectedChars)+" of data.");
						int arrayIndex = 0;
						while (expectedChars>0) {
							if (in.available()>0) {
								char col = in.readChar();
								int convert = ((((col&0b0111110000000000)>>>10)*8)<<16)+
								((((col&0b0000001111100000)*8)>>>5)<<8)+
								((((col&0b0000000000011111))*8));
								Panel.pixel[arrayIndex++]=convert;
								//System.out.println("Received "+col+" / "+convert);
								expectedChars--;
							}
						}
						p.render();
						System.out.println("Initial image processed!");
						int frame=0;
						while (true) {
							if (in.available()>0) {
								char col = in.readChar();
								int convert = ((((col&0b0111110000000000)>>>10)*8)<<16)+
								((((col&0b0000001111100000)*8)>>>5)<<8)+
								((((col&0b0000000000011111))*8));
								int b1=in.readUnsignedByte()&0xff,b2=in.readUnsignedByte()&0xff,b3=in.readUnsignedByte()&0xff;
								int x = b1+((b2&0xF)<<8);
								int y = (b3<<4)+((b2&0xF0)>>>4);
								System.out.println("  Pixel "+frame+++" ("+x+","+y+") "+b1+"/"+(b2&0xF)+"/"+((b2&0xF0)>>>4)+"/"+b3+" processed");
								Panel.pixel[y*SCREEN_WIDTH+x]=convert;
							}
						}
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
	private static BufferedImage CaptureScreen() throws IOException {
		BufferedImage screenshot = r.createScreenCapture(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
		//ImageIO.write(screenshot,"png",new File("screenshot.png"));
		return screenshot;
	}
}
