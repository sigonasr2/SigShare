package sig;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JFrame;
import sig.engine.Panel;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.Robot;
import java.awt.Graphics2D;

public class SigShare {
	static Robot r;
	public static final String PROGRAM_NAME="SigShare";
	public static double SCREEN_MULT=2;
	public static int REGION_X_COUNT = 12;
	public static int REGION_Y_COUNT = 12;
	public static int SCREEN_WIDTH=((int)(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getWidth()/SCREEN_MULT)/REGION_X_COUNT)*REGION_X_COUNT;
	public static int SCREEN_HEIGHT=((int)(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().getHeight()/SCREEN_MULT)/REGION_Y_COUNT)*REGION_Y_COUNT;
	public static int[] img = new int[SCREEN_WIDTH*SCREEN_HEIGHT]; //Store the number of changes in the last 8 bits of ints. Use 2 ints per section, meaning an 8x8 section uses 128 integers to store 64 values.
	public static int CHANGE_THRESHOLD = 500;
	public static char[] changes = new char[REGION_X_COUNT*REGION_Y_COUNT];
	public static int REGION_WIDTH = SCREEN_WIDTH/REGION_X_COUNT;
	public static int REGION_HEIGHT = SCREEN_HEIGHT/REGION_Y_COUNT;
	public static long LAST_CLEANUP = System.currentTimeMillis();
	public static int CLEANUP_FREQUENCY = 5000;
	public static HashMap<Character,Boolean> REGION_CHECK = new HashMap<>();
	public static void main(String[] args) throws AWTException {
		r = new Robot();
		if (args.length==2&&args[1].equalsIgnoreCase("server")) {
			ServerSocket socket;
			try {
				socket = new ServerSocket(4191);
				System.out.println("Listening on port 4191.");
				try (Socket client = socket.accept()) {
					System.out.println("New client connection detected: "+client.toString());
					System.out.println("Taking screenshot...");
					System.out.println("Sending initial data...");
					BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(),"ISO-8859-1"));
					DataOutputStream clientOutput = new DataOutputStream(client.getOutputStream());
					clientOutput.write(("DESKTOP "+SCREEN_WIDTH+" "+SCREEN_HEIGHT+"\r\n").getBytes());
					System.out.println("Send initial screen");
					//char[] screen = new char[SCREEN_WIDTH*SCREEN_HEIGHT];
					/*for (int y=0;y<SCREEN_HEIGHT;y++) {
						for (int x=0;x<SCREEN_WIDTH;x++) {
							int col = pixels[y*SCREEN_WIDTH+x] = screenshot.getRGB(x, y);
							int r = ((col&0x00FF0000)>>>16)/8;
							int g = ((col&0x0000FF00)>>>8)/8;
							int b = ((col&0x000000FF))/8;
							char compressedCol=(char)((r<<10)+(g<<5)+b);
							clientOutput.writeChar(compressedCol);
							//screen[y*SCREEN_WIDTH+x]=compressedCol;
						}	
					}*/
					int frame=0;
					CaptureScreen(SCREEN_WIDTH,SCREEN_HEIGHT);
					FileInputStream stream = new FileInputStream(new File("screenshot.jpg"));
					while (stream.available()>0) {
						clientOutput.writeByte(stream.read());
					}
					stream.close();
					BufferedImage image = ImageIO.read(new File("screenshot.jpg"));
					for (int y=0;y<image.getHeight();y++) {
						for (int x=0;x<image.getWidth();x++) {
							img[y*image.getWidth()+x]=image.getRGB(x, y)&0x00FFFFFF;
						}		
					}
					for (int i=0;i<10;i++) {
						clientOutput.writeChar('-');
					}
					System.out.println("Frame "+frame+++" processed. Waiting on client.");
					while (!in.ready()) {
					}
					System.out.println("Client no longer idle.");
					in.readLine();
					System.out.println("Begin diff analysis mode.");
					while (true) {
						BufferedImage newCapture = CaptureScreen(SCREEN_WIDTH,SCREEN_HEIGHT);
						REGION_CHECK.clear();
							if (System.currentTimeMillis()-LAST_CLEANUP>=CLEANUP_FREQUENCY) {
								System.out.println("New full refresh");
								CaptureScreen(SCREEN_WIDTH,SCREEN_HEIGHT);
								clientOutput.write(255);
								stream = new FileInputStream(new File("screenshot.jpg"));
								while (stream.available()>0) {
									clientOutput.writeByte(stream.read());
								}
								stream.close();
								image = ImageIO.read(new File("screenshot.jpg"));
								for (int y=0;y<image.getHeight();y++) {
									for (int x=0;x<image.getWidth();x++) {
										img[y*image.getWidth()+x]=image.getRGB(x, y)&0x00FFFFFF;
									}		
								}
								for (int i=0;i<10;i++) {
									clientOutput.writeChar('-');
								}
								System.out.println("Frame "+frame+++" processed. Waiting on client.");
								while (!in.ready()) {
								}
								System.out.println("Client no longer idle.");
								in.readLine();
								LAST_CLEANUP=System.currentTimeMillis();
							} else {
							for (int y=0;y<newCapture.getHeight();y++) {
								for (int x=0;x<newCapture.getWidth();x++) {
									int currentPixel = img[y*newCapture.getWidth()+x];
									int newPixel = newCapture.getRGB(x, y)&0x00FFFFFF;
									int gridX = x/REGION_WIDTH;
									int gridY = y/REGION_HEIGHT;
									if (currentPixel!=newPixel) {
										img[y*newCapture.getWidth()+x]=newPixel;
										//System.out.println("Changes ("+gridX+","+gridY+"): "+changes[gridY*REGION_X_COUNT+gridX]);
										if (!REGION_CHECK.containsKey((char)(gridY*REGION_X_COUNT+gridX))) {
											changes[gridY*REGION_X_COUNT+gridX]+=2;
											if (gridY>0) {
												changes[(gridY-1)*REGION_X_COUNT+gridX]+=1;
											}
											if (gridY<REGION_Y_COUNT-1) {
												changes[(gridY+1)*REGION_X_COUNT+gridX]+=1;
											}
											if (gridX>0) {
												changes[(gridY)*REGION_X_COUNT+(gridX-1)]+=1;
											}
											if (gridX<REGION_Y_COUNT-1) {
												changes[(gridY)*REGION_X_COUNT+(gridX+1)]+=1;
											}
											if (changes[gridY*REGION_X_COUNT+gridX]>=CHANGE_THRESHOLD) {
												performSubimageUpdate(in, clientOutput, newCapture, gridX, gridY);
											}
										}
									}
								}		
							}
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
			DataOutputStream out;
			DataInputStream in;
			
			JFrame f = new JFrame(PROGRAM_NAME);
			Panel p = new Panel(f);

			try {
				socket = new Socket(args[0],4191);
				out = new DataOutputStream(socket.getOutputStream());
				in=new DataInputStream(socket.getInputStream());
				
			
				while (true) {
					String line;
					if (in.available()>0) {
					line=in.readLine();
						System.out.println(line);
						if (line.contains("DESKTOP")) {
							String[] split = line.split(Pattern.quote(" "));
							
							SCREEN_WIDTH=Integer.parseInt(split[1]);
							SCREEN_HEIGHT=Integer.parseInt(split[2]);
							p.init(SCREEN_WIDTH,SCREEN_HEIGHT);
							
							f.add(p);
							f.setSize(SCREEN_WIDTH,SCREEN_HEIGHT);
							f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
							f.setVisible(true);

							int frame=0;
							int dashCount=0;
							BufferedOutputStream stream = null;
							while (true) {
								while (in.available()>0) {
									if (stream==null) {
										//System.out.println("Stream opened.");
										stream=new BufferedOutputStream(new FileOutputStream(new File("screenshot_out.jpg"),true));
									}
									int val = in.read();
									stream.write(val);
									//System.out.print((char)val);
									if (val=='-') {
										dashCount++;
									} else 
									if (val!=0) {
										dashCount=0;
									}
								}
								if (dashCount>=10) {
									stream.close();
									stream=null;
									dashCount=0;
									System.out.println("Frame "+frame+++" processed.");
									BufferedImage i = ImageIO.read(new File("screenshot_out.jpg"));
									if (i!=null) {
										for (int y=0;y<i.getHeight();y++) {
											for (int x=0;x<i.getWidth();x++) {
												Panel.pixel[y*SCREEN_WIDTH+x]=i.getRGB(x,y);
											}
										}
									}
									out.writeChars("Done\r\n");
									stream=new BufferedOutputStream(new FileOutputStream(new File("screenshot_out.jpg"),false));
									break;
								}
							}

							int regionInfo = -1;
							System.out.println("Waiting for region data...");
							while (true) {
								//System.out.println("Waiting for region data...");
								while (in.available()>0) {
									if (regionInfo==-1) {
										regionInfo=in.read();
										if (regionInfo==255) {
											System.out.println("Full refresh received.");
											while (true) {
												while (in.available()>0) {
													if (stream==null) {
														//System.out.println("Stream opened.");
														stream=new BufferedOutputStream(new FileOutputStream(new File("screenshot_out.jpg"),true));
													}
													int val = in.read();
													stream.write(val);
													//System.out.print((char)val);
													if (val=='-') {
														dashCount++;
													} else 
													if (val!=0) {
														dashCount=0;
													}
												}
												if (dashCount>=10) {
													stream.close();
													stream=null;
													dashCount=0;
													System.out.println("Frame "+frame+++" processed.");
													BufferedImage i = ImageIO.read(new File("screenshot_out.jpg"));
													if (i!=null) {
														for (int y=0;y<i.getHeight();y++) {
															for (int x=0;x<i.getWidth();x++) {
																Panel.pixel[y*SCREEN_WIDTH+x]=i.getRGB(x,y);
															}
														}
													}
													out.writeChars("Done\r\n");
													stream=new BufferedOutputStream(new FileOutputStream(new File("screenshot_out.jpg"),false));
													break;
												}
											}
										}
									} else {
										while (in.available()>0) {
											if (stream==null) {
												//System.out.println("Stream opened.");
												stream=new BufferedOutputStream(new FileOutputStream(new File("screenshot_out.jpg"),true));
											}
											int val = in.read();
											stream.write(val);
											if (val=='-') {
												dashCount++;
											} else 
											if (val!=0) {
												dashCount=0;
											}
										}
										if (dashCount>=10) {
											stream.close();
											stream=null;
											dashCount=0;
											System.out.println("Frame "+frame+++" processed.");
											BufferedImage i = ImageIO.read(new File("screenshot_out.jpg"));
											if (i!=null) {
												int yy=0;
												for (int y=(regionInfo/REGION_X_COUNT)*REGION_HEIGHT;y<(regionInfo/REGION_X_COUNT)*REGION_HEIGHT+REGION_HEIGHT;y++) {
													int xx=0;
													for (int x=(regionInfo%REGION_X_COUNT)*REGION_WIDTH;x<(regionInfo%REGION_X_COUNT)*REGION_WIDTH+REGION_WIDTH;x++) {
														Panel.pixel[y*SCREEN_WIDTH+x]=i.getRGB(xx,yy);
														xx++;
													}
													yy++;
												}
											}
											//System.out.println("Region ("+(regionInfo%REGION_X_COUNT)+","+(regionInfo/REGION_X_COUNT)+") updated.");
											//System.out.println("Waiting for region data...");
											regionInfo=-1;
											out.writeChars("Done\r\n");
											stream=new BufferedOutputStream(new FileOutputStream(new File("screenshot_out.jpg"),false));
										}
									}
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
	private static void performSubimageUpdate(BufferedReader in, DataOutputStream clientOutput, BufferedImage newCapture, int gridX,
			int gridY) throws IOException, FileNotFoundException {
		FileInputStream stream;
		changes[gridY*REGION_X_COUNT+gridX]=0;
		GetSubimage(newCapture, gridX*REGION_WIDTH, gridY*REGION_HEIGHT, REGION_WIDTH, REGION_HEIGHT);
		//System.out.println("Preparing to send region("+gridX+","+gridY+").");
		stream = new FileInputStream(new File("screenshot_part.jpg"));
		clientOutput.writeByte(gridY*REGION_X_COUNT+gridX);
		while (stream.available()>0) {
			clientOutput.writeByte(stream.read());
		}
		stream.close();
		for (int i=0;i<10;i++) {
			clientOutput.writeChar('-');
		}
		//System.out.println("Region sent, waiting for reply.");
		while (!in.ready());
		in.readLine();
		//System.out.println("Client no longer idle.");
		REGION_CHECK.put((char)(gridY*REGION_X_COUNT+gridX),true);
		/*x=(x/REGION_WIDTH)*REGION_WIDTH+REGION_WIDTH;
		y=(y/REGION_HEIGHT)*REGION_HEIGHT+REGION_HEIGHT;*/
	}
	private static BufferedImage CaptureScreen(int w, int h) throws IOException {
		//BufferedImage screenshot = toBufferedImage(r.createScreenCapture(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()).getScaledInstance(w, h, Image.SCALE_DEFAULT));
		BufferedImage screenshot;
		ImageOutputStream  ios =  ImageIO.createImageOutputStream(new File("screenshot.jpg"));
		Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpeg");
		ImageWriter writer = iter.next();
		ImageWriteParam iwp = writer.getDefaultWriteParam();
		iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		iwp.setCompressionQuality(0.8f);
		writer.setOutput(ios);
		writer.write(null, new IIOImage(screenshot=resizeImage(r.createScreenCapture(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds()),w,h),null,null),iwp);
		writer.dispose();
	
		return screenshot;
	}
	private static BufferedImage GetSubimage(BufferedImage screenshot, int x, int y, int w, int h) throws IOException {
		BufferedImage newimg = screenshot.getSubimage(x, y, w, h);
		ImageIO.write(newimg,"jpeg",new File("screenshot_part.jpg"));
		return newimg;
	}
	static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
		Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
		BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
		return outputImage;
	}
	public static BufferedImage toBufferedImage(Image img)
{
    if (img instanceof BufferedImage)
    {
        return (BufferedImage) img;
    }

    // Create a buffered image with transparency
    BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

    // Draw the image on to the buffered image
    Graphics2D bGr = bimage.createGraphics();
    bGr.drawImage(img, 0, 0, null);
    bGr.dispose();

    // Return the buffered image
    return bimage;
}
}
